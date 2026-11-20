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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.network.transport.NetworkLinkTimeouts
import org.meshtastic.core.network.transport.ReconnectBackoff
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** The knobs tests shrink. */
internal data class HttpPollTiming(
    val requestTimeout: Duration = NetworkLinkTimeouts.CONNECT_TIMEOUT_MS.milliseconds,
    val minPoll: Duration = 100.milliseconds,
    val maxPoll: Duration = 1_000.milliseconds,
)

/**
 * The phone API over firmware's HTTP endpoints at [baseUrl]: `PUT /api/v1/toradio` with one `ToRadio`, `GET
 * /api/v1/fromradio` for one `FromRadio`, an empty body once the queue is empty.
 *
 * Every request goes out from one coroutine, one at a time, because an ESP32 serves two HTTPS connections at most.
 * The queue is read until it is empty, then polled with a delay that doubles from [HttpPollTiming.minPoll] to
 * [HttpPollTiming.maxPoll] and resets on any traffic. `?all=true` is never used: firmware concatenates its messages
 * with no framing. Firmware keeps one queue for every HTTP client, so a second client on the same radio steals
 * messages from this one.
 */
class HttpRadioTransport
internal constructor(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    private val baseUrl: String,
    private val timing: HttpPollTiming,
    private val client: HttpClient,
) : RadioTransport {

    constructor(
        callback: RadioTransportCallback,
        scope: CoroutineScope,
        baseUrl: String,
    ) : this(
        callback,
        scope,
        baseUrl.trimEnd('/'),
        HttpPollTiming(),
        webSocketHttpClient(StreamFrameCodec.MAX_TO_FROM_RADIO_SIZE.toLong(), NetworkLinkTimeouts.CONNECT_TIMEOUT_MS),
    )

    private val lifecycle = TransportLifecycleGate("HTTP")
    private val logLabel = baseUrl.toLogLabel()
    private val outbox = Channel<ByteArray>(OUTBOX_CAPACITY)
    private var loop: Job? = null

    @Volatile private var gets = 0

    @Volatile private var puts = 0

    override fun start() {
        lifecycle.runIfOpen { loop = scope.launch { reconnectLoop() } }
    }

    private suspend fun reconnectLoop() {
        val backoff = ReconnectBackoff()
        var reported = false
        while (true) {
            val started = TimeSource.Monotonic.markNow()
            var opened = false
            var hadData = false
            @Suppress("TooGenericExceptionCaught")
            try {
                session(onOpen = { opened = true }, onData = { hadData = true })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w { "HTTP $logLabel ${if (opened) "session" else "connect"} failed: ${e::class.simpleName}" }
            }
            if (opened) {
                reported = false
                lifecycle.runIfOpen { callback.onDisconnect(isPermanent = false) }
            } else if (!reported) {
                reported = true
                lifecycle.runIfOpen {
                    callback.onDisconnect(isPermanent = false, reason = TransportDisconnectReason.WebSocketConnectFailed)
                }
            }
            backoff.onSessionEnded(hadData, started.elapsedNow().inWholeMilliseconds)
            val wait = backoff.nextDelayMillis(0)
            Logger.i { "HTTP $logLabel reconnect #${backoff.attempt - 1} in ${wait}ms" }
            delay(wait)
        }
    }

    private suspend fun session(onOpen: () -> Unit, onData: () -> Unit) {
        // The first poll is the probe. A browser cannot send OPTIONS itself: it preflights it, and firmware allows only
        // GET on this path. A plain GET needs no preflight, and whatever it dequeues is ours.
        val first = getFromRadio()
        onOpen()
        val opened = TimeSource.Monotonic.markNow()
        Logger.i { "HTTP $logLabel open; polling" }
        lifecycle.runIfOpen { callback.onConnect() }
        if (first.isNotEmpty()) {
            onData()
            lifecycle.runIfOpen { callback.handleFromRadio(first) }
        }
        var idle = timing.minPoll
        var firstEmptyLogged = false
        while (true) {
            val pending = outbox.tryReceive().getOrNull()
            if (pending != null) {
                put(pending)
                idle = timing.minPoll
                continue
            }
            val bytes = getFromRadio()
            if (bytes.isNotEmpty()) {
                onData()
                lifecycle.runIfOpen { callback.handleFromRadio(bytes) }
                idle = timing.minPoll
                continue
            }
            if (!firstEmptyLogged) {
                firstEmptyLogged = true
                Logger.i { "HTTP $logLabel queue first empty after ${opened.elapsedNow()} ($gets GETs, $puts PUTs)" }
            }
            val next = withTimeoutOrNull(idle) { outbox.receive() }
            if (next != null) {
                put(next)
                idle = timing.minPoll
            } else {
                idle = (idle * 2).coerceAtMost(timing.maxPoll)
            }
        }
    }

    private suspend fun getFromRadio(): ByteArray {
        val bytes = request { client.get("$baseUrl$FROM_RADIO_PATH?all=false") }.bodyAsBytes()
        gets++
        return bytes
    }

    private suspend fun put(bytes: ByteArray) {
        request {
            client.put("$baseUrl$TO_RADIO_PATH") {
                contentType(PROTOBUF)
                setBody(bytes)
            }
        }
        puts++
    }

    private suspend fun request(block: suspend () -> HttpResponse): HttpResponse {
        val response = withTimeout(timing.requestTimeout) { block() }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return response
    }

    override fun handleSendToRadio(p: ByteArray): Boolean {
        if (p.size > StreamFrameCodec.MAX_TO_FROM_RADIO_SIZE) return false
        return lifecycle.runIfOpen { outbox.trySend(p).isSuccess } ?: false
    }

    override fun keepAlive() {
        handleSendToRadio(WebSocketRadioTransport.HEARTBEAT)
    }

    override suspend fun close() {
        lifecycle.close(
            teardown = {
                loop?.cancelAndJoin()
                outbox.close()
                client.close()
            },
        )
    }

    internal companion object {
        const val OUTBOX_CAPACITY = 64
        const val FROM_RADIO_PATH = "/api/v1/fromradio"
        const val TO_RADIO_PATH = "/api/v1/toradio"
        val PROTOBUF = ContentType("application", "x-protobuf")
    }
}
