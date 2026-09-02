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

import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainTileMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainDownloadEstimateTest {

    private val tinyBounds = GeoBounds(south = 47.6, west = -122.4, north = 47.61, east = -122.39)

    @Test
    fun `matches a manual sum of tilesAt across the global zoom range only`() {
        val maxZoom = MapterhornEndpoints.GLOBAL_MAX_ZOOM
        val expected = (0..maxZoom).sumOf { zoom -> TerrainTileMath.tilesAt(zoom, tinyBounds).size.toLong() }

        assertEquals(expected, estimateTerrainTiles(tinyBounds, maxZoom))
    }

    @Test
    fun `a maxZoom at or below the global ceiling never counts a regional tier`() {
        val globalOnly = estimateTerrainTiles(tinyBounds, MapterhornEndpoints.GLOBAL_MAX_ZOOM)
        val deeper = estimateTerrainTiles(tinyBounds, MapterhornEndpoints.GLOBAL_MAX_ZOOM + 1)

        // The regional tier only exists past the global ceiling — one more zoom level must add regional tiles too.
        assertTrue(deeper > globalOnly)
    }

    @Test
    fun `a huge box that never fits a single regional archive gets no regional tier`() {
        // Spans more than one z6 tile, so MapterhornEndpoints.regionalUrlFor returns null.
        val huge = GeoBounds(south = -60.0, west = -170.0, north = 60.0, east = 170.0)

        val atGlobalCeiling = estimateTerrainTiles(huge, MapterhornEndpoints.GLOBAL_MAX_ZOOM)
        val past = estimateTerrainTiles(huge, MapterhornEndpoints.GLOBAL_MAX_ZOOM + 1)

        // Past the global ceiling with no regional archive, nothing further is fetched — the count doesn't grow.
        assertEquals(atGlobalCeiling, past)
    }

    @Test
    fun `zero or negative maxZoom counts nothing`() {
        assertEquals(0L, estimateTerrainTiles(tinyBounds, -1))
    }
}
