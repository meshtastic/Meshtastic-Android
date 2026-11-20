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
package org.meshtastic.core.repository

/** Machine-readable transport disconnect causes that need service/UI-specific handling. */
enum class TransportDisconnectReason {
    /** Android denied USB device access for the current connection attempt. */
    UsbPermissionDenied,

    /** The radio gave its one WebSocket client slot to another client, WebSocket close code 4001. */
    ReplacedByAnotherClient,

    /** A WebSocket could not be opened: the host refused, timed out or did not resolve. */
    WebSocketConnectFailed,

    /** The server answered a WebSocket upgrade with something other than 101, such as a 404 for a wrong path. */
    WebSocketHandshakeRejected,

    /** The `wss://` TLS handshake failed, most often on the radio's self-signed certificate. */
    WebSocketTlsFailed,

    /** The radio closed the WebSocket with 1009: a message over its 512-byte limit. */
    WebSocketMessageTooBig,

    /** The WebSocket kept dropping right after opening without a close frame, the sign of two clients taking turns. */
    WebSocketSlotContested,
}
