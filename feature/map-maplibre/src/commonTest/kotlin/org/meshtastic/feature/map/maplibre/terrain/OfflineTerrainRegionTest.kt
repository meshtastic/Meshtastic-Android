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
package org.meshtastic.feature.map.maplibre.terrain

import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineTerrainRegionTest {

    private fun region(south: Double, west: Double, north: Double, east: Double) = OfflineTerrainRegion(
        south = south,
        west = west,
        north = north,
        east = east,
        maxZoom = 12,
        hasRegionalDetail = false,
        tileCount = 1,
        byteSize = 1,
    )

    @Test
    fun `bounds reflects the manifest's own flat fields`() {
        val region = region(south = 1.0, west = 2.0, north = 3.0, east = 4.0)
        assertEquals(GeoBounds(south = 1.0, west = 2.0, north = 3.0, east = 4.0), region.bounds)
    }

    @Test
    fun `toBoundingBox and toGeoBounds are inverses`() {
        val bounds = GeoBounds(south = -10.0, west = -20.0, north = 30.0, east = 40.0)
        assertEquals(bounds, bounds.toBoundingBox().toGeoBounds())
    }

    @Test
    fun `intersects is true for overlapping boxes and false for disjoint ones`() {
        val region = region(south = 0.0, west = 0.0, north = 10.0, east = 10.0)

        val overlapping =
            BoundingBox(
                southwest = Position(longitude = 5.0, latitude = 5.0),
                northeast = Position(longitude = 15.0, latitude = 15.0),
            )
        assertTrue(region.intersects(overlapping))

        val disjoint =
            BoundingBox(
                southwest = Position(longitude = 20.0, latitude = 20.0),
                northeast = Position(longitude = 30.0, latitude = 30.0),
            )
        assertFalse(region.intersects(disjoint))
    }

    @Test
    fun `intersects is true for a viewport entirely inside the region`() {
        val region = region(south = 0.0, west = 0.0, north = 10.0, east = 10.0)
        val inside =
            BoundingBox(
                southwest = Position(longitude = 4.0, latitude = 4.0),
                northeast = Position(longitude = 6.0, latitude = 6.0),
            )
        assertTrue(region.intersects(inside))
    }
}
