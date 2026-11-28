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
package org.meshtastic.core.service

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error_recovery_exhausted
import org.meshtastic.core.resources.firmware_too_old
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.serial_permission_denied
import org.meshtastic.core.resources.usb_permission_denied
import org.meshtastic.core.resources.websocket_connect_failed
import org.meshtastic.core.resources.websocket_handshake_rejected
import org.meshtastic.core.resources.websocket_message_too_big
import org.meshtastic.core.resources.websocket_not_supported
import org.meshtastic.core.resources.websocket_replaced_by_another_client
import org.meshtastic.core.resources.websocket_slot_contested
import org.meshtastic.core.resources.websocket_tls_failed

/** The user-facing text for [reason]. Lookup fails only outside a full resource runtime; the name beats silence. */
internal suspend fun connectionErrorText(reason: TransportDisconnectReason): String = safeCatchingAll {
    getStringSuspend(reason.toConnectionErrorMessage())
}
    .getOrDefault(reason.name)

internal fun TransportDisconnectReason.toConnectionErrorMessage(): StringResource = when (this) {
    TransportDisconnectReason.ReplacedByAnotherClient -> Res.string.websocket_replaced_by_another_client
    TransportDisconnectReason.WebSocketConnectFailed -> Res.string.websocket_connect_failed
    TransportDisconnectReason.WebSocketHandshakeRejected -> Res.string.websocket_handshake_rejected
    TransportDisconnectReason.WebSocketNotSupported -> Res.string.websocket_not_supported
    TransportDisconnectReason.WebSocketTlsFailed -> Res.string.websocket_tls_failed
    TransportDisconnectReason.WebSocketMessageTooBig -> Res.string.websocket_message_too_big
    TransportDisconnectReason.WebSocketSlotContested -> Res.string.websocket_slot_contested
    TransportDisconnectReason.HandshakeRecoveryExhausted -> Res.string.error_recovery_exhausted
    TransportDisconnectReason.FirmwareTooOld -> Res.string.firmware_too_old
    TransportDisconnectReason.UsbPermissionDenied -> Res.string.usb_permission_denied
    TransportDisconnectReason.SerialPermissionDenied -> Res.string.serial_permission_denied
}
