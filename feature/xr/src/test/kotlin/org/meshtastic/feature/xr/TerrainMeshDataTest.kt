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

import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.map.terrain.spatial.SpatialMapArea
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainMeshDataTest {

    @Test
    fun `vertex buffer is position and normal floats then four normalized color bytes`() {
        val data =
            TerrainMeshData(
                positions = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
                normals = floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f),
                colors = floatArrayOf(1f, 0.5f, 0f, 1f, 0f, 0f, 1f, 0.2f),
                indices = intArrayOf(0, 1, 0),
            )
        val buffer = data.vertexBuffer().order(ByteOrder.nativeOrder())

        assertEquals(2 * TerrainMeshData.BYTES_PER_VERTEX, buffer.limit())
        assertEquals(28, TerrainMeshData.BYTES_PER_VERTEX)
        assertEquals(listOf(1f, 2f, 3f), List(3) { buffer.getFloat(it * 4) })
        assertEquals(listOf(0f, 1f, 0f), List(3) { buffer.getFloat(12 + it * 4) })
        // SceneCore rejects float colors; the fourth attribute is RGBA as unsigned bytes.
        assertEquals(listOf(255, 128, 0, 255), List(4) { buffer.get(24 + it).toInt() and 0xFF })
        val second = TerrainMeshData.BYTES_PER_VERTEX
        assertEquals(listOf(0, 0, 255, 51), List(4) { buffer.get(second + 24 + it).toInt() and 0xFF })
    }

    @Test
    fun `a far node does not stretch the circle when there is no connected radio`() {
        val seattle =
            listOf(LatLon(47.60, -122.30), LatLon(47.61, -122.31), LatLon(47.62, -122.29)).mapIndexed { i, p ->
                SpatialNodeSnapshot(i.toLong(), p)
            }
        val tokyo = SpatialNodeSnapshot(9, LatLon(35.68, 139.69))

        val unanchored = SpatialMapArea.meshCircle(seattle + tokyo)
        val anchored = SpatialMapArea.meshCircle(withLocalAnchor(seattle + tokyo))

        assertTrue(checkNotNull(unanchored).radiusMeters > SpatialMapArea.MAX_RADIUS_METERS)
        assertTrue(checkNotNull(anchored).radiusMeters < SpatialMapArea.MAX_RADIUS_METERS)
    }

    @Test
    fun `a chip's name is black on a light node color and white on a dark one`() {
        assertTrue(ChipImage.isLight(Rgb(0.9f, 0.9f, 0.2f)))
        assertTrue(!ChipImage.isLight(Rgb(0.2f, 0.1f, 0.6f)))
    }

    @Test
    fun `a cut-out model masks the clear parts of its image`() {
        val mesh =
            TerrainMeshData(
                floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
                FloatArray(9),
                FloatArray(12),
                intArrayOf(0, 1, 2),
            )

        val cut = String(terrainGlb(mesh, ByteArray(4), "image/png", cutout = true), Charsets.ISO_8859_1)

        assertTrue("\"alphaMode\":\"MASK\"" in cut)
        assertTrue("alphaMode" !in String(terrainGlb(mesh, ByteArray(4), "image/png"), Charsets.ISO_8859_1))
    }

    @Test
    fun `a connected radio stays the anchor`() {
        val radio = SpatialNodeSnapshot(1, LatLon(47.6, -122.3), isConnectedRadio = true)
        val other = SpatialNodeSnapshot(2, LatLon(47.7, -122.4))

        assertEquals(listOf(radio, other), withLocalAnchor(listOf(radio, other)))
    }

    @Test
    fun `relief is stretched toward an eighth of the width and capped`() {
        // 1500 m of relief across 100 km would be 1.5% of the width; 0.12 needs 8x, which the cap holds to 6.
        assertEquals(6.0, reliefExaggeration(1_500.0, 100_000.0), 1e-9)
        // 2000 m across 50 km: 0.12 * 50 000 / 2000 = 3.
        assertEquals(3.0, reliefExaggeration(2_000.0, 50_000.0), 1e-9)
        // Steep ground close in keeps the floor, and flat ground has nothing to stretch.
        assertEquals(1.5, reliefExaggeration(1_000.0, 4_000.0), 1e-9)
        assertEquals(1.5, reliefExaggeration(0.0, 4_000.0), 1e-9)
    }

    @Test
    fun `a disk is closed and every index is in range`() {
        val disk = MeshAccumulator().apply { disk(0.5f, -0.01f, 0f, Rgb(0.1f, 0.1f, 0.1f), segments = 8) }.build()

        // Per segment: a top and a bottom triangle and a side quad.
        assertEquals(8 * 12, disk.indices.size)
        assertTrue(disk.indices.all { it in 0 until disk.vertexCount })
    }
}
