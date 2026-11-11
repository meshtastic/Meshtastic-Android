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
package org.meshtastic.feature.xr

import org.meshtastic.feature.map.terrain.spatial.SpatialCircle
import org.meshtastic.feature.map.terrain.spatial.SpatialHeightGrid
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class TerrainCacheTest {
    private val here = SpatialCircle(47.6, -122.3, 5_000.0)
    private val grid = SpatialHeightGrid(2, 2, FloatArray(4), BooleanArray(4) { true }, 0f, 0f)

    @Test
    fun `a ground is kept for its own circle only`() {
        val cache = TerrainCache()
        cache.keep(here, grid)

        assertSame(grid, cache.grid(here))
        assertNull(cache.grid(SpatialCircle(47.6, -122.3, 2_500.0)))
        assertNull(TerrainCache().grid(here))
    }

    @Test
    fun `an image is kept for its circle and layer and the newer one replaces it`() {
        val cache = TerrainCache()
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        cache.keep(here, "esri", first)

        assertSame(first, cache.image(here, "esri"))
        assertNull(cache.image(here, "osm"))
        assertNull(cache.image(SpatialCircle(47.6, -122.3, 2_500.0), "esri"))

        cache.keep(here, "osm", second)
        assertNull(cache.image(here, "esri"))
        assertSame(second, cache.image(here, "osm"))
    }
}
