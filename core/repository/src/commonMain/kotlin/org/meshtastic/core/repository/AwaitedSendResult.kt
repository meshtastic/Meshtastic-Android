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

/** Outcome of a packet whose caller waited for radio acceptance. */
enum class AwaitedSendStatus {
    /** An active transport dispatched the packet and the radio acknowledged it. */
    ACCEPTED,

    /** The app rejected the packet before it reached a transport, such as an invalid or duplicate packet ID. */
    REJECTED,

    /** The transport dispatched the packet, but the radio rejected it. */
    RADIO_REJECTED,

    /** No radio response arrived within the response window after the packet reached the queue head. */
    TIMED_OUT,

    /** The queue or owning service scope stopped before the radio answered; retry after the next connection. */
    TRANSPORT_STOPPED,

    /** No transport accepted the bytes, or the send attempt raised an error. */
    SEND_FAILED,
}

/**
 * Detailed result for an awaited send. [dispatched] is true only when an active transport accepted the outbound bytes
 * for asynchronous delivery. [departureEpochAtDispatch] captures the canonical departure counter at that admission
 * boundary, allowing callers to distinguish a later transport departure from one that happened while the packet was
 * still queued. Correlated responses received before dispatch are ignored, so an accepted result always belongs to an
 * admitted transport send.
 */
data class AwaitedSendResult(
    val status: AwaitedSendStatus,
    val dispatched: Boolean,
    val departureEpochAtDispatch: Long? = null,
) {
    init {
        require(dispatched == (departureEpochAtDispatch != null)) {
            "departureEpochAtDispatch must be set exactly when dispatched is true"
        }
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
    }

    val accepted: Boolean
        get() = status == AwaitedSendStatus.ACCEPTED
}
