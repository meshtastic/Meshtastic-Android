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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GeofenceCrossingStoreTest {

    @Test
    fun firstSightingIsBaselineThenTransitionsOnlyOnChange() = runTest {
        val store = GeofenceCrossingStore()

        // First sighting establishes a baseline — never a notification.
        assertEquals(GeofenceTransition.BASELINE, store.update(waypointId = 1, nodeNum = 9, isInside = false))
        // Same side → no change.
        assertEquals(GeofenceTransition.UNCHANGED, store.update(1, 9, false))
        // outside -> inside.
        assertEquals(GeofenceTransition.ENTERED, store.update(1, 9, true))
        assertEquals(GeofenceTransition.UNCHANGED, store.update(1, 9, true))
        // inside -> outside.
        assertEquals(GeofenceTransition.EXITED, store.update(1, 9, false))
    }

    @Test
    fun stateIsIndependentPerWaypointAndNode() = runTest {
        val store = GeofenceCrossingStore()
        assertEquals(GeofenceTransition.BASELINE, store.update(1, 9, true))
        // Different node, same waypoint — its own baseline.
        assertEquals(GeofenceTransition.BASELINE, store.update(1, 10, true))
        // Different waypoint, same node — its own baseline.
        assertEquals(GeofenceTransition.BASELINE, store.update(2, 9, true))
    }

    @Test
    fun retainOnlyForgetsInactiveWaypoints() = runTest {
        val store = GeofenceCrossingStore()
        store.update(1, 9, true)
        store.update(2, 9, true)

        store.retainOnly(setOf(2))

        // Waypoint 1 was forgotten → re-baselines; waypoint 2 was kept.
        assertEquals(GeofenceTransition.BASELINE, store.update(1, 9, true))
        assertEquals(GeofenceTransition.UNCHANGED, store.update(2, 9, true))
    }
}
