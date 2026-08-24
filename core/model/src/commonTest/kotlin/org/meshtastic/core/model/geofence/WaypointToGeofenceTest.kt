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

import org.meshtastic.proto.BoundingBox
import org.meshtastic.proto.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaypointToGeofenceTest {

    private val box =
        BoundingBox(
            longitude_west_i = 200_000_000,
            latitude_south_i = 100_000_000,
            longitude_east_i = 210_000_000,
            latitude_north_i = 110_000_000,
        )

    @Test
    fun radiusOnlyDecodesCircleNoBox() {
        val wp = Waypoint(id = 1, latitude_i = 100_000_000, longitude_i = 200_000_000, geofence_radius = 500)
        val geofence = wp.toGeofence()
        assertNotNullCircleAt(geofence?.circle, 10.0, 20.0, 500)
        assertNull(geofence?.box)
    }

    @Test
    fun boxOnlyDecodesBoxNoCircle() {
        val wp = Waypoint(id = 1, bounding_box = box)
        val geofence = wp.toGeofence()
        assertNull(geofence?.circle)
        assertEquals(GeofenceBox(south = 10.0, west = 20.0, north = 11.0, east = 21.0), geofence?.box)
    }

    @Test
    fun neitherShapeDecodesToNull() {
        assertNull(Waypoint(id = 1).toGeofence())
        assertNull(Waypoint(id = 1, geofence_radius = 0).toGeofence())
    }

    @Test
    fun bothShapesDecode() {
        val wp =
            Waypoint(
                id = 1,
                latitude_i = 100_000_000,
                longitude_i = 200_000_000,
                geofence_radius = 500,
                bounding_box = box,
            )
        val geofence = wp.toGeofence()
        assertNotNullCircleAt(geofence?.circle, 10.0, 20.0, 500)
        assertEquals(GeofenceBox(south = 10.0, west = 20.0, north = 11.0, east = 21.0), geofence?.box)
    }

    @Test
    fun invertedBoundingBoxIsNormalized() {
        // A box whose corners arrive transposed (south>north, west>east) should still describe the intended
        // rectangle after decode.
        val inverted =
            BoundingBox(
                longitude_west_i = 210_000_000, // 21 (east-most) given as west
                latitude_south_i = 110_000_000, // 11 (north-most) given as south
                longitude_east_i = 200_000_000, // 20
                latitude_north_i = 100_000_000, // 10
            )
        val geofence = Waypoint(id = 1, bounding_box = inverted).toGeofence()
        assertEquals(GeofenceBox(south = 10.0, west = 20.0, north = 11.0, east = 21.0), geofence?.box)
    }

    @Test
    fun notifiesOnCrossingTruthTable() {
        assertFalse(Waypoint(id = 1).notifiesOnCrossing)
        assertTrue(Waypoint(id = 1, notify_on_enter = true).notifiesOnCrossing)
        assertTrue(Waypoint(id = 1, notify_on_exit = true).notifiesOnCrossing)
    }

    /** R2: geofence fields survive an unrelated edit and a proto encode/decode round-trip. */
    @Test
    fun geofenceFieldsSurviveEditAndRoundTrip() {
        val original =
            Waypoint(
                id = 7,
                latitude_i = 100_000_000,
                longitude_i = 200_000_000,
                name = "old",
                geofence_radius = 500,
                bounding_box = box,
                notify_on_enter = true,
                notify_on_exit = true,
                notify_favorites_only = true,
            )
        val edited = original.copy(name = "new")
        assertEquals(500, edited.geofence_radius)
        assertEquals(box, edited.bounding_box)
        assertTrue(edited.notify_on_enter)
        assertTrue(edited.notify_on_exit)
        assertTrue(edited.notify_favorites_only)

        val roundTripped = Waypoint.ADAPTER.decode(Waypoint.ADAPTER.encode(edited))
        assertEquals(edited, roundTripped)
    }

    private fun assertNotNullCircleAt(circle: GeofenceCircle?, lat: Double, lon: Double, radius: Int) {
        assertEquals(GeofenceCircle(centerLat = lat, centerLon = lon, radiusMeters = radius), circle)
    }
}
