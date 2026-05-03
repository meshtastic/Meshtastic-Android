/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.network.transport

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.proto.ToRadio
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared JVM TCP transport for Meshtastic radios.
 *
 * Manages the TCP socket lifecycle (connect, read loop, reconnect with backoff) and uses [StreamFrameCodec] for the
 * START1/START2 stream framing protocol. [sendHeartbeat] sends a heartbeat with a monotonically-increasing nonce so the
 * firmware's per-connection duplicate-write filter does not silently drop it.
 *
 * Used by Android and Desktop via the shared `SharedRadioInterfaceService`.
 */
@Suppress("TooManyFunctions", "MagicNumber")
class TcpTransport(
    private val dispatchers: CoroutineDispatchers,
    private val scope: CoroutineScope,
    private val listener: Listener,
    private val logTag: String = "TcpTransport",
) {

    /** Callbacks from the transport to the owning radio interface. */
    interface Listener {
        /** Called when the TCP connection is established and wake bytes have been sent. */
        fun onConnected()

        /** Called when the TCP connection is lost. */
        fun onDisconnected()

        /** Called when a decoded Meshtastic packet arrives. */
        fun onPacketReceived(bytes: ByteArray)
    }

    companion object {
        /**
         * Maximum reconnect retries. Set to [Int.MAX_VALUE] to retry indefinitely — the caller ([TcpTransport.stop])
         * owns the cancellation lifecycle.
         */
        const val MAX_RECONNECT_RETRIES = Int.MAX_VALUE
        const val MIN_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 5 * 60 * 1_000L
        const val SOCKET_TIMEOUT_MS = 5_000
        const val SOCKET_RETRIES = 18 // 18 * 5s = 90s inactivity before disconnect
        const val TIMEOUT_LOG_INTERVAL = 5
        private const val MILLIS_PER_SECOND = 1_000L
    }

    private val codec =
        StreamFrameCodec(
            onPacketReceived = {
                packetsReceived++
                listener.onPacketReceived(it)
            },
            logTag = logTag,
        )

    // TCP socket state
    @Volatile private var socket: Socket? = null

    @Volatile private var outStream: OutputStream? = null

    @Volatile private var connectionJob: Job? = null

    @Volatile private var currentAddress: String? = null

    // Metrics
    @Volatile private var connectionStartTime: Long = 0

    @Volatile private var packetsReceived: Int = 0

    @Volatile private var packetsSent: Int = 0

    @Volatile private var bytesReceived: Long = 0

    @Volatile private var bytesSent: Long = 0

    @Volatile private var timeoutEvents: Int = 0

    private val heartbeatNonce = AtomicInteger(0)

    /** Whether the transport is currently connected. */
    val isConnected: Boolean
        get() {
            val s = socket ?: return false
            return s.isConnected && !s.isClosed
        }

    /**
     * Start a TCP connection to the given address with automatic reconnect.
     *
     * @param address host or host:port string
     */
    fun start(address: String) {
        stop()
        currentAddress = address
        connectionJob = scope.handledLaunch { connectWithRetry(address) }
    }

    /** Stop the transport and close the socket. */
    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        disconnectSocket()
        currentAddress = null
    }

    /**
     * Send a raw framed Meshtastic packet.
     *
     * The payload is wrapped with the START1/START2 header by the codec.
     */
    suspend fun sendPacket(payload: ByteArray) {
        codec.frameAndSend(payload = payload, sendBytes = ::sendBytesRaw, flush = ::flushBytes)
        packetsSent++
        bytesSent += payload.size
    }

    /** Send a heartbeat packet with a monotonically-increasing nonce to keep the connection alive. */
    suspend fun sendHeartbeat() {
        val nonce = heartbeatNonce.getAndIncrement()
        val heartbeat = ToRadio(heartbeat = org.meshtastic.proto.Heartbeat(nonce = nonce))
        sendPacket(heartbeat.encode())
    }

    // region Connection lifecycle

    @Suppress("NestedBlockDepth")
    private suspend fun connectWithRetry(address: String) {
        var retryCount = 1
        var backoff = MIN_BACKOFF_MILLIS

        while (retryCount <= MAX_RECONNECT_RETRIES) {
            val hadData =
                try {
                    connectAndRead(address)
                } catch (ex: IOException) {
                    Logger.w { "$logTag: [$address] TCP connection error" }
                    disconnectSocket()
                    false
                } catch (@Suppress("TooGenericExceptionCaught") ex: Throwable) {
                    Logger.e(ex) { "$logTag: [$address] TCP exception" }
                    disconnectSocket()
                    false
                }

            // Reset backoff after a connection that successfully exchanged data,
            // so transient firmware-side disconnects recover quickly.
            if (hadData) {
                Logger.d { "$logTag: [$address] Resetting backoff after successful data exchange" }
                retryCount = 1
                backoff = MIN_BACKOFF_MILLIS
            }

            val delaySec = backoff / MILLIS_PER_SECOND
            Logger.i { "$logTag: [$address] Reconnect #$retryCount in ${delaySec}s" }
            delay(backoff)
            retryCount++
            backoff = minOf(backoff * 2, MAX_BACKOFF_MILLIS)
        }
    }

    /**
     * Connect to the given address, read data until the connection is lost, and return whether any bytes were
     * successfully received (used by [connectWithRetry] to decide whether to reset backoff).
     */
    @Suppress("NestedBlockDepth")
    private suspend fun connectAndRead(address: String): Boolean = withContext(dispatchers.io) {
        val parts = address.split(":", limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: StreamFrameCodec.DEFAULT_TCP_PORT

        Logger.i { "$logTag: [$address] Connecting to $host:$port" }
        val attemptStart = nowMillis

        Socket(InetAddress.getByName(host), port).use { sock ->
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.soTimeout = SOCKET_TIMEOUT_MS
            socket = sock

            val connectTime = nowMillis - attemptStart
            connectionStartTime = nowMillis
            resetMetrics()
            codec.reset()

            Logger.i { "$logTag: [$address] Socket connected in ${connectTime}ms" }

            BufferedOutputStream(sock.getOutputStream()).use { output ->
                outStream = output

                BufferedInputStream(sock.getInputStream()).use { input ->
                    // Send wake bytes and signal connected
                    sendBytesRaw(StreamFrameCodec.WAKE_BYTES)
                    listener.onConnected()

                    // Read loop
                    var timeoutCount = 0
                    while (timeoutCount < SOCKET_RETRIES) {
                        try {
                            val c = input.read()
                            if (c == -1) {
                                Logger.i { "$logTag: [$address] EOF after $packetsReceived packets" }
                                break
                            }
                            timeoutCount = 0
                            bytesReceived++
                            codec.processInputByte(c.toByte())
                        } catch (_: SocketTimeoutException) {
                            timeoutCount++
                            timeoutEvents++
                            if (timeoutCount % TIMEOUT_LOG_INTERVAL == 0) {
                                Logger.d { "$logTag: [$address] Timeout $timeoutCount/$SOCKET_RETRIES" }
                            }
                        }
                    }

                    if (timeoutCount >= SOCKET_RETRIES) {
                        Logger.w { "$logTag: [$address] Closing after $SOCKET_RETRIES consecutive timeouts" }
                    }
                }
            }
            val hadData = bytesReceived > 0
            disconnectSocket()
            hadData
        }
    }

    // Guards against recursive disconnects triggered by listener callbacks.
    private val isDisconnecting = AtomicBoolean(false)

    private fun disconnectSocket() {
        if (!isDisconnecting.compareAndSet(false, true)) return

        try {
            val s = socket
            val hadConnection = s != null || outStream != null
            if (s != null) {
                val uptime = if (connectionStartTime > 0) nowMillis - connectionStartTime else 0
                Logger.i {
                    "$logTag: [$currentAddress] Disconnecting - Uptime: ${uptime}ms, " +
                        "RX: $packetsReceived ($bytesReceived bytes), " +
                        "TX: $packetsSent ($bytesSent bytes)"
                }
                try {
                    s.close()
                } catch (_: IOException) {
                    // Ignore close errors
                }
            }

            socket = null
            outStream = null

            if (hadConnection) {
                listener.onDisconnected()
            }
        } finally {
            isDisconnecting.set(false)
        }
    }

    // endregion

    // region Byte I/O

    private fun sendBytesRaw(p: ByteArray) {
        val stream =
            outStream
                ?: run {
                    Logger.w { "$logTag: [$currentAddress] Cannot send ${p.size} bytes: not connected" }
                    return
                }
        try {
            stream.write(p)
        } catch (ex: IOException) {
            Logger.w(ex) { "$logTag: [$currentAddress] TCP write error" }
            disconnectSocket()
        }
    }

    private fun flushBytes() {
        val stream = outStream ?: return
        try {
            stream.flush()
        } catch (ex: IOException) {
            Logger.w(ex) { "$logTag: [$currentAddress] TCP flush error" }
            disconnectSocket()
        }
    }

    // endregion

    private fun resetMetrics() {
        packetsReceived = 0
        packetsSent = 0
        bytesReceived = 0
        bytesSent = 0
        timeoutEvents = 0
    }
}
