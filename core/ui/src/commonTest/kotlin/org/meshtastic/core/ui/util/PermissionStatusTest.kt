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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `requireAll false accepts a coarse-only grant`() {
        // Location requests FINE+COARSE; a coarse-only grant ([fine=false, coarse=true]) must count as granted (R7).
        assertTrue(isPermissionGroupGranted(results = listOf(false, true), requireAll = false))
        assertTrue(isPermissionGroupGranted(results = listOf(true, false), requireAll = false))
        assertFalse(isPermissionGroupGranted(results = listOf(false, false), requireAll = false))
    }

    @Test
    fun `requireAll true demands every permission`() {
        // Bluetooth needs both SCAN and CONNECT; a partial grant is not granted.
        assertTrue(isPermissionGroupGranted(results = listOf(true, true), requireAll = true))
        assertFalse(isPermissionGroupGranted(results = listOf(true, false), requireAll = true))
        assertFalse(isPermissionGroupGranted(results = listOf(false, false), requireAll = true))
    }

    @Test
    fun `a held permission proceeds`() {
        assertEquals(PermissionGateAction.PROCEED, permissionGateAction(PermissionStatus.GRANTED))
    }

    @Test
    fun `a first request skips the rationale`() {
        // The guidance is explicit that an unrequested permission can be asked for directly; a dialog before the
        // system dialog is friction with nothing extra to say.
        assertEquals(PermissionGateAction.REQUEST, permissionGateAction(PermissionStatus.NOT_REQUESTED))
    }

    @Test
    fun `a re-request must be preceded by a rationale`() {
        // The clause this whole type exists to make unmissable: shouldShowRequestPermissionRationale is true here, and
        // the prompt that follows is the one whose "Deny" becomes permanent.
        assertEquals(PermissionGateAction.SHOW_RATIONALE, permissionGateAction(PermissionStatus.DENIED_CAN_RETRY))
    }

    @Test
    fun `a permanent denial offers settings rather than a silent no-op request`() {
        assertEquals(PermissionGateAction.OPEN_SETTINGS, permissionGateAction(PermissionStatus.PERMANENTLY_DENIED))
    }

    @Test
    fun `every status maps to a distinct gate action`() {
        val actions = PermissionStatus.entries.map { permissionGateAction(it) }
        assertEquals(PermissionStatus.entries.size, actions.toSet().size, "each status needs its own recovery")
    }
}
