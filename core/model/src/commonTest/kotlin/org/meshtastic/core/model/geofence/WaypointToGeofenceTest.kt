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
        BoundingBox.Builder()
            .also { wb ->
                wb.longitude_west_i = 200_000_000
                wb.latitude_south_i = 100_000_000
                wb.longitude_east_i = 210_000_000
                wb.latitude_north_i = 110_000_000
            }
            .build()

    @Test
    fun radiusOnlyDecodesCircleNoBox() {
        val wp =
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 1
                    wb.latitude_i = 100_000_000
                    wb.longitude_i = 200_000_000
                    wb.geofence_radius = 500
                }
                .build()
        val geofence = wp.toGeofence()
        assertNotNullCircleAt(geofence?.circle, 10.0, 20.0, 500)
        assertNull(geofence?.box)
    }

    @Test
    fun boxOnlyDecodesBoxNoCircle() {
        val wp =
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 1
                    wb.bounding_box = box
                }
                .build()
        val geofence = wp.toGeofence()
        assertNull(geofence?.circle)
        assertEquals(GeofenceBox(south = 10.0, west = 20.0, north = 11.0, east = 21.0), geofence?.box)
    }

    @Test
    fun neitherShapeDecodesToNull() {
        assertNull(Waypoint.Builder().also { wb -> wb.id = 1 }.build().toGeofence())
        assertNull(
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 1
                    wb.geofence_radius = 0
                }
                .build()
                .toGeofence(),
        )
    }

    @Test
    fun bothShapesDecode() {
        val wp =
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 1
                    wb.latitude_i = 100_000_000
                    wb.longitude_i = 200_000_000
                    wb.geofence_radius = 500
                    wb.bounding_box = box
                }
                .build()
        val geofence = wp.toGeofence()
        assertNotNullCircleAt(geofence?.circle, 10.0, 20.0, 500)
        assertEquals(GeofenceBox(south = 10.0, west = 20.0, north = 11.0, east = 21.0), geofence?.box)
    }

    @Test
    fun invertedBoundingBoxIsNormalized() {
        // A box whose corners arrive transposed (south>north, west>east) should still describe the intended
        // rectangle after decode.
        val inverted =
            BoundingBox.Builder()
                .also { wb ->
                    wb.longitude_west_i = 210_000_000 // 21 (east-most) given as west
                    wb.latitude_south_i = 110_000_000 // 11 (north-most) given as south
                    wb.longitude_east_i = 200_000_000 // 20
                    wb.latitude_north_i = 100_000_000 // 10
                }
                .build()
        val geofence =
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 1
                    wb.bounding_box = inverted
                }
                .build()
                .toGeofence()
        assertEquals(GeofenceBox(south = 10.0, west = 20.0, north = 11.0, east = 21.0), geofence?.box)
    }

    @Test
    fun notifiesOnCrossingTruthTable() {
        assertFalse(Waypoint.Builder().also { wb -> wb.id = 1 }.build().notifiesOnCrossing)
        assertTrue(
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 1
                    wb.notify_on_enter = true
                }
                .build()
                .notifiesOnCrossing,
        )
        assertTrue(
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 1
                    wb.notify_on_exit = true
                }
                .build()
                .notifiesOnCrossing,
        )
    }

    /** R2: geofence fields survive an unrelated edit and a proto encode/decode round-trip. */
    @Test
    fun geofenceFieldsSurviveEditAndRoundTrip() {
        val original =
            Waypoint.Builder()
                .also { wb ->
                    wb.id = 7
                    wb.latitude_i = 100_000_000
                    wb.longitude_i = 200_000_000
                    wb.name = "old"
                    wb.geofence_radius = 500
                    wb.bounding_box = box
                    wb.notify_on_enter = true
                    wb.notify_on_exit = true
                    wb.notify_favorites_only = true
                }
                .build()
        val edited = original.newBuilder().also { wb -> wb.name = "new" }.build()
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
