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
package org.meshtastic.feature.map.terrain.spatial

import kotlinx.coroutines.test.runTest
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainTileMath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SpatialHeightGridSamplingTest {
    /** Ground that climbs [SLOPE] meters per tile going east, the same across every tile edge. */
    private class RampSource(private val edge: Int) : ElevationTileSource {
        override suspend fun elevationTile(z: Int, x: Int, y: Int) =
            ElevationTile(edge, edge, FloatArray(edge * edge) { (x + (it % edge + 0.5f) / edge) * SLOPE })

        override suspend fun coverage(z: Int, x: Int, y: Int): GeoBounds {
            val northwest = TerrainTileMath.lonLatAt(z, x.toDouble(), y.toDouble())
            val southeast = TerrainTileMath.lonLatAt(z, x + 1.0, y + 1.0)
            return GeoBounds(southeast.latitude, northwest.longitude, northwest.latitude, southeast.longitude)
        }

        override suspend fun finestDataZoom() = 12
    }

    private val box = GeoBounds(south = 46.5, west = -124.0, north = 48.5, east = -120.0)

    private suspend fun worstError(edge: Int): Float {
        val frame = SpatialFrame(box)
        val plan = SpatialHeightGrid.plan(frame, maxTriangles = 4_000, dataMaxZoom = 12)
        val grid = SpatialHeightGrid.sample(frame, plan, RampSource(edge))
        val tilesAcross = 1 shl plan.samplingZoom
        var worst = 0f
        for (i in 0 until plan.columns) {
            val longitude = box.west + (box.east - box.west) * i / (plan.columns - 1)
            val expected = (TerrainTileMath.worldFraction(frame.centerLatitude, longitude).first * tilesAcross) * SLOPE
            worst = maxOf(worst, abs(grid.heights[i] - expected.toFloat()))
        }
        return worst
    }

    @Test
    fun `a ramp is sampled without steps at tile edges from 256 pixel tiles`() = runTest {
        assertTrue(worstError(256) < TOLERANCE_METERS)
    }

    @Test
    fun `a ramp is sampled without steps at tile edges from 512 pixel tiles`() = runTest {
        assertTrue(worstError(512) < TOLERANCE_METERS)
    }

    private companion object {
        const val SLOPE = 100f

        // Under a half texel of a 512 pixel tile, 0.1 m here, so a half texel of drift fails.
        const val TOLERANCE_METERS = 0.05f
    }
}
