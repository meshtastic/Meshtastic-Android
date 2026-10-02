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

class TerrainTileCountTest {

    private val tinySeattleBox = GeoBounds(south = 47.6, west = -122.4, north = 47.61, east = -122.39)
    private val continentalBox = GeoBounds(south = -60.0, west = -170.0, north = 60.0, east = 170.0)

    @Test
    fun `a download that stays global counts one tile per zoom for a box inside one tile`() {
        assertEquals(13L, terrainTileCount(tinySeattleBox, maxZoom = MapterhornEndpoints.GLOBAL_MAX_ZOOM))
    }

    @Test
    fun `a box inside one regional archive adds the regional zooms`() {
        // z13 and z14 cover 2 tiles apiece here, on top of the 13 global ones.
        assertEquals(17L, terrainTileCount(tinySeattleBox, maxZoom = 14))
    }

    @Test
    fun `a box with no regional archive never counts past the global tier`() {
        val globalOnly = terrainTileCount(continentalBox, maxZoom = MapterhornEndpoints.GLOBAL_MAX_ZOOM)

        assertEquals(8_869_485L, globalOnly)
        assertEquals(globalOnly, terrainTileCount(continentalBox, maxZoom = MapterhornEndpoints.REGIONAL_MAX_ZOOM))
    }

    @Test
    fun `a negative maxZoom counts nothing`() {
        assertEquals(0L, terrainTileCount(tinySeattleBox, maxZoom = -1))
    }

    @Test
    fun `maxZoom 0 counts the one tile that covers the world`() {
        assertEquals(1L, terrainTileCount(tinySeattleBox, maxZoom = 0))
    }
}
