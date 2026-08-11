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

/** Outcome of a packet whose caller waited for transport admission and any required routing result. */
enum class AwaitedSendStatus {
    /** The radio acknowledged the packet, or firmware completed synchronous local-loopback delivery. */
    ACCEPTED,

    /** The app rejected the packet before it reached a transport, such as an invalid or duplicate packet ID. */
    REJECTED,

    /** The transport dispatched the packet, but the radio rejected it. */
    RADIO_REJECTED,

    /** No required radio response arrived within the response window after transport admission. */
    TIMED_OUT,

    /**
     * The queue or owning service scope stopped before the radio answered. Inspect [AwaitedSendResult.dispatched]
     * before retrying because the transport can stop after admission.
     */
    TRANSPORT_STOPPED,

    /** No transport accepted the bytes, or the send attempt raised an error. */
    SEND_FAILED,
}

/**
 * Detailed result for an awaited send. [departureEpochAtDispatch] captures the canonical departure counter when an
 * active transport accepts the outbound bytes for asynchronous delivery. Its presence is also the single source of
 * truth for [dispatched], allowing callers to distinguish a later transport departure from one that happened while the
 * packet was still queued. Correlated responses received before dispatch are ignored, so an accepted result always
 * belongs to an admitted transport send.
 */
data class AwaitedSendResult(val status: AwaitedSendStatus, val departureEpochAtDispatch: Long? = null) {
    /** True only when an active transport admitted the outbound bytes. */
    val dispatched: Boolean
        get() = departureEpochAtDispatch != null

    val accepted: Boolean
        get() = status == AwaitedSendStatus.ACCEPTED

    // TRANSPORT_STOPPED intentionally has no dispatch invariant: teardown can win before or after byte admission.
    init {
        require(status != AwaitedSendStatus.REJECTED || !dispatched) {
            "a REJECTED result must not come from an admitted transport send"
        }
        require(status != AwaitedSendStatus.RADIO_REJECTED || dispatched) {
            "a RADIO_REJECTED result must come from an admitted transport send"
        }
        require(status != AwaitedSendStatus.ACCEPTED || dispatched) {
            "an ACCEPTED result must come from an admitted transport send"
        }
        require(status != AwaitedSendStatus.TIMED_OUT || dispatched) {
            "a TIMED_OUT result must come from an admitted transport send"
        }
        require(status != AwaitedSendStatus.SEND_FAILED || !dispatched) {
            "a SEND_FAILED result must not claim transport dispatch"
        }
    }
}
