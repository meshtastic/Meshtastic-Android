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
package org.meshtastic.core.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionStatusTest {

    @Test
    fun `granted always wins regardless of other inputs`() {
        // All four granted=true combinations resolve to GRANTED.
        for (hasRequested in listOf(true, false)) {
            for (shouldShowRationale in listOf(true, false)) {
                assertEquals(
                    PermissionStatus.GRANTED,
                    computePermissionStatus(
                        granted = true,
                        hasRequested = hasRequested,
                        shouldShowRationale = shouldShowRationale,
                    ),
                    "granted=true, hasRequested=$hasRequested, shouldShowRationale=$shouldShowRationale",
                )
            }
        }
    }

    @Test
    fun `not requested when the user has never been prompted`() {
        // shouldShowRationale is false before the first prompt — must NOT be read as permanent denial.
        assertEquals(
            PermissionStatus.NOT_REQUESTED,
            computePermissionStatus(granted = false, hasRequested = false, shouldShowRationale = false),
        )
        // Even if the system somehow reports rationale before a request, the unrequested flag dominates.
        assertEquals(
            PermissionStatus.NOT_REQUESTED,
            computePermissionStatus(granted = false, hasRequested = false, shouldShowRationale = true),
        )
    }

    @Test
    fun `denied can retry when requested and rationale should still show`() {
        assertEquals(
            PermissionStatus.DENIED_CAN_RETRY,
            computePermissionStatus(granted = false, hasRequested = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `permanently denied only when requested and rationale suppressed`() {
        // The adversarial-flagged case: this resolves to PERMANENTLY_DENIED ONLY because hasRequested reflects a
        // COMPLETED request (set from the launcher result callback, never at launch() time).
        assertEquals(
            PermissionStatus.PERMANENTLY_DENIED,
            computePermissionStatus(granted = false, hasRequested = true, shouldShowRationale = false),
        )
    }
}
