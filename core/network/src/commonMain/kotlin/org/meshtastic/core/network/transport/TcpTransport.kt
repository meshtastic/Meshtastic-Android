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
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.proto.ToRadio
import kotlin.concurrent.Volatile

/**
 * Decides whether to reset the reconnect backoff based on session data and uptime.
 *
 * Only sessions that lasted at least [thresholdMs] with actual data exchange are considered stable enough to warrant a
 * backoff reset. Short sessions — e.g., an ESP32 dumping config then closing the socket — keep the growing backoff so
 * the radio has time to recover between attempts.
 */
internal fun shouldResetBackoff(hadData: Boolean, sessionUptimeMs: Long, thresholdMs: Long): Boolean =
    hadData && sessionUptimeMs >= thresholdMs

/**
 * Shared TCP transport for Meshtastic radios.
 *
 * Manages the TCP socket lifecycle (connect, read loop, reconnect with backoff) and uses [StreamFrameCodec] for the
 * START1/START2 stream framing protocol. [sendHeartbeat] sends a heartbeat with a monotonically-increasing nonce so the
 * firmware's per-connection duplicate-write filter does not silently drop it.
 *
 * Uses Ktor raw sockets (`ktor-network`) so the implementation is KMP-common — shared by Android, Desktop, and (once
 * wired) iOS via the shared `SharedRadioInterfaceService`.
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

        /** Per-read inactivity timeout. Combined with [SOCKET_RETRIES] this gives the 90s idle-disconnect window. */
        const val SOCKET_TIMEOUT_MS = 5_000L
        const val SOCKET_RETRIES = 18 // 18 * 5s = 90s inactivity before disconnect
        const val TIMEOUT_LOG_INTERVAL = 5

        /** TCP connect timeout. A failed connect just feeds the reconnect/backoff loop, so it is not fatal. */
        const val CONNECT_TIMEOUT_MS = 30_000L
        private const val READ_BUFFER_SIZE = 1024
        private const val MILLIS_PER_SECOND = 1_000L

        /**
         * Minimum session duration for backoff to reset. Sessions shorter than this that ended in peer-EOF are treated
         * as transient firmware-side disconnects (e.g., ESP32 light sleep closing the TCP PhoneAPI session after a
         * config dump) and do NOT reset the backoff — the growing delay gives the radio time to recover between
         * attempts instead of hammering it at 1 Hz.
         */
        const val SHORT_SESSION_THRESHOLD_MS = 30_000L
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
    @Volatile private var selectorManager: SelectorManager? = null

    @Volatile private var socket: Socket? = null

    @Volatile private var writeChannel: ByteWriteChannel? = null

    @Volatile private var connectionJob: Job? = null

    @Volatile private var currentAddress: String? = null

    @Volatile private var connected: Boolean = false

    // Metrics
    @Volatile private var connectionStartTime: Long = 0

    @Volatile private var packetsReceived: Int = 0

    @Volatile private var packetsSent: Int = 0

    @Volatile private var bytesReceived: Long = 0

    @Volatile private var bytesSent: Long = 0

    @Volatile private var timeoutEvents: Int = 0

    private val heartbeatNonce = atomic(0)

    /** Whether the transport is currently connected. */
    val isConnected: Boolean
        get() = connected && socket?.socketContext?.isActive == true

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
                } catch (ex: TimeoutCancellationException) {
                    Logger.w(ex) { "$logTag: [$address] TCP connect timed out" }
                    disconnectSocket()
                    false
                } catch (ex: IOException) {
                    Logger.w(ex) { "$logTag: [$address] TCP connection error" }
                    disconnectSocket()
                    false
                } catch (ce: CancellationException) {
                    // Outer-scope cancellation (stop()) — tear down and let it propagate to end the loop.
                    disconnectSocket()
                    throw ce
                } catch (@Suppress("TooGenericExceptionCaught") ex: Throwable) {
                    Logger.e(ex) { "$logTag: [$address] TCP exception" }
                    disconnectSocket()
                    false
                }

            // Reset backoff only after a session that lasted long enough to indicate a real connection,
            // not a short config-dump-then-EOF from a sleeping radio. Short sessions keep the backoff
            // growing so the radio has time to recover between reconnect attempts.
            val sessionUptime = if (connectionStartTime > 0) nowMillis - connectionStartTime else 0
            if (shouldResetBackoff(hadData, sessionUptime, SHORT_SESSION_THRESHOLD_MS)) {
                Logger.d { "$logTag: [$address] Resetting backoff after successful data exchange (${sessionUptime}ms)" }
                retryCount = 1
                backoff = MIN_BACKOFF_MILLIS
            } else if (hadData) {
                val backoffSec = backoff / MILLIS_PER_SECOND
                Logger.d {
                    "$logTag: [$address] Short session (${sessionUptime}ms) — keeping backoff at ${backoffSec}s"
                }
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

        val selector = SelectorManager(dispatchers.io)
        selectorManager = selector
        val sock =
            withTimeout(CONNECT_TIMEOUT_MS) {
                aSocket(selector).tcp().connect(InetSocketAddress(host, port)) {
                    noDelay = true
                    keepAlive = true
                }
            }
        socket = sock

        try {
            val connectTime = nowMillis - attemptStart
            connectionStartTime = nowMillis
            resetMetrics()
            codec.reset()

            Logger.i { "$logTag: [$address] Socket connected in ${connectTime}ms" }

            val output = sock.openWriteChannel(autoFlush = false)
            writeChannel = output
            val input: ByteReadChannel = sock.openReadChannel()

            // Send wake bytes and signal connected
            sendBytesRaw(StreamFrameCodec.WAKE_BYTES)
            flushBytes()
            connected = true
            listener.onConnected()

            readLoop(address, input)

            bytesReceived > 0
        } finally {
            disconnectSocket()
        }
    }

    /**
     * Read until EOF or [SOCKET_RETRIES] consecutive inactivity timeouts. [withTimeoutOrNull] gives a *resumable*
     * inactivity timeout: cancelling a parked `readAvailable` leaves the channel usable for the next iteration
     * (validated in `TcpTransportTest`).
     */
    @Suppress("NestedBlockDepth")
    private suspend fun readLoop(address: String, input: ByteReadChannel) {
        val buf = ByteArray(READ_BUFFER_SIZE)
        var timeoutCount = 0
        while (timeoutCount < SOCKET_RETRIES) {
            val read = withTimeoutOrNull(SOCKET_TIMEOUT_MS) { input.readAvailable(buf) }
            when {
                read == null -> {
                    timeoutCount++
                    timeoutEvents++
                    if (timeoutCount % TIMEOUT_LOG_INTERVAL == 0) {
                        Logger.d { "$logTag: [$address] Timeout $timeoutCount/$SOCKET_RETRIES" }
                    }
                }

                read == -1 -> {
                    Logger.i { "$logTag: [$address] EOF after $packetsReceived packets" }
                    return
                }

                else -> {
                    timeoutCount = 0
                    bytesReceived += read
                    for (i in 0 until read) {
                        codec.processInputByte(buf[i])
                    }
                }
            }
        }
        Logger.w { "$logTag: [$address] Closing after $SOCKET_RETRIES consecutive timeouts" }
    }

    // Guards against recursive disconnects triggered by listener callbacks.
    private val isDisconnecting = atomic(false)

    private fun disconnectSocket() {
        if (!isDisconnecting.compareAndSet(expect = false, update = true)) return

        try {
            val s = socket
            val hadConnection = s != null || writeChannel != null
            if (s != null) {
                val uptime = if (connectionStartTime > 0) nowMillis - connectionStartTime else 0
                Logger.i {
                    "$logTag: [$currentAddress] Disconnecting - Uptime: ${uptime}ms, " +
                        "RX: $packetsReceived ($bytesReceived bytes), " +
                        "TX: $packetsSent ($bytesSent bytes)"
                }
                try {
                    s.close()
                } catch (ex: IOException) {
                    Logger.w(ex) { "$logTag: [$currentAddress] Error closing socket" }
                }
            }
            selectorManager?.close()

            socket = null
            writeChannel = null
            selectorManager = null
            connected = false

            if (hadConnection) {
                listener.onDisconnected()
            }
        } finally {
            isDisconnecting.value = false
        }
    }

    // endregion

    // region Byte I/O

    private suspend fun sendBytesRaw(p: ByteArray) {
        val stream =
            writeChannel
                ?: run {
                    Logger.w { "$logTag: [$currentAddress] Cannot send ${p.size} bytes: not connected" }
                    return
                }
        try {
            stream.writeFully(p)
        } catch (ex: IOException) {
            Logger.w(ex) { "$logTag: [$currentAddress] TCP write error" }
            disconnectSocket()
        }
    }

    private suspend fun flushBytes() {
        val stream = writeChannel ?: return
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
