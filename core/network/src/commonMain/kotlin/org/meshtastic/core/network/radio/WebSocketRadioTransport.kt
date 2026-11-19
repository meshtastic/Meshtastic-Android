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
package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.parseUrl
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.readBytes
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.model.util.anonymizePublicHost
import org.meshtastic.core.network.transport.NetworkLinkTimeouts
import org.meshtastic.core.network.transport.ReconnectBackoff
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.network.transport.isTlsFailure
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The knobs tests shrink; production uses the defaults, shared with the TCP link where it has one. */
internal data class WebSocketTiming(
    val connectTimeout: Duration = NetworkLinkTimeouts.CONNECT_TIMEOUT_MS.milliseconds,
    val inactivityTimeout: Duration = NetworkLinkTimeouts.INACTIVITY_TIMEOUT_MS.milliseconds,
    val tryAgainLaterDelay: Duration = ReconnectBackoff.SHORT_SESSION_THRESHOLD_MS.milliseconds,
    val newBackoff: () -> ReconnectBackoff = { ReconnectBackoff() },
)

/**
 * The phone API over a WebSocket at [url], as firmware serves it at `/api/v1/ws`: one binary message per `ToRadio` or
 * `FromRadio`, no stream header, one client at a time.
 *
 * Reconnects by itself with [ReconnectBackoff], as [TcpTransport] does, until [close] or a close code that says
 * retrying cannot help: 4001 (another client took the slot) and 1009 (a message over firmware's limit). 1013 (the radio
 * is out of heap) waits at least [WebSocketTiming.tryAgainLaterDelay]. A link with nothing inbound for
 * [WebSocketTiming.inactivityTimeout] is dropped and redialled; firmware answers every [keepAlive] with a QueueStatus,
 * so silence means a half-open socket.
 *
 * ESP32 firmware aborts a displaced client without a close frame when it cannot write, so a 4001 may never arrive.
 * After [MAX_QUICK_DROPS] sessions in a row that open and then drop without a close frame inside
 * [ReconnectBackoff.SHORT_SESSION_THRESHOLD_MS], the transport stops with
 * [TransportDisconnectReason.WebSocketSlotContested] rather than keep taking the slot back from the other client.
 */
class WebSocketRadioTransport
internal constructor(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    private val url: String,
    private val timing: WebSocketTiming,
) : RadioTransport {

    constructor(
        callback: RadioTransportCallback,
        scope: CoroutineScope,
        url: String,
    ) : this(callback, scope, url, WebSocketTiming())

    private val lifecycle = TransportLifecycleGate("WebSocket")
    private val logLabel = url.toLogLabel()

    private val client = webSocketHttpClient(MAX_MESSAGE_BYTES.toLong(), timing.connectTimeout.inWholeMilliseconds)

    /** The open session's outbound queue, or null while no session is open. */
    @Volatile private var outbox: Channel<ByteArray>? = null

    private var loop: Job? = null
    private val clock = TimeSource.Monotonic.markNow()
    private val lastInboundNanos = atomic(0L)
    private val textFrameLogged = atomic(false)

    override fun start() {
        lifecycle.runIfOpen { loop = scope.launch { reconnectLoop() } }
    }

    private suspend fun reconnectLoop() {
        val backoff = timing.newBackoff()
        var reportedFailure: TransportDisconnectReason? = null
        var quickDrops = 0
        while (true) {
            val attempt = connectOnce()
            permanentReason(attempt.closeCode)?.let { reason ->
                Logger.w { "WebSocket $logLabel closed with ${attempt.closeCode}; not reconnecting" }
                lifecycle.runIfOpen { callback.onDisconnect(isPermanent = true, reason = reason) }
                return
            }
            val uptimeMs = attempt.openedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L
            if (attempt.openedAt != null) {
                reportedFailure = null
                quickDrops = if (attempt.isQuickDrop(uptimeMs)) quickDrops + 1 else 0
                if (quickDrops >= MAX_QUICK_DROPS) {
                    Logger.w { "WebSocket $logLabel dropped $quickDrops times right after opening; giving up" }
                    val reason = TransportDisconnectReason.WebSocketSlotContested
                    lifecycle.runIfOpen { callback.onDisconnect(isPermanent = true, reason = reason) }
                    return
                }
                lifecycle.runIfOpen { callback.onDisconnect(isPermanent = false) }
            } else {
                // One message per kind of failure, not one per retry.
                val failure = attempt.failure ?: TransportDisconnectReason.WebSocketConnectFailed
                if (failure != reportedFailure) {
                    reportedFailure = failure
                    lifecycle.runIfOpen { callback.onDisconnect(isPermanent = false, reason = failure) }
                }
            }
            backoff.onSessionEnded(attempt.hadData, uptimeMs)
            val tryAgainLater = attempt.closeCode == CloseReason.Codes.TRY_AGAIN_LATER.code
            val floor = if (tryAgainLater) timing.tryAgainLaterDelay else Duration.ZERO
            val wait = backoff.nextDelayMillis(floor.inWholeMilliseconds)
            Logger.i { "WebSocket $logLabel reconnect #${backoff.attempt - 1} in ${wait}ms" }
            delay(wait)
        }
    }

    /** One dial and, if it opens, one session; a watchdog cancels it once nothing has come in for too long. */
    private suspend fun connectOnce(): Attempt = coroutineScope {
        val attempt = Attempt()
        markInbound()
        val session = launch { runSession(attempt) }
        val watchdog = launch {
            while (true) {
                val idle = clock.elapsedNow() - lastInboundNanos.value.nanoseconds
                if (idle >= timing.inactivityTimeout) break
                delay(timing.inactivityTimeout - idle)
            }
            Logger.w { "WebSocket $logLabel heard nothing for ${timing.inactivityTimeout}; reconnecting" }
            attempt.timedOut = true
            session.cancel()
        }
        session.join()
        watchdog.cancel()
        attempt
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSession(attempt: Attempt) {
        try {
            client.webSocket(url) {
                attempt.openedAt = TimeSource.Monotonic.markNow()
                markInbound()
                val queue = Channel<ByteArray>(OUTBOX_CAPACITY)
                outbox = queue
                lifecycle.runIfOpen { callback.onConnect() }
                val writer = launch { for (bytes in queue) send(Frame.Binary(true, bytes)) }
                try {
                    receiveAll(attempt)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: FrameTooBigException) {
                    Logger.w { "WebSocket $logLabel sent a message over $MAX_MESSAGE_BYTES bytes" }
                    attempt.closeCode = CloseReason.Codes.TOO_BIG.code
                } catch (e: Exception) {
                    // The session may still have recorded why it closed; awaitCloseCode below reads it.
                    Logger.w { "WebSocket $logLabel read failed: ${e::class.simpleName}" }
                } finally {
                    outbox = null
                    queue.close()
                    writer.cancel()
                }
                attempt.closeCode = attempt.closeCode ?: awaitCloseCode()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt.openedAt == null) {
                attempt.failure = e.toWebSocketFailure()
                Logger.w { "WebSocket $logLabel connect failed: ${e::class.simpleName} (${attempt.failure})" }
            } else {
                Logger.w { "WebSocket $logLabel session failed: ${e::class.simpleName}" }
            }
        } finally {
            outbox = null
        }
    }

    private suspend fun DefaultClientWebSocketSession.receiveAll(attempt: Attempt) {
        for (frame in incoming) {
            markInbound()
            when (frame) {
                is Frame.Binary -> {
                    attempt.hadData = true
                    val bytes = frame.readBytes()
                    lifecycle.runIfOpen { callback.handleFromRadio(bytes) }
                }

                is Frame.Text ->
                    if (textFrameLogged.compareAndSet(expect = false, update = true)) {
                        Logger.w { "WebSocket $logLabel sent a text frame; the phone API is binary, dropping text" }
                    }

                else -> Unit
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.awaitCloseCode(): Short? =
        withTimeoutOrNull(CLOSE_REASON_WAIT) { closeReason.await() }?.code

    private fun markInbound() {
        lastInboundNanos.value = clock.elapsedNow().inWholeNanoseconds
    }

    override fun handleSendToRadio(p: ByteArray): Boolean {
        if (p.size > MAX_MESSAGE_BYTES) {
            // Firmware would close the socket with 1009 for this, so it never leaves.
            Logger.w { "WebSocket $logLabel refusing a ${p.size}-byte ToRadio over $MAX_MESSAGE_BYTES" }
            return false
        }
        return lifecycle.runIfOpen { outbox?.trySend(p)?.isSuccess ?: false } ?: false
    }

    override suspend fun close() {
        lifecycle.close(
            teardown = {
                loop?.cancelAndJoin()
                client.close()
            },
        )
    }

    // Firmware closes a socket that sends no ToRadio for 15 minutes (WS_IDLE_TIMEOUT_MS) and answers each heartbeat
    // with a QueueStatus, which keeps the inactivity watchdog fed.
    override fun keepAlive() {
        handleSendToRadio(HEARTBEAT)
    }

    private class Attempt {
        @Volatile var openedAt: TimeSource.Monotonic.ValueTimeMark? = null

        @Volatile var hadData = false

        @Volatile var closeCode: Short? = null

        @Volatile var failure: TransportDisconnectReason? = null

        @Volatile var timedOut = false

        /** Opened, then ended inside the short-session window with no close frame and no watchdog timeout. */
        fun isQuickDrop(uptimeMs: Long): Boolean {
            val noCloseFrame = closeCode == null || closeCode == CLOSED_ABNORMALLY
            return noCloseFrame && !timedOut && uptimeMs < ReconnectBackoff.SHORT_SESSION_THRESHOLD_MS
        }
    }

    internal companion object {
        const val OUTBOX_CAPACITY = 64

        /** Firmware's MAX_TO_FROM_RADIO_SIZE: it closes with 1009 past this, and never sends more. */
        const val MAX_MESSAGE_BYTES = StreamFrameCodec.MAX_TO_FROM_RADIO_SIZE

        /** Firmware's `WS_CLOSE_REPLACED`. */
        const val CLOSE_REPLACED: Short = 4001

        /** RFC 6455's code for a connection that ended without a close frame; Ktor records it but marks it internal. */
        const val CLOSED_ABNORMALLY: Short = 1006

        const val MAX_QUICK_DROPS = 5
        val CLOSE_REASON_WAIT = 1.seconds
        val HEARTBEAT: ByteArray =
            ToRadio.ADAPTER.encode(ToRadio.Builder().heartbeat(Heartbeat.Builder().build()).build())

        fun permanentReason(closeCode: Short?): TransportDisconnectReason? = when (closeCode) {
            CLOSE_REPLACED -> TransportDisconnectReason.ReplacedByAnotherClient

            // A retry would only replay the message firmware refused.
            CloseReason.Codes.TOO_BIG.code -> TransportDisconnectReason.WebSocketMessageTooBig

            else -> null
        }
    }
}

/**
 * What a failed dial tells the user. Ktor names a non-101 upgrade only in a [WebSocketException]'s message; its browser
 * engine raises the same type for any error event, a DNS or certificate failure included, with no status to report.
 */
internal fun Throwable.toWebSocketFailure(): TransportDisconnectReason = when {
    this is WebSocketException && message.orEmpty().startsWith(UPGRADE_REFUSED_PREFIX) ->
        TransportDisconnectReason.WebSocketHandshakeRejected

    isTlsFailure() -> TransportDisconnectReason.WebSocketTlsFailed

    else -> TransportDisconnectReason.WebSocketConnectFailed
}

private const val UPGRADE_REFUSED_PREFIX = "Handshake exception, expected status code"

/** The URL with a public host anonymized, as [TcpRadioTransport] logs its address. */
private fun String.toLogLabel(): String {
    val parsed = parseUrl(this) ?: return "<unparseable url>"
    val host = parsed.host.let { if (':' in it && !it.startsWith("[")) "[$it]" else it }
    return "${parsed.protocol.name}://${"$host:${parsed.port}".anonymizePublicHost()}${parsed.encodedPath}"
}
