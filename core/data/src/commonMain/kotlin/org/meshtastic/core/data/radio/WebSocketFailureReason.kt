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
package org.meshtastic.core.data.radio

import org.meshtastic.core.model.util.WEBSOCKET_API_PATH
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.transport.websocket.WebSocketTransportException

private const val CLOSE_REPLACED = 4001
private const val CLOSE_TOO_BIG = 1009
private const val HTTP_NOT_FOUND = 404

// The exception has no field for an untrusted certificate; its message is the only sign.
private const val TLS_UNTRUSTED = "TLS certificate for"

/** What a WebSocket failure to reach [url] means to the user, from the close or upgrade the SDK recorded. */
internal fun webSocketFailureReason(cause: MeshtasticException, url: String): TransportDisconnectReason? {
    val failure = cause as? WebSocketTransportException ?: return null
    return when {
        failure.slotContested -> TransportDisconnectReason.WebSocketSlotContested

        failure.closeCode == CLOSE_REPLACED -> TransportDisconnectReason.ReplacedByAnotherClient

        failure.closeCode == CLOSE_TOO_BIG -> TransportDisconnectReason.WebSocketMessageTooBig

        failure.httpStatus == HTTP_NOT_FOUND && url.isFirmwareEndpoint() ->
            TransportDisconnectReason.WebSocketNotSupported

        failure.httpStatus != null -> TransportDisconnectReason.WebSocketHandshakeRejected

        failure.cause?.message.orEmpty().startsWith(TLS_UNTRUSTED) ||
            failure.message.orEmpty().startsWith(TLS_UNTRUSTED) -> TransportDisconnectReason.WebSocketTlsFailed

        // A failed connect carries no close code; a drop without a close frame is an ordinary reconnect.
        failure.closeCode == null && failure.closeReason == null -> TransportDisconnectReason.WebSocketConnectFailed

        else -> null
    }
}

private fun String.isFirmwareEndpoint(): Boolean {
    val path = substringAfter("://").substringBefore('?').substringAfter('/', "")
    return path.isEmpty() || "/$path" == WEBSOCKET_API_PATH
}
