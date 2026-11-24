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

import org.meshtastic.proto.Routing
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.MessageHandle
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.SendFailure
import org.meshtastic.sdk.SendOutcome

/**
 * A node request the radio admitted.
 *
 * [awaitRoutingError] suspends until the request settles and returns the [Routing.Error] value that failed it, or null
 * when it was answered, went unanswered, or lost its link. [requestId] is the wire ID when the sender knows it.
 */
class NodeRequest(val requestId: Int?, private val outcome: suspend () -> Int?) {
    suspend fun awaitRoutingError(): Int? = outcome()

    companion object {
        /** A request that was not sent because it has no recipient, such as one addressed to our own node. */
        val NONE = NodeRequest(requestId = null) { null }
    }
}

/** Tracks [MessageHandle.await] as a [NodeRequest]. */
fun MessageHandle.toNodeRequest(): NodeRequest = NodeRequest(id.raw) { await().routingErrorOrNull() }

/** Tracks a paced send's delivery outcome as a [NodeRequest]. */
fun PacedMessageHandle.toNodeRequest(): NodeRequest = message.toNodeRequest()

/** The routing error a failed send carries; null for success and for failures the mesh did not report. */
fun SendOutcome.routingErrorOrNull(): Int? = when (this) {
    SendOutcome.Success -> null
    is SendOutcome.Failure -> reason.routingErrorOrNull()
}

private fun SendFailure.routingErrorOrNull(): Int? = when (this) {
    SendFailure.NoRoute -> Routing.Error.NO_ROUTE.value

    SendFailure.MaxRetransmit -> Routing.Error.MAX_RETRANSMIT.value

    SendFailure.Timeout -> Routing.Error.TIMEOUT.value

    SendFailure.DutyCycleLimit -> Routing.Error.DUTY_CYCLE_LIMIT.value

    SendFailure.RateLimited -> Routing.Error.RATE_LIMIT_EXCEEDED.value

    // Firmware reports a refused phone packet both as this receipt and as a NAK carrying the same code.
    is SendFailure.QueueRejected -> Routing.Error.fromValue(res)?.takeIf { it != Routing.Error.NONE }?.value

    is SendFailure.Other -> routingError.value

    SendFailure.AckTimeout,
    SendFailure.Disconnected,
    SendFailure.HandshakeFailed,
    SendFailure.Cancelled,
    SendFailure.IdCollision,
    SendFailure.QueueFull,
    is SendFailure.Unknown,
    -> null
}

/** The routing error a failed RPC carries; null for success, a timeout and a lost link. */
fun AdminResult<*>.routingErrorOrNull(): Int? = when (this) {
    is AdminResult.Failed -> routingError.value

    AdminResult.RateLimited -> Routing.Error.RATE_LIMIT_EXCEEDED.value

    // The SDK folds NOT_AUTHORIZED and ADMIN_PUBLIC_KEY_UNAUTHORIZED into Unauthorized; the key refusal is the
    // actionable one.
    AdminResult.Unauthorized -> Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED.value

    AdminResult.SessionKeyExpired -> Routing.Error.ADMIN_BAD_SESSION_KEY.value

    is AdminResult.Success,
    AdminResult.Timeout,
    AdminResult.NodeUnreachable,
    -> null
}
