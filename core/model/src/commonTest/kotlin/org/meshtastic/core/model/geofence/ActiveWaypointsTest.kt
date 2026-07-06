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
package org.meshtastic.core.model.geofence

import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveWaypointsTest {

    private val now = 1000L

    private fun packet(wp: Waypoint) = DataPacket(to = "!abcdabcd", channel = 0, waypoint = wp)

    /** The P1 regression: re-broadcasts of the same waypoint are separate rows; latest transmission must win. */
    @Test
    fun latestTransmissionWinsPerWaypointId() {
        val old = packet(Waypoint(id = 42, geofence_radius = 100, expire = 0))
        val new = packet(Waypoint(id = 42, geofence_radius = 5000, expire = 0))

        val active = listOf(old, new).activeWaypointPackets(now)

        assertEquals(1, active.size)
        assertEquals(5000, active[42]?.waypoint?.geofence_radius)
    }

    @Test
    fun expiredWaypointsAreDropped() {
        val expired = packet(Waypoint(id = 1, geofence_radius = 100, expire = 500)) // before now=1000
        val active = packet(Waypoint(id = 2, geofence_radius = 100, expire = 2000)) // after now
        val never = packet(Waypoint(id = 3, geofence_radius = 100, expire = 0)) // never expires

        val result = listOf(expired, active, never).activeWaypointPackets(now)

        assertFalse(result.containsKey(1))
        assertTrue(result.containsKey(2))
        assertTrue(result.containsKey(3))
    }

    private val myNodeNum = 7

    private fun geofence(id: Int) = Waypoint(id = id, geofence_radius = 100, notify_on_enter = true)

    private fun localPacket(wp: Waypoint) = DataPacket(to = "!abcdabcd", channel = 0, waypoint = wp) // from = ^local

    private fun remotePacket(wp: Waypoint) =
        DataPacket(to = "!abcdabcd", channel = 0, waypoint = wp).also { it.from = "!00000009" } // node 9

    @Test
    fun geofencesToMonitorKeepsOwnDropsForeign() {
        val packets = listOf(localPacket(geofence(1)), remotePacket(geofence(2)))

        val monitored = packets.geofencesToMonitor(myNodeNum, optedInIds = emptySet()).map { it.id }

        assertEquals(listOf(1), monitored) // only the locally-created geofence
    }

    @Test
    fun geofencesToMonitorIncludesOptedInForeign() {
        val packets = listOf(localPacket(geofence(1)), remotePacket(geofence(2)))

        val monitored = packets.geofencesToMonitor(myNodeNum, optedInIds = setOf(2)).map { it.id }.sorted()

        assertEquals(listOf(1, 2), monitored) // opting into #2 adds the foreign geofence back
    }

    @Test
    fun geofencesToMonitorDropsWaypointsWithNoCrossingNotifications() {
        val silent = localPacket(Waypoint(id = 1, geofence_radius = 100)) // notify_on_enter/exit both false

        assertTrue(listOf(silent).geofencesToMonitor(myNodeNum, optedInIds = emptySet()).isEmpty())
    }
}
