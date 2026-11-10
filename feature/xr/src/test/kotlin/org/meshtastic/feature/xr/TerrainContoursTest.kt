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

import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.spatial.SpatialFrame
import org.meshtastic.feature.map.terrain.spatial.SpatialHeightGrid
import org.meshtastic.feature.map.terrain.spatial.SpatialModelTransform
import org.meshtastic.feature.map.terrain.spatial.SpatialTerrainGeometry
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerrainContoursTest {
    /** Ground about 2 km on a side, rising evenly from sea level in the west to [top] meters in the east. */
    private fun ramp(top: Float): SpatialTerrainGeometry {
        val size = 97
        val frame = SpatialFrame(GeoBounds(south = 47.60, west = -122.34, north = 47.62, east = -122.31))
        val heights = FloatArray(size * size) { top * (it % size) / (size - 1) }
        val grid = SpatialHeightGrid(size, size, heights, BooleanArray(size * size) { true }, 0f, top)
        return SpatialTerrainGeometry(grid, SpatialModelTransform(frame, 0.0, top.toDouble()))
    }

    private class Ribbon(val altitude: Double, val width: Float, val reach: Float, val facing: Float)

    /** Each quad's height in meters at its middle, its width, how far out it reaches and how squarely it faces up. */
    private fun ribbons(mesh: TerrainMeshData, geometry: SpatialTerrainGeometry): List<Ribbon> =
        List(mesh.vertexCount / 4) { quad ->
            fun corner(k: Int) = List(3) { mesh.positions[(quad * 4 + k) * 3 + it] }
            val corners = List(4) { corner(it) }
            val ny = mesh.normals[quad * 4 * 3 + 1]
            val middle = corners.sumOf { it[1].toDouble() }.toFloat() / 4 - ny * TerrainContours.LIFT
            val across = List(3) { corners[3][it] - corners[0][it] }
            val u = List(3) { corners[1][it] - corners[0][it] }
            val v = List(3) { corners[2][it] - corners[0][it] }
            val normal = listOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            Ribbon(
                altitude = geometry.transform.altitude(middle),
                width = sqrt(across.sumOf { (it * it).toDouble() }).toFloat(),
                reach = corners.maxOf { hypot(it[0], it[2]) },
                facing = List(3) { normal[it] * mesh.normals[quad * 4 * 3 + it] }.sum(),
            )
        }

    @Test
    fun `the spacing is the finest that keeps the relief to forty lines`() {
        assertEquals(10.0, TerrainContours.spacing(400.0, ContourUnits.Metric))
        assertEquals(20.0, TerrainContours.spacing(401.0, ContourUnits.Metric))
        assertEquals(1000.0, TerrainContours.spacing(100_000.0, ContourUnits.Metric))
        assertEquals(20 * 0.3048, TerrainContours.spacing(100.0, ContourUnits.Imperial), 1e-9)
    }

    @Test
    fun `lines lie on the ground at round heights with every fifth one wider`() {
        val geometry = ramp(400f)

        val mesh = assertNotNull(TerrainContours.meshData(geometry, ContourUnits.Metric, onImage = false))

        val ribbons = ribbons(mesh, geometry)
        val levels = ribbons.map { (it.altitude / 10).roundToInt() }.toSet()
        assertTrue(levels.size >= 30, "a line for most of the forty levels, found ${levels.size}")
        assertTrue(levels.all { it in 1..40 })
        for (ribbon in ribbons) {
            val level = (ribbon.altitude / 10).roundToInt()
            assertEquals(level * 10.0, ribbon.altitude, 0.05)
            val half = if (level % 5 == 0) TerrainContours.INDEX_HALF_WIDTH else TerrainContours.MINOR_HALF_WIDTH
            assertEquals(2 * half, ribbon.width, 1e-5f)
            assertTrue(ribbon.reach <= geometry.transform.diskRadius + 2 * half, "inside the disk")
            assertTrue(ribbon.facing > 0, "winds the way its normal points")
        }
    }

    @Test
    fun `the spacing widens until the lines fit the budget`() {
        val geometry = ramp(400f)
        val full = assertNotNull(TerrainContours.meshData(geometry, ContourUnits.Metric, onImage = true))
        val budget = full.indices.size / 3 / 3

        val fewer = assertNotNull(TerrainContours.meshData(geometry, ContourUnits.Metric, onImage = true, budget))

        assertTrue(fewer.indices.size / 3 <= budget)
        val levels = ribbons(fewer, geometry).map { it.altitude }
        assertTrue(levels.all { abs(it / 50 - (it / 50).roundToInt()) < 1e-3 }, "every line is on a 50 m level")
        assertTrue(fewer.colors[0] > 0.65f, "light over an image")
    }

    @Test
    fun `flat ground has no lines`() {
        assertNull(TerrainContours.meshData(ramp(0f), ContourUnits.Metric, onImage = false))
    }
}
