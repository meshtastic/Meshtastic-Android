/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.delivery_confirmed
import org.meshtastic.core.strings.error
import org.meshtastic.core.strings.message_delivery_status
import org.meshtastic.core.strings.message_status_enroute
import org.meshtastic.core.strings.message_status_queued
import org.meshtastic.core.strings.message_status_sfpp_confirmed
import org.meshtastic.core.strings.message_status_sfpp_routing
import org.meshtastic.core.strings.routing_error_admin_bad_session_key
import org.meshtastic.core.strings.routing_error_admin_public_key_unauthorized
import org.meshtastic.core.strings.routing_error_bad_request
import org.meshtastic.core.strings.routing_error_duty_cycle_limit
import org.meshtastic.core.strings.routing_error_got_nak
import org.meshtastic.core.strings.routing_error_max_retransmit
import org.meshtastic.core.strings.routing_error_no_channel
import org.meshtastic.core.strings.routing_error_no_interface
import org.meshtastic.core.strings.routing_error_no_response
import org.meshtastic.core.strings.routing_error_no_route
import org.meshtastic.core.strings.routing_error_none
import org.meshtastic.core.strings.routing_error_not_authorized
import org.meshtastic.core.strings.routing_error_pki_failed
import org.meshtastic.core.strings.routing_error_pki_send_fail_public_key
import org.meshtastic.core.strings.routing_error_pki_unknown_pubkey
import org.meshtastic.core.strings.routing_error_rate_limit_exceeded
import org.meshtastic.core.strings.routing_error_timeout
import org.meshtastic.core.strings.routing_error_too_large
import org.meshtastic.core.strings.unrecognized
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Routing

@Suppress("CyclomaticComplexMethod")
fun getStringResFrom(routingError: Int): StringResource = when (routingError) {
    Routing.Error.NONE.value -> Res.string.routing_error_none
    Routing.Error.NO_ROUTE.value -> Res.string.routing_error_no_route
    Routing.Error.GOT_NAK.value -> Res.string.routing_error_got_nak
    Routing.Error.TIMEOUT.value -> Res.string.routing_error_timeout
    Routing.Error.NO_INTERFACE.value -> Res.string.routing_error_no_interface
    Routing.Error.MAX_RETRANSMIT.value -> Res.string.routing_error_max_retransmit
    Routing.Error.NO_CHANNEL.value -> Res.string.routing_error_no_channel
    Routing.Error.TOO_LARGE.value -> Res.string.routing_error_too_large
    Routing.Error.NO_RESPONSE.value -> Res.string.routing_error_no_response
    Routing.Error.DUTY_CYCLE_LIMIT.value -> Res.string.routing_error_duty_cycle_limit
    Routing.Error.BAD_REQUEST.value -> Res.string.routing_error_bad_request
    Routing.Error.NOT_AUTHORIZED.value -> Res.string.routing_error_not_authorized
    Routing.Error.PKI_FAILED.value -> Res.string.routing_error_pki_failed
    Routing.Error.PKI_UNKNOWN_PUBKEY.value -> Res.string.routing_error_pki_unknown_pubkey
    Routing.Error.ADMIN_BAD_SESSION_KEY.value -> Res.string.routing_error_admin_bad_session_key
    Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED.value -> Res.string.routing_error_admin_public_key_unauthorized
    Routing.Error.RATE_LIMIT_EXCEEDED.value -> Res.string.routing_error_rate_limit_exceeded
    Routing.Error.PKI_SEND_FAIL_PUBLIC_KEY.value -> Res.string.routing_error_pki_send_fail_public_key
    else -> Res.string.unrecognized
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
    val relayNode: Int? = null,
    val relays: Int = 0,
    val filtered: Boolean = false,
    /** The transport mechanism this packet arrived over (see [MeshPacket.TransportMechanism]). */
    val transportMechanism: Int = 0,
) {
    fun getStatusStringRes(): Pair<StringResource, StringResource> {
        val title = if (routingError > 0) Res.string.error else Res.string.message_delivery_status
        val text =
            when (status) {
                MessageStatus.RECEIVED -> Res.string.delivery_confirmed
                MessageStatus.QUEUED -> Res.string.message_status_queued
                MessageStatus.ENROUTE -> Res.string.message_status_enroute
                MessageStatus.SFPP_ROUTING -> Res.string.message_status_sfpp_routing
                MessageStatus.SFPP_CONFIRMED -> Res.string.message_status_sfpp_confirmed
                else -> getStringResFrom(routingError)
            }
        return title to text
    }
}
