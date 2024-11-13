/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.model

import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.Routing
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val Routing.Error.stringRes: Int
    get() = when (this) {
        Routing.Error.NONE -> R.string.routing_error_none
        Routing.Error.NO_ROUTE -> R.string.routing_error_no_route
        Routing.Error.GOT_NAK -> R.string.routing_error_got_nak
        Routing.Error.TIMEOUT -> R.string.routing_error_timeout
        Routing.Error.NO_INTERFACE -> R.string.routing_error_no_interface
        Routing.Error.MAX_RETRANSMIT -> R.string.routing_error_max_retransmit
        Routing.Error.NO_CHANNEL -> R.string.routing_error_no_channel
        Routing.Error.TOO_LARGE -> R.string.routing_error_too_large
        Routing.Error.NO_RESPONSE -> R.string.routing_error_no_response
        Routing.Error.DUTY_CYCLE_LIMIT -> R.string.routing_error_duty_cycle_limit
        Routing.Error.BAD_REQUEST -> R.string.routing_error_bad_request
        Routing.Error.NOT_AUTHORIZED -> R.string.routing_error_not_authorized
        Routing.Error.PKI_FAILED -> R.string.routing_error_pki_failed
        Routing.Error.PKI_UNKNOWN_PUBKEY -> R.string.routing_error_pki_unknown_pubkey
        Routing.Error.ADMIN_BAD_SESSION_KEY -> R.string.routing_error_admin_bad_session_key
        Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED -> R.string.routing_error_admin_public_key_unauthorized
        else -> R.string.unrecognized
    }

data class Message(
    val uuid: Long,
    val receivedTime: Long,
    val user: MeshProtos.User,
    val text: String,
    val time: String,
    val read: Boolean,
    val status: MessageStatus?,
    val routingError: Int,
) {
    private fun getStatusStringRes(value: Int): Int {
        val error = Routing.Error.forNumber(value) ?: Routing.Error.UNRECOGNIZED
        return error.stringRes
    }

    fun getStatusStringRes(): Pair<Int, Int> {
        val title = if (routingError > 0) R.string.error else R.string.message_delivery_status
        val text = when (status) {
            MessageStatus.RECEIVED -> R.string.delivery_confirmed
            MessageStatus.QUEUED -> R.string.message_status_queued
            MessageStatus.ENROUTE -> R.string.message_status_enroute
            else -> getStatusStringRes(routingError)
        }
        return title to text
    }
}

@Serializable
data class IntentMessage (
    val message: String,
    @SerialName("contact_key")
    val contactKey: String,
    @SerialName("contact_name")
    val contactName: String,
    @SerialName("auto_send")
    val autoSend: Boolean,
)