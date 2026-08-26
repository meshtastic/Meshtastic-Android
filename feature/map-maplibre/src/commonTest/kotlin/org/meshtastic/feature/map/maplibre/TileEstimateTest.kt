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
package org.meshtastic.feature.map.maplibre

import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileEstimateTest {

    private fun box(west: Double, south: Double, east: Double, north: Double) =
        BoundingBox(west = west, south = south, east = east, north = north)

    @Test
    fun `whole world at zoom zero is a single tile`() {
        assertEquals(1L, box(-180.0, -85.0, 180.0, 85.0).tileCount(minZoom = 0, maxZoom = 0))
    }

    @Test
    fun `whole world at zoom one is four tiles`() {
        assertEquals(4L, box(-180.0, -85.0, 180.0, 85.0).tileCount(minZoom = 1, maxZoom = 1))
    }

    @Test
    fun `a range sums every level in it`() {
        val world = box(-180.0, -85.0, 180.0, 85.0)
        // 1 + 4 + 16 = 21
        assertEquals(21L, world.tileCount(minZoom = 0, maxZoom = 2))
    }

    @Test
    fun `an inverted range counts nothing rather than throwing`() {
        assertEquals(0L, box(-93.8, 41.5, -93.5, 41.7).tileCount(minZoom = 17, maxZoom = 16))
    }

    @Test
    fun `a small region stays small while a whole state does not`() {
        val neighbourhood = box(-93.66, 41.58, -93.60, 41.62)
        val state = box(-96.6, 40.4, -90.1, 43.5)

        val neighbourhoodTiles = neighbourhood.tileCount(minZoom = 14, maxZoom = 16)
        val stateTiles = state.tileCount(minZoom = 14, maxZoom = 16)

        // The point of showing an estimate: same zoom range, wildly different cost.
        assertTrue(neighbourhoodTiles < 200L, "neighbourhood was $neighbourhoodTiles tiles")
        assertTrue(stateTiles > 10_000L, "state was only $stateTiles tiles")
    }

    @Test
    fun `poles are clamped instead of running to infinity`() {
        val arctic = box(-180.0, 89.0, 180.0, 90.0)
        assertTrue(arctic.tileCount(minZoom = 3, maxZoom = 3) > 0L)
    }
}
