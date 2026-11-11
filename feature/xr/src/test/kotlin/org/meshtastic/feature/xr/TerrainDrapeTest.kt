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
import org.meshtastic.feature.map.terrain.TerrainTileMath
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainDrapeTest {
    private val seattle = GeoBounds(south = 47.5, west = -122.4, north = 47.7, east = -122.2)

    @Test
    fun `imagery plan reaches the target width and its tiles cover the box`() {
        val plan = ImageryPlan.of(seattle, maxZoom = 19, targetPixels = 2048, maxTiles = 400)

        assertTrue(plan.width >= 2048)
        assertTrue(plan.width < 4096)
        val northwest = TerrainTileMath.tileAt(plan.zoom, seattle.north, seattle.west)
        val southeast = TerrainTileMath.tileAt(plan.zoom, seattle.south, seattle.east)
        assertEquals(northwest.x..southeast.x, plan.columns)
        assertEquals(northwest.y..southeast.y, plan.rows)
    }

    @Test
    fun `imagery plan stops at the source's deepest zoom and at the tile budget`() {
        assertEquals(9, ImageryPlan.of(seattle, maxZoom = 9, targetPixels = 2048, maxTiles = 100).zoom)
        assertTrue(ImageryPlan.of(seattle, maxZoom = 19, targetPixels = 2048, maxTiles = 4).tileCount <= 4)
    }

    @Test
    fun `terrain glb is one mesh with the image after its buffers`() {
        val mesh =
            TerrainMeshData(
                positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
                normals = floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f),
                colors = FloatArray(12) { 1f },
                indices = intArrayOf(0, 1, 2),
                uvs = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            )
        val image = byteArrayOf(1, 2, 3, 4, 5)

        val glb = ByteBuffer.wrap(terrainGlb(mesh, image, "image/jpeg")).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0x46546C67, glb.getInt(0))
        assertEquals(glb.capacity(), glb.getInt(8))
        val jsonLength = glb.getInt(12)
        val json = String(glb.array(), 20, jsonLength)
        assertTrue("\"TEXCOORD_0\":3" in json)
        assertTrue("\"KHR_materials_unlit\"" in json)
        // 36 + 36 + 12 + 24 + 12 bytes of vertex and index data, then the image, padded to four.
        assertTrue("\"byteOffset\":120,\"byteLength\":5" in json)
        assertEquals(128, glb.getInt(20 + jsonLength))
        assertEquals(0, jsonLength % 4)
    }
}
