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
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio
import kotlin.time.Duration.Companion.seconds

/**
 * The phone API over a WebSocket at [url], as firmware serves it at `/api/v1/ws`: one binary message per `ToRadio` or
 * `FromRadio`, no stream header. One-shot, as [TcpRadioTransport] is: a dropped socket reports a transient disconnect
 * and the service builds a fresh transport to reconnect.
 */
class WebSocketRadioTransport(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    private val url: String,
    private val clientFactory: () -> HttpClient = { HttpClient { install(WebSockets) } },
) : RadioTransport {
    private val lifecycle = TransportLifecycleGate("WebSocket")
    private val outbox = Channel<ByteArray>(OUTBOX_CAPACITY)
    private val connected = atomic(false)
    private var session: Job? = null

    override fun start() {
        lifecycle.runIfOpen { session = scope.launch { run() } }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun run() {
        val client = clientFactory()
        var closeCode: Short? = null
        try {
            client.webSocket(url) {
                connected.value = true
                lifecycle.runIfOpen { callback.onConnect() }
                val writer = launch { for (bytes in outbox) send(Frame.Binary(true, bytes)) }
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Binary) {
                            val bytes = frame.readBytes()
                            lifecycle.runIfOpen { callback.handleFromRadio(bytes) }
                        }
                    }
                    closeCode = withTimeoutOrNull(CLOSE_REASON_WAIT) { closeReason.await() }?.code
                } finally {
                    writer.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "WebSocket phone link failed: ${e.message}" }
        } finally {
            connected.value = false
            client.close()
        }
        // Firmware gives its one WebSocket slot to the newest client and tells the one it displaced with 4001.
        // Reconnecting would take the slot straight back, so two apps would keep displacing each other.
        if (closeCode == CLOSE_REPLACED) {
            Logger.w { "WebSocket phone link replaced by another client" }
            lifecycle.runIfOpen {
                callback.onDisconnect(isPermanent = true, reason = TransportDisconnectReason.ReplacedByAnotherClient)
            }
        } else {
            lifecycle.runIfOpen { callback.onDisconnect(isPermanent = false) }
        }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean {
        if (!connected.value) return false
        return lifecycle.runIfOpen { outbox.trySend(p).isSuccess } ?: false
    }

    override suspend fun close() {
        lifecycle.close(
            teardown = {
                session?.cancelAndJoin()
                outbox.close()
            },
        )
    }

    // Firmware closes a socket that sends no ToRadio for 15 minutes (WS_IDLE_TIMEOUT_MS).
    override fun keepAlive() {
        handleSendToRadio(HEARTBEAT)
    }

    private companion object {
        const val OUTBOX_CAPACITY = 64

        /** Firmware's `WS_CLOSE_REPLACED`. */
        const val CLOSE_REPLACED: Short = 4001
        val CLOSE_REASON_WAIT = 1.seconds
        val HEARTBEAT: ByteArray =
            ToRadio.ADAPTER.encode(ToRadio.Builder().heartbeat(Heartbeat.Builder().build()).build())
    }
}
