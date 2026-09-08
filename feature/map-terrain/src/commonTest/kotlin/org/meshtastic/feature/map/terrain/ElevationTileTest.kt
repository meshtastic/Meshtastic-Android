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
package org.meshtastic.feature.map.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ElevationTileTest {

    @Test
    fun `zero RGB decodes to the Terrarium offset's negative`() {
        // R=0,G=0,B=0 -> 0 - 32768 = -32768, the encoding's own floor.
        assertEquals(-32768f, terrariumElevationMeters(red = 0, green = 0, blue = 0))
    }

    @Test
    fun `mid-gray-ish RGB decodes to sea level - per the spec's own worked example`() {
        // Tilezen/Joerd's spec worked example for ~0m: R=128, G=0, B=0 -> 128*256 - 32768 = 0.
        assertEquals(0f, terrariumElevationMeters(red = 128, green = 0, blue = 0))
    }

    @Test
    fun `green and blue channels add sub-256m and sub-1m precision`() {
        // R=128 (32768 raw), G=10 adds 10m, B=128 adds 0.5m -> 32768+10+0.5-32768 = 10.5
        assertEquals(10.5f, terrariumElevationMeters(red = 128, green = 10, blue = 128))
    }

    @Test
    fun `elevationAt reads row-major with top-left origin`() {
        val tile = ElevationTile(width = 2, height = 2, elevations = floatArrayOf(1f, 2f, 3f, 4f))
        assertEquals(1f, tile.elevationAt(0, 0))
        assertEquals(2f, tile.elevationAt(1, 0))
        assertEquals(3f, tile.elevationAt(0, 1))
        assertEquals(4f, tile.elevationAt(1, 1))
    }

    @Test
    fun `elevationAt clamps out-of-bounds coordinates to the tile's own edge`() {
        val tile = ElevationTile(width = 2, height = 2, elevations = floatArrayOf(1f, 2f, 3f, 4f))
        assertEquals(1f, tile.elevationAt(-5, -5))
        assertEquals(4f, tile.elevationAt(50, 50))
    }

    @Test
    fun `a mismatched elevations array size is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            ElevationTile(width = 2, height = 2, elevations = floatArrayOf(1f))
        }
    }

    @Test
    fun `a zero-dimension grid is rejected at construction rather than crashing elevationAt later`() {
        // width=0 (or height=0) passes the size == width*height check trivially (0 == 0), but would later throw
        // from elevationAt's coerceIn(0, width - 1) -> coerceIn(0, -1) on first use. Reject it up front instead.
        assertFailsWith<IllegalArgumentException> { ElevationTile(width = 0, height = 2, elevations = floatArrayOf()) }
        assertFailsWith<IllegalArgumentException> { ElevationTile(width = 2, height = 0, elevations = floatArrayOf()) }
    }

    @Test
    fun `dimensions whose product overflows Int are rejected rather than matching an empty array`() {
        // 65536 * 65536 wraps to 0 in Int, which would satisfy `size == width * height` for an empty array.
        assertFailsWith<IllegalArgumentException> {
            ElevationTile(width = 65536, height = 65536, elevations = floatArrayOf())
        }
    }
}
