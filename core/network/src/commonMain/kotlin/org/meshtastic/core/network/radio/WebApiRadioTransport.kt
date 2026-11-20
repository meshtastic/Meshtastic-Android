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
import io.ktor.client.plugins.websocket.webSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.model.util.WEBSOCKET_API_PATH
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A radio's web phone API at an `http://` or `https://` [baseUrl]: firmware's WebSocket at `/api/v1/ws` when it opens,
 * otherwise [HttpRadioTransport]'s polling. The WebSocket is tried on every start, so a radio whose firmware gains the
 * route moves to it with no change to the saved address.
 */
class WebApiRadioTransport(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    baseUrl: String,
) : RadioTransport {
    private val base = baseUrl.trimEnd('/')
    private val webSocketUrl = (if (base.startsWith("https", ignoreCase = true)) "wss" else "ws") +
        base.substringAfter(':') + WEBSOCKET_API_PATH
    private val logLabel = base.toLogLabel()

    @Volatile private var delegate: RadioTransport? = null

    @Volatile private var closed = false
    private val early = Channel<ByteArray>(HttpRadioTransport.OUTBOX_CAPACITY)
    private var probe: Job? = null

    override fun start() {
        probe = scope.launch {
            val started = TimeSource.Monotonic.markNow()
            val webSocket = webSocketOpens()
            Logger.i {
                "Web API $logLabel: ${if (webSocket) "WebSocket" else "no WebSocket, HTTP polling"} " +
                    "(probe ${started.elapsedNow()})"
            }
            if (closed) return@launch
            val chosen =
                if (webSocket) {
                    WebSocketRadioTransport(callback, scope, webSocketUrl)
                } else {
                    HttpRadioTransport(callback, scope, base)
                }
            delegate = chosen
            chosen.start()
            while (true) chosen.handleSendToRadio(early.tryReceive().getOrNull() ?: break)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun webSocketOpens(): Boolean {
        val client = webSocketHttpClient(WebSocketRadioTransport.MAX_MESSAGE_BYTES.toLong(), PROBE_TIMEOUT.inWholeMilliseconds)
        return try {
            withTimeoutOrNull(PROBE_TIMEOUT) {
                client.webSocket(webSocketUrl) { }
                true
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.i { "Web API $logLabel WebSocket probe: ${e::class.simpleName}: ${e.message}" }
            false
        } finally {
            client.close()
        }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean =
        delegate?.handleSendToRadio(p) ?: early.trySend(p).isSuccess

    override fun keepAlive() {
        delegate?.keepAlive()
    }

    override suspend fun close() {
        closed = true
        probe?.cancelAndJoin()
        early.close()
        delegate?.close()
    }

    private companion object {
        val PROBE_TIMEOUT = 5.seconds
    }
}
