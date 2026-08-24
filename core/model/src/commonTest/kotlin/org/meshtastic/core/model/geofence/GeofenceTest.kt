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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeofenceTest {

    // Circle centered at (10, 20) with a 1 km radius.
    private val circle = GeofenceCircle(centerLat = 10.0, centerLon = 20.0, radiusMeters = 1000)

    // WSEN box: south=10, west=20, north=11, east=21.
    private val box = GeofenceBox(south = 10.0, west = 20.0, north = 11.0, east = 21.0)

    @Test
    fun circleContainsCenterAndNearby() {
        assertTrue(circle.contains(10.0, 20.0)) // distance 0
        assertTrue(circle.contains(10.005, 20.0)) // ~555 m north, inside 1 km
    }

    @Test
    fun circleExcludesFarPoint() {
        assertFalse(circle.contains(10.05, 20.0)) // ~5.5 km north, outside 1 km
    }

    @Test
    fun boxBoundsAreInclusive() {
        assertTrue(box.contains(10.0, 20.0)) // SW corner
        assertTrue(box.contains(11.0, 21.0)) // NE corner
        assertTrue(box.contains(10.5, 20.5)) // interior
        assertTrue(box.contains(10.0, 20.5)) // on the south edge
    }

    @Test
    fun boxExcludesOutsidePoints() {
        assertFalse(box.contains(9.999, 20.5)) // just south
        assertFalse(box.contains(11.001, 20.5)) // just north
        assertFalse(box.contains(10.5, 21.001)) // just east
        assertFalse(box.contains(10.5, 19.999)) // just west
    }

    @Test
    fun geofenceUsesOrSemantics() {
        val circleOnly = Geofence(circle = circle, box = null)
        val boxOnly = Geofence(circle = null, box = box)
        val both = Geofence(circle = circle, box = box)
        val neither = Geofence(circle = null, box = null)

        // A point inside the box but far from the circle counts via the box.
        assertTrue(boxOnly.contains(10.9, 20.9))
        assertTrue(both.contains(10.9, 20.9))
        assertFalse(circleOnly.contains(10.9, 20.9)) // ~140 km from circle center

        // A point inside the circle but outside the box counts via the circle.
        assertTrue(circleOnly.contains(9.998, 20.0))
        assertTrue(both.contains(9.998, 20.0))
        assertFalse(boxOnly.contains(9.998, 20.0)) // south of the box

        // Empty geofence never contains anything.
        assertFalse(neither.contains(10.0, 20.0))
    }
}
