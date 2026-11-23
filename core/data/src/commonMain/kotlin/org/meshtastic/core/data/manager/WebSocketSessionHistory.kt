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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.delay
import org.meshtastic.core.model.util.WEBSOCKET_API_PATH
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.transport.websocket.WebSocketTransportException
import kotlin.time.TimeSource

/** How a PhoneAPI session ended: whether a retry may help, and what to tell the user. */
internal data class SessionEnd(val recoverable: Boolean, val reason: TransportDisconnectReason? = null)

/**
 * The state the SDK's WebSocketTransport keeps across connects on one instance, kept here across the fresh transport
 * each app retry builds. ESP32 firmware drops a displaced client without a close frame, so five sessions in a row that
 * open and drop that way within 30 s mean another client holds the radio's one slot. A 1013 close (the radio is out of
 * heap) holds the next connect for 30 s. [url] is the endpoint dialed, where a 404 at firmware's own path means the
 * firmware predates the WebSocket phone API.
 */
internal class WebSocketSessionHistory(url: String, private val clockMs: () -> Long = monotonicMs()) {
    private val firmwareEndpoint = url.isFirmwareEndpoint()
    private var quickDrops = 0
    private var retryNotBeforeMs: Long? = null
    private var openedAtMs: Long? = null

    /** Starts a session, holding it until a 1013 floor has passed. */
    suspend fun beforeConnect() {
        openedAtMs = null
        val wait = (retryNotBeforeMs ?: return) - clockMs()
        if (wait > 0) delay(wait)
    }

    /** The socket opened. */
    fun onOpened() {
        if (openedAtMs == null) openedAtMs = clockMs()
    }

    /** Classifies the end of a session whose transport last reported [error]. */
    fun onEnded(error: TransportState.Error?): SessionEnd {
        val now = clockMs()
        val opened = openedAtMs.also { openedAtMs = null }
        val cause = error?.cause as? WebSocketTransportException
        retryNotBeforeMs = if (cause?.closeCode == CLOSE_TRY_AGAIN_LATER) now + TRY_AGAIN_FLOOR_MS else null
        if (opened != null) {
            val noCloseFrame = cause != null && (cause.closeCode == null || cause.closeCode == CLOSED_ABNORMALLY)
            val quick = noCloseFrame && error.recoverable && now - opened < SHORT_SESSION_MS
            quickDrops = if (quick) quickDrops + 1 else 0
        }
        return when {
            opened == null -> SessionEnd(error?.recoverable ?: true, error?.let { connectFailure(it, cause) })

            error?.recoverable == false -> SessionEnd(recoverable = false, reason = cause?.closeReason())

            quickDrops >= MAX_QUICK_DROPS -> {
                quickDrops = 0
                SessionEnd(recoverable = false, reason = TransportDisconnectReason.WebSocketSlotContested)
            }

            else -> SessionEnd(recoverable = true)
        }
    }

    private fun WebSocketTransportException.closeReason(): TransportDisconnectReason? = when {
        slotContested -> TransportDisconnectReason.WebSocketSlotContested
        closeCode == CLOSE_REPLACED -> TransportDisconnectReason.ReplacedByAnotherClient
        closeCode == CLOSE_TOO_BIG -> TransportDisconnectReason.WebSocketMessageTooBig
        else -> null
    }

    private fun connectFailure(error: TransportState.Error, cause: WebSocketTransportException?) = when {
        cause?.httpStatus == HTTP_NOT_FOUND && firmwareEndpoint -> TransportDisconnectReason.WebSocketNotSupported

        cause?.httpStatus != null -> TransportDisconnectReason.WebSocketHandshakeRejected

        // The exception has no field for an untrusted certificate; its message is the only sign.
        error.cause.message.orEmpty().startsWith(TLS_UNTRUSTED) -> TransportDisconnectReason.WebSocketTlsFailed

        else -> TransportDisconnectReason.WebSocketConnectFailed
    }

    private companion object {
        const val SHORT_SESSION_MS = 30_000L
        const val TRY_AGAIN_FLOOR_MS = 30_000L
        const val MAX_QUICK_DROPS = 5
        const val HTTP_NOT_FOUND = 404

        // Firmware's WebSocket close codes; 1006 is the code a client reports for a drop with no close frame.
        const val CLOSE_REPLACED = 4001
        const val CLOSE_TOO_BIG = 1009
        const val CLOSE_TRY_AGAIN_LATER = 1013
        const val CLOSED_ABNORMALLY = 1006
        const val TLS_UNTRUSTED = "TLS certificate for"

        fun String.isFirmwareEndpoint(): Boolean {
            val path = substringAfter("://").substringBefore('?').substringAfter('/', "")
            return path.isEmpty() || "/$path" == WEBSOCKET_API_PATH
        }

        fun monotonicMs(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }
    }
}
