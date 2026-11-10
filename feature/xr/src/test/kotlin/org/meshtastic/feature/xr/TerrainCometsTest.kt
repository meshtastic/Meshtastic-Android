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
import org.meshtastic.feature.map.terrain.spatial.SpatialSignal
import org.meshtastic.feature.map.terrain.spatial.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerrainCometsTest {
    private fun marker(id: Long, x: Float, z: Float, node: SpatialNodeSnapshot.() -> SpatialNodeSnapshot = { this }) =
        NodeMarker(SpatialNodeSnapshot(id, LatLon(0.0, 0.0)).node(), Vec3(x, 0f, z))

    @Test
    fun `a comet flies each leg in turn and lands at the end`() {
        val a = Vec3(0f, 0f, 0f)
        val b = Vec3(0.3f, 0f, 0f)
        val c = Vec3(0.3f, 0f, 0.6f)
        val path = CometPath(listOf(a to b, b to c), Rgb(1f, 0f, 0f))

        // 0.3 and 0.6 units at 0.6 a second.
        assertEquals(listOf(0.5, 1.0), path.durations.map { Math.round(it * 1000) / 1000.0 })
        val start = assertNotNull(path.at(0.0))
        assertEquals(a, start.position)
        assertTrue(start.direction.x > 0, "sets off toward the first hop")
        val middle = assertNotNull(path.at(1.0))
        assertEquals(0.3f, middle.position.x, 1e-6f)
        assertEquals(0.3f, middle.position.z, 1e-6f)
        assertTrue(middle.position.y > 0, "rises between hops")
        assertTrue(middle.direction.z > 0 && middle.direction.x == 0f, "turns down the second leg")
        assertNull(path.at(path.duration))
    }

    @Test
    fun `a short hop still takes long enough to follow`() {
        val path = CometPath(listOf(Vec3(0f, 0f, 0f) to Vec3(0.01f, 0f, 0f)), Rgb(1f, 1f, 1f))

        assertEquals(0.4, path.duration)
    }

    @Test
    fun `a route runs out in orange and back in blue on either side of the line and passes over a missing hop`() {
        val markers = listOf(marker(1, 0f, 0f), marker(2, 0.2f, 0f), marker(4, 0.4f, 0f))

        val (out, back) = routePaths(markers, TerrainRoute(forward = listOf(1, 2, 3, 4), back = listOf(4, 1)))

        assertEquals(2, out.legs.size)
        assertEquals(1, back.legs.size)
        assertEquals(Rgb(0xE8 / 255f, 0x6A / 255f, 0f), out.color)
        assertEquals(Rgb(0f, 0x81 / 255f, 0xC7 / 255f), back.color)
        assertTrue(out.legs.all { it.first.z > 0 && it.second.z > 0 }, "the way out keeps to one side")
        assertTrue(back.legs.all { it.first.z < 0 && it.second.z < 0 }, "the way back to the other")
        assertTrue(routePaths(markers, null).isEmpty())
        assertTrue(routePaths(markers, TerrainRoute(listOf(9, 8), emptyList())).isEmpty())
    }

    @Test
    fun `a comet flies in from each node the radio hears directly in the signal's color`() {
        val markers =
            listOf(
                marker(1, 0f, 0f) { copy(isConnectedRadio = true) },
                marker(2, 0.2f, 0f) { copy(hopsAway = 0, signal = SpatialSignal.GOOD) },
                marker(3, 0.4f, 0f) { copy(hopsAway = 2) },
            )

        val paths = linkPaths(markers)

        assertEquals(setOf(2L), paths.keys)
        val path = paths.getValue(2)
        assertEquals(markers[1].top, path.legs.single().first)
        assertEquals(markers[0].top, path.legs.single().second)
        assertEquals(Rgb(0x3F / 255f, 0xB8 / 255f, 0x6D / 255f), path.color)
        assertTrue(linkPaths(markers.drop(1)).isEmpty(), "none with no connected radio in view")
    }

    @Test
    fun `a comet's tail trails back from its head and tapers`() {
        val mesh = cometMeshData(Rgb(0f, 0.5f, 1f))

        val z = mesh.positions.filterIndexed { i, _ -> i % 3 == 2 }
        assertTrue(z.min() < -0.04f, "the tail reaches back")
        assertTrue(z.max() < 0.007f, "nothing ahead of the head")
        assertTrue(mesh.colors[0] > 0f, "the head is lighter than the tail")
    }
}
