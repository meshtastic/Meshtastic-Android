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
package org.meshtastic.core.model

import org.meshtastic.proto.Waypoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaypointLockTest {

    private val myNodeNum = 0x433d0d3c
    private val otherNodeNum = 0x1a2b3c4d

    @Test
    fun unlockedWaypointIsNotLocked() {
        assertFalse(Waypoint(id = 1, locked_to = 0).isLocked)
    }

    @Test
    fun lockedWaypointIsLocked() {
        assertTrue(Waypoint(id = 1, locked_to = myNodeNum).isLocked)
    }

    @Test
    fun unlockedWaypointIsModifiableByAnyone() {
        val wp = Waypoint(id = 1, locked_to = 0)
        assertTrue(wp.isModifiableBy(myNodeNum))
        assertTrue(wp.isModifiableBy(otherNodeNum))
        // Editable by anyone includes the "identity unknown" case.
        assertTrue(wp.isModifiableBy(null))
    }

    @Test
    fun lockedWaypointIsModifiableOnlyByOwner() {
        val wp = Waypoint(id = 1, locked_to = myNodeNum)
        assertTrue(wp.isModifiableBy(myNodeNum))
        assertFalse(wp.isModifiableBy(otherNodeNum))
        assertFalse(wp.isModifiableBy(null))
    }

    /**
     * Regression for #6343: the "locked" toggle used to write the placeholder `1` instead of the creator's node number.
     * A placeholder never equals a real node number, so the creator's own edit/delete gates all failed. Locking to the
     * real node number is what lets the owner manage the waypoint.
     */
    @Test
    fun placeholderLockShutsOutTheCreatorButRealOwnerLockDoesNot() {
        assertFalse(Waypoint(id = 1, locked_to = 1).isModifiableBy(myNodeNum))
        assertTrue(Waypoint(id = 1, locked_to = myNodeNum).isModifiableBy(myNodeNum))
    }
}
