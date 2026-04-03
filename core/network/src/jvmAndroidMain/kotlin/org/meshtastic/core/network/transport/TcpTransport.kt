/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Shared JVM TCP transport for Meshtastic radios.
 *
 * Manages the TCP socket lifecycle (connect, read loop, reconnect with backoff) and uses [StreamFrameCodec] for the
 * START1/START2 stream framing protocol. Heartbeat scheduling is owned by [SharedRadioInterfaceService]; this class
 * only exposes [sendHeartbeat] for external callers.
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
    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var connectionJob: Job? = null

    // Metrics
    private var connectionStartTime: Long = 0
    private var packetsReceived: Int = 0
    private var packetsSent: Int = 0
    private var bytesReceived: Long = 0
    private var bytesSent: Long = 0
    private var timeoutEvents: Int = 0

    /** Whether the transport is currently connected. */
    val isConnected: Boolean
        get() = socket?.isConnected == true && !socket!!.isClosed

    /**
     * Start a TCP connection to the given address with automatic reconnect.
     *
     * @param address host or host:port string
     */
    fun start(address: String) {
        stop()
        connectionJob = scope.handledLaunch { connectWithRetry(address) }
    }

    /** Stop the transport and close the socket. */
    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        disconnectSocket()
    }

    /**
     * Send a raw framed Meshtastic packet.
     *
     * The payload is wrapped with the START1/START2 header by the codec.
     */
    suspend fun sendPacket(payload: ByteArray) {
        codec.frameAndSend(payload = payload, sendBytes = ::sendBytesRaw, flush = ::flushBytes)
    }

    /** Send a heartbeat packet to keep the connection alive. */
    suspend fun sendHeartbeat() {
        val heartbeat = ToRadio(heartbeat = Heartbeat())
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
                    Logger.w { "$logTag: [$address] TCP connection error - ${ex.message}" }
                    disconnectSocket()
                    false
                } catch (@Suppress("TooGenericExceptionCaught") ex: Throwable) {
                    Logger.e(ex) { "$logTag: [$address] TCP exception - ${ex.message}" }
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

        Logger.i { "$logTag: [$address] Connecting to $host:$port..." }
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
                                Logger.w { "$logTag: [$address] EOF after $packetsReceived packets" }
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
    @Volatile private var isDisconnecting: Boolean = false

    private fun disconnectSocket() {
        if (isDisconnecting) return

        isDisconnecting = true
        try {
            val s = socket
            val hadConnection = s != null || outStream != null
            if (s != null) {
                val uptime = if (connectionStartTime > 0) nowMillis - connectionStartTime else 0
                Logger.i {
                    "$logTag: Disconnecting - Uptime: ${uptime}ms, " +
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
            isDisconnecting = false
        }
    }

    // endregion

    // region Byte I/O

    private fun sendBytesRaw(p: ByteArray) {
        val stream =
            outStream
                ?: run {
                    Logger.w { "$logTag: Cannot send ${p.size} bytes: not connected" }
                    return
                }
        packetsSent++
        bytesSent += p.size
        try {
            stream.write(p)
        } catch (ex: IOException) {
            Logger.w(ex) { "$logTag: TCP write error: ${ex.message}" }
            disconnectSocket()
        }
    }

    private fun flushBytes() {
        val stream = outStream ?: return
        try {
            stream.flush()
        } catch (ex: IOException) {
            Logger.w(ex) { "$logTag: TCP flush error: ${ex.message}" }
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
