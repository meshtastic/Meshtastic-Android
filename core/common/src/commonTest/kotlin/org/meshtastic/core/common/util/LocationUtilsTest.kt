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
package org.meshtastic.core.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationUtilsTest {

    @Test
    fun testGpsFormat() {
        val formatted = GPSFormat.toDec(45.123456, -93.654321)
        assertEquals("45.12345, -93.65432", formatted)
    }

    @Test
    fun testLatLongToMeter() {
        // Distance from (0,0) to (0,1) at equator should be approx 111.3km
        val distance = latLongToMeter(0.0, 0.0, 0.0, 1.0)
        assertTrue(distance > 111000 && distance < 112000, "Distance was $distance")

        // Distance from (45, -93) to (45, -92)
        val distance2 = latLongToMeter(45.0, -93.0, 45.0, -92.0)
        assertTrue(distance2 > 78000 && distance2 < 79000, "Distance was $distance2")
    }

    @Test
    fun testBearing() {
        // North
        assertEquals(0.0, bearing(0.0, 0.0, 1.0, 0.0), 0.1)
        // East
        assertEquals(90.0, bearing(0.0, 0.0, 0.0, 1.0), 0.1)
        // South
        assertEquals(180.0, bearing(0.0, 0.0, -1.0, 0.0), 0.1)
        // West
        assertEquals(270.0, bearing(0.0, 0.0, 0.0, -1.0), 0.1)
    }
}
