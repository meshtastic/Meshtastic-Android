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

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwaitedSendResultTest {
    @Test
    fun validDispatchPairingPreservesAcceptedMapping() {
        val result = AwaitedSendResult(status = AwaitedSendStatus.ACCEPTED, departureEpochAtDispatch = 7L)

        assertTrue(result.accepted)
        assertTrue(result.dispatched)
    }

    @Test
    fun acceptedStatusRequiresDispatch() {
        assertFailsWith<IllegalArgumentException> { AwaitedSendResult(AwaitedSendStatus.ACCEPTED) }
    }

    @Test
    fun rejectedStatusCannotClaimTransportDispatch() {
        assertFailsWith<IllegalArgumentException> {
            AwaitedSendResult(AwaitedSendStatus.REJECTED, departureEpochAtDispatch = 7L)
        }
    }

    @Test
    fun radioRejectedStatusRequiresTransportDispatch() {
        assertFailsWith<IllegalArgumentException> { AwaitedSendResult(AwaitedSendStatus.RADIO_REJECTED) }
    }

    @Test
    fun timedOutStatusRequiresTransportDispatch() {
        assertFailsWith<IllegalArgumentException> { AwaitedSendResult(AwaitedSendStatus.TIMED_OUT) }
    }

    @Test
    fun rejectedNonDispatchedResultIsValidAndNotAccepted() {
        val result = AwaitedSendResult(AwaitedSendStatus.REJECTED)

        assertFalse(result.accepted)
        assertFalse(result.dispatched)
    }

    @Test
    fun transportStoppedMayOccurBeforeOrAfterTransportDispatch() {
        val beforeDispatch = AwaitedSendResult(AwaitedSendStatus.TRANSPORT_STOPPED)
        val afterDispatch = AwaitedSendResult(AwaitedSendStatus.TRANSPORT_STOPPED, departureEpochAtDispatch = 7L)

        assertFalse(beforeDispatch.accepted)
        assertFalse(beforeDispatch.dispatched)
        assertFalse(afterDispatch.accepted)
        assertTrue(afterDispatch.dispatched)
    }

    @Test
    fun sendFailedStatusCannotClaimTransportDispatch() {
        val result = AwaitedSendResult(AwaitedSendStatus.SEND_FAILED)
        assertFalse(result.accepted)
        assertFalse(result.dispatched)
        assertFailsWith<IllegalArgumentException> {
            AwaitedSendResult(AwaitedSendStatus.SEND_FAILED, departureEpochAtDispatch = 7L)
        }
    }

    @Test
    fun radioRejectedAndTimedOutResultsAreNotAccepted() {
        assertFalse(AwaitedSendResult(AwaitedSendStatus.RADIO_REJECTED, 7L).accepted)
        assertFalse(AwaitedSendResult(AwaitedSendStatus.TIMED_OUT, 7L).accepted)
    }
}
