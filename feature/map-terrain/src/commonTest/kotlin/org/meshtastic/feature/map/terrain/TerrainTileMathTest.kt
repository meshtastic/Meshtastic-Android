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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerrainTileMathTest {

    @Test
    fun `zoom 0 is a single tile covering everywhere`() {
        assertEquals(TileIndex(0, 0, 0), TerrainTileMath.tileAt(0, latitude = 10.0, longitude = 10.0))
        assertEquals(TileIndex(0, 0, 0), TerrainTileMath.tileAt(0, latitude = -70.0, longitude = 170.0))
    }

    @Test
    fun `zoom 1 splits the world into hemispheric quadrants`() {
        assertEquals(TileIndex(1, 0, 0), TerrainTileMath.tileAt(1, latitude = 45.0, longitude = -170.0)) // NW
        assertEquals(TileIndex(1, 1, 1), TerrainTileMath.tileAt(1, latitude = -45.0, longitude = 10.0)) // SE
    }

    @Test
    fun `tilesAt covers a bbox's own corners inclusively`() {
        val bounds = GeoBounds(south = -1.0, west = -1.0, north = 1.0, east = 1.0)
        val tiles = TerrainTileMath.tilesAt(zoom = 2, bounds)
        assertTrue(tiles.contains(TerrainTileMath.tileAt(2, bounds.north, bounds.west)))
        assertTrue(tiles.contains(TerrainTileMath.tileAt(2, bounds.south, bounds.east)))
    }

    @Test
    fun `fitsInSingleTile is true for a tiny box and false for a global one`() {
        val tiny = GeoBounds(south = 47.6, west = -122.4, north = 47.61, east = -122.39)
        assertTrue(TerrainTileMath.fitsInSingleTile(zoom = 6, tiny))

        val global = GeoBounds(south = -60.0, west = -170.0, north = 60.0, east = 170.0)
        assertFalse(TerrainTileMath.fitsInSingleTile(zoom = 6, global))
    }

    @Test
    fun `lonLatAt at a tile's own corner round-trips through tileAt`() {
        val zoom = 12
        val tile = TerrainTileMath.tileAt(zoom, latitude = 47.6, longitude = -122.3)

        // The tile's own top-left corner (localX=0, localY=0) must land back inside that same tile.
        val corner = TerrainTileMath.lonLatAt(tile, localX = 0f, localY = 0f)
        assertEquals(tile, TerrainTileMath.tileAt(zoom, corner.latitude, corner.longitude))
    }

    @Test
    fun `lonLatAt at the tile center is roughly the tile's own midpoint`() {
        val zoom = 8
        val tile = TileIndex(zoom, x = 41, y = 91)

        val center = TerrainTileMath.lonLatAt(tile, localX = 0.5f, localY = 0.5f)
        assertEquals(tile, TerrainTileMath.tileAt(zoom, center.latitude, center.longitude))
    }

    @Test
    fun `lonLatAt zoom 0 spans the whole world`() {
        val topLeft = TerrainTileMath.lonLatAt(TileIndex(0, 0, 0), localX = 0f, localY = 0f)
        assertTrue(abs(topLeft.longitude - -180.0) < 1e-6)

        val bottomRight = TerrainTileMath.lonLatAt(TileIndex(0, 0, 0), localX = 1f, localY = 1f)
        assertTrue(abs(bottomRight.longitude - 180.0) < 1e-6)
    }
}
