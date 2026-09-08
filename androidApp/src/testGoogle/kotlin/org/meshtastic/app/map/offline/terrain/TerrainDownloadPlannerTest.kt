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
package org.meshtastic.app.map.offline.terrain

import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainTileMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainDownloadPlannerTest {

    @Test
    fun `a tiny region gets full regional depth when the tile budget easily allows it`() {
        val bounds = GeoBounds(south = 37.774, west = -122.420, north = 37.776, east = -122.418)

        val zoom = TerrainDownloadPlanner.maxZoomFitting(bounds, maxTiles = 3_000)

        assertEquals(MapterhornEndpoints.REGIONAL_MAX_ZOOM, zoom)
    }

    @Test
    fun `a region whose tile count blows the budget even at global-only falls back to global-only`() {
        // A 1x1 degree box: even the always-downloaded 0-12 global tier alone is already hundreds of tiles.
        val bounds = GeoBounds(south = 37.0, west = -123.0, north = 38.0, east = -122.0)

        val zoom = TerrainDownloadPlanner.maxZoomFitting(bounds, maxTiles = 50)

        assertEquals(MapterhornEndpoints.GLOBAL_MAX_ZOOM, zoom)
    }

    @Test
    fun `whenever regional depth is granted, its own tile count actually fits the budget`() {
        val bounds = GeoBounds(south = 37.7, west = -122.5, north = 37.8, east = -122.4)
        val maxTiles = 200

        val zoom = TerrainDownloadPlanner.maxZoomFitting(bounds, maxTiles)

        if (zoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM) {
            // Recomputed independently via the same public tile math the planner is built on, rather than trusting
            // its own internal count — this is what actually verifies the *selection*, not just its own arithmetic.
            val globalCount =
                (0..MapterhornEndpoints.GLOBAL_MAX_ZOOM).sumOf { z -> TerrainTileMath.tilesAt(z, bounds).size }
            val regionalCount =
                (MapterhornEndpoints.REGIONAL_MIN_ZOOM..zoom).sumOf { z -> TerrainTileMath.tilesAt(z, bounds).size }
            assertTrue(globalCount + regionalCount <= maxTiles)
        }
    }

    @Test
    fun `raising the tile budget never returns a shallower zoom for the same region`() {
        val bounds = GeoBounds(south = 37.7, west = -122.5, north = 37.8, east = -122.4)

        val shallow = TerrainDownloadPlanner.maxZoomFitting(bounds, maxTiles = 100)
        val deep = TerrainDownloadPlanner.maxZoomFitting(bounds, maxTiles = 10_000)

        assertTrue(deep >= shallow)
    }
}
