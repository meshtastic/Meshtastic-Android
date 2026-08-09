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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AwaitedSendResultTest {
    @Test
    fun validDispatchPairingPreservesAcceptedMapping() {
        val result = AwaitedSendResult(status = AwaitedSendStatus.ACCEPTED, departureEpochAtDispatch = 7L)

        assertEquals(true, result.accepted)
        assertEquals(true, result.dispatched)
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

        assertEquals(false, result.accepted)
        assertEquals(false, result.dispatched)
    }
}
