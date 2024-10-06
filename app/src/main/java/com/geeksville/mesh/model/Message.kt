package com.geeksville.mesh.model

import com.geeksville.mesh.MeshProtos.Routing
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R

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
    val user: MeshUser,
    val text: String,
    val time: Long,
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
