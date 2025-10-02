/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.database.model

import androidx.annotation.StringRes
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.R
import org.meshtastic.proto.MeshProtos.Routing

@Suppress("CyclomaticComplexMethod")
@StringRes
fun getStringResFrom(routingError: Int): Int = when (routingError) {
    Routing.Error.NONE_VALUE -> R.string.routing_error_none
    Routing.Error.NO_ROUTE_VALUE -> R.string.routing_error_no_route
    Routing.Error.GOT_NAK_VALUE -> R.string.routing_error_got_nak
    Routing.Error.TIMEOUT_VALUE -> R.string.routing_error_timeout
    Routing.Error.NO_INTERFACE_VALUE -> R.string.routing_error_no_interface
    Routing.Error.MAX_RETRANSMIT_VALUE -> R.string.routing_error_max_retransmit
    Routing.Error.NO_CHANNEL_VALUE -> R.string.routing_error_no_channel
    Routing.Error.TOO_LARGE_VALUE -> R.string.routing_error_too_large
    Routing.Error.NO_RESPONSE_VALUE -> R.string.routing_error_no_response
    Routing.Error.DUTY_CYCLE_LIMIT_VALUE -> R.string.routing_error_duty_cycle_limit
    Routing.Error.BAD_REQUEST_VALUE -> R.string.routing_error_bad_request
    Routing.Error.NOT_AUTHORIZED_VALUE -> R.string.routing_error_not_authorized
    Routing.Error.PKI_FAILED_VALUE -> R.string.routing_error_pki_failed
    Routing.Error.PKI_UNKNOWN_PUBKEY_VALUE -> R.string.routing_error_pki_unknown_pubkey
    Routing.Error.ADMIN_BAD_SESSION_KEY_VALUE -> R.string.routing_error_admin_bad_session_key
    Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED_VALUE -> R.string.routing_error_admin_public_key_unauthorized
    Routing.Error.RATE_LIMIT_EXCEEDED_VALUE -> R.string.routing_error_rate_limit_exceeded
    else -> R.string.unrecognized
}

data class Message(
    val uuid: Long,
    val receivedTime: Long,
    val node: Node,
    val text: String,
    val fromLocal: Boolean,
    val time: String,
    val read: Boolean,
    val status: MessageStatus?,
    val routingError: Int,
    val packetId: Int,
    val emojis: List<Reaction>,
    val snr: Float,
    val rssi: Int,
    val hopsAway: Int,
    val replyId: Int?,
    val originalMessage: Message? = null,
    val viaMqtt: Boolean = false,
) {
    fun getStatusStringRes(): Pair<Int, Int> {
        val title = if (routingError > 0) R.string.error else R.string.message_delivery_status
        val text =
            when (status) {
                MessageStatus.RECEIVED -> R.string.delivery_confirmed
                MessageStatus.QUEUED -> R.string.message_status_queued
                MessageStatus.ENROUTE -> R.string.message_status_enroute
                else -> getStringResFrom(routingError)
            }
        return title to text
    }
}
