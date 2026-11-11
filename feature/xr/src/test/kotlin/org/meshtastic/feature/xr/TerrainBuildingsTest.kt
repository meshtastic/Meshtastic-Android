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
import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.map.terrain.spatial.SpatialFrame
import org.meshtastic.feature.map.terrain.spatial.SpatialHeightGrid
import org.meshtastic.feature.map.terrain.spatial.SpatialModelTransform
import org.meshtastic.feature.map.terrain.spatial.SpatialTerrainGeometry
import org.meshtastic.feature.map.terrain.spatial.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerrainBuildingsTest {
    /** Flat ground at sea level, about 2 km on a side. */
    private val geometry: SpatialTerrainGeometry
        get() {
            val frame = SpatialFrame(GeoBounds(south = 47.60, west = -122.34, north = 47.62, east = -122.31))
            val grid = SpatialHeightGrid(3, 3, FloatArray(9), BooleanArray(9) { true }, 0f, 0f)
            return SpatialTerrainGeometry(grid, SpatialModelTransform(frame, 0.0, 0.0, verticalExaggeration = 6.0))
        }

    private fun square(south: Double, west: Double, side: Double) = listOf(
        LatLon(south, west),
        LatLon(south, west + side),
        LatLon(south + side, west + side),
        LatLon(south + side, west),
    )

    @Test
    fun `an L shaped roof is filled without covering its notch`() {
        val outline = listOf(Vec2(0f, 0f), Vec2(2f, 0f), Vec2(2f, 1f), Vec2(1f, 1f), Vec2(1f, 2f), Vec2(0f, 2f))

        val roof = TerrainBuildings.triangulate(outline)

        assertEquals(4 * 3, roof.size)
        var area = 0f
        for (t in roof.indices step 3) {
            val a = outline[roof[t]]
            val b = outline[roof[t + 1]]
            val c = outline[roof[t + 2]]
            val twice = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
            assertTrue(twice > 0, "each triangle keeps the outline's order")
            area += twice / 2
        }
        assertEquals(3f, area, 1e-5f)
    }

    @Test
    fun `a ring's closing corner is dropped`() {
        val ring = listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 0f), Vec2(1f, 1f), Vec2(0f, 0f))

        assertEquals(listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 1f)), TerrainBuildings.outline(ring))
    }

    @Test
    fun `an outline gives way to the parts inside it but not to one in its courtyard`() {
        val whole = BuildingFootprint(square(47.610, -122.330, 0.001), heightMeters = 180.0)
        val part = BuildingFootprint(square(47.6104, -122.3296, 0.0002), heightMeters = 180.0, isPart = true)
        val elsewhere = BuildingFootprint(square(47.615, -122.320, 0.001), heightMeters = 10.0)

        assertEquals(listOf(part, elsewhere), TerrainBuildings.withoutOutlinesOfParts(listOf(whole, part, elsewhere)))

        val courtyard = whole.copy(holes = listOf(square(47.6103, -122.3297, 0.0004)))
        assertEquals(listOf(courtyard, part), TerrainBuildings.withoutOutlinesOfParts(listOf(courtyard, part)))
    }

    @Test
    fun `a building is four walls facing out and a roof facing up at its height`() {
        val geometry = geometry
        val footprint = BuildingFootprint(square(47.610, -122.326, 0.0003), heightMeters = 30.0)

        val mesh = assertNotNull(TerrainBuildings.meshData(listOf(footprint), geometry))

        assertEquals(4 + 4 * 4, mesh.vertexCount)
        assertEquals((2 + 4 * 2) * 3, mesh.indices.size)
        val transform = geometry.transform
        assertEquals(6.0, transform.verticalExaggeration)
        val top = (30.0 * TerrainBuildings.MAX_EXAGGERATION * transform.scale).toFloat()
        assertEquals(top, mesh.positions.filterIndexed { i, _ -> i % 3 == 1 }.max(), 1e-6f)
        val middle = geometry.surfacePosition(LatLon(47.61015, -122.32585))
        for (t in mesh.indices.indices step 3) {
            val (a, b, c) = List(3) { k -> List(3) { mesh.positions[mesh.indices[t + k] * 3 + it] } }
            val u = List(3) { b[it] - a[it] }
            val v = List(3) { c[it] - a[it] }
            val normal = listOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            val stored = List(3) { mesh.normals[mesh.indices[t] * 3 + it] }
            val facing = normal[0] * stored[0] + normal[1] * stored[1] + normal[2] * stored[2]
            assertTrue(facing > 0, "triangle ${t / 3} winds the way its normal points")
            // A wall's normal points away from the middle of the building.
            val outward = stored[0] * (a[0] - middle.x) + stored[2] * (a[2] - middle.z)
            assertTrue(abs(stored[1]) == 1f || outward > 0, "triangle ${t / 3} faces out")
        }
    }

    /** The area of the triangles facing straight up, in model units. */
    private fun roofArea(mesh: TerrainMeshData): Float {
        var area = 0f
        for (t in mesh.indices.indices step 3) {
            if (mesh.normals[mesh.indices[t] * 3 + 1] != 1f) continue
            val (a, b, c) = List(3) { k -> List(3) { mesh.positions[mesh.indices[t + k] * 3 + it] } }
            area += abs((b[0] - a[0]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[0] - a[0])) / 2
        }
        return area
    }

    @Test
    fun `a courtyard is left open in the roof and walled round`() {
        val geometry = geometry
        val solid = BuildingFootprint(square(47.610, -122.326, 0.0006), heightMeters = 30.0)
        val ring = solid.copy(holes = listOf(square(47.6102, -122.3258, 0.0002)))

        val filled = assertNotNull(TerrainBuildings.meshData(listOf(solid), geometry))
        val open = assertNotNull(TerrainBuildings.meshData(listOf(ring), geometry))

        // Round the outline and the courtyard, joined by a cut that repeats a corner of each.
        assertEquals(4 + 4 + 2 + (4 + 4) * 4, open.vertexCount)
        assertEquals((8 + (4 + 4) * 2) * 3, open.indices.size)
        assertEquals(roofArea(filled) * 8 / 9, roofArea(open), roofArea(filled) * 1e-3f)
        // The courtyard's walls face its middle, the outer walls away from it.
        val middle = geometry.surfacePosition(LatLon(47.6103, -122.3257))
        val inward =
            (0 until open.vertexCount).count { v ->
                val out = List(3) { open.normals[v * 3 + it] }
                val from = listOf(open.positions[v * 3] - middle.x, 0f, open.positions[v * 3 + 2] - middle.z)
                out[1] == 0f && out[0] * from[0] + out[2] * from[2] < 0
            }
        assertEquals(4 * 4, inward)
    }

    @Test
    fun `a building past the disk is left out and the largest are kept within the budget`() {
        val geometry = geometry
        val corner = BuildingFootprint(square(47.6001, -122.3399, 0.0003), heightMeters = 10.0)
        val small = BuildingFootprint(square(47.610, -122.326, 0.0002), heightMeters = 10.0)
        val large = BuildingFootprint(square(47.612, -122.324, 0.0006), heightMeters = 10.0)

        assertNull(TerrainBuildings.meshData(listOf(corner), geometry))

        val one = assertNotNull(TerrainBuildings.meshData(listOf(small, large), geometry, triangleBudget = 10))
        assertEquals(20, one.vertexCount)
        val widest = one.positions.filterIndexed { i, _ -> i % 3 == 0 }
        val width = geometry.transform.frame.metersPerDegreeLongitude * 0.0006 * geometry.transform.scale
        assertEquals(width.toFloat(), widest.max() - widest.min(), 1e-5f)
    }
}
