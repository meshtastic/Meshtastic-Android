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
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot
import org.meshtastic.feature.map.terrain.spatial.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainChipsTest {
    private fun marker(
        id: Long,
        name: String = "N$id",
        node: SpatialNodeSnapshot.() -> SpatialNodeSnapshot = { this },
    ) = NodeMarker(SpatialNodeSnapshot(id, LatLon(0.0, 0.0), shortName = name).node(), Vec3(id * 0.01f, 0.1f, 0.2f))

    @Test
    fun `a chip stands above its pin, the connected radio first, and unnamed nodes have none`() {
        val markers =
            listOf(
                marker(1) { copy(isOnline = false) },
                marker(2) { copy(isFavorite = true) },
                marker(3, name = ""),
                marker(4) { copy(isConnectedRadio = true) },
            )

        val chips = terrainChips(markers)

        assertEquals(listOf("N4", "N2", "N1"), chips.map { it.name })
        val radio = chips.first()
        assertEquals(markers[3].top.x, radio.at.x)
        assertEquals(markers[3].top.z, radio.at.z)
        assertTrue(radio.at.y > markers[3].top.y, "above the pin's ball")
    }

    @Test
    fun `there are only so many chips`() {
        val chips = terrainChips(List(40) { marker(it + 1L) })

        assertEquals(18, chips.size)
    }

    @Test
    fun `a chip's quad stands upright about its middle and faces the front`() {
        val quad = chipMeshData()

        assertEquals(4, quad.vertexCount)
        assertTrue(quad.positions.filterIndexed { i, _ -> i % 3 == 2 }.all { it == 0f }, "flat in x and y")
        assertEquals(listOf(0f, 0f, 1f), quad.normals.take(3))
        val p = quad.positions
        val ux = p[3] - p[0]
        val uy = p[4] - p[1]
        val vx = p[6] - p[0]
        val vy = p[7] - p[1]
        assertTrue(ux * vy - uy * vx > 0, "counter-clockwise seen from the front")
        assertEquals(-p.take(3).first(), p[3], "centered")
    }
}
