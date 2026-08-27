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
package org.meshtastic.feature.map.maplibre

import org.meshtastic.core.model.Node
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rule deciding which nodes are drawn on their own, and so which ones get a chip and a precision circle.
 *
 * It stands in for a decision the source makes internally at render time and never reports back.
 */
class UnclusteredNodesTest {

    private companion object {
        const val RADIUS_PX = 50
        const val MIN_POINTS = 10
        const val MAX_CLUSTER_ZOOM = 20
    }

    private fun node(num: Int, latitude: Double, longitude: Double) = Node(
        num = num,
        position = Position(latitude_i = (latitude * 1e7).toInt(), longitude_i = (longitude * 1e7).toInt()),
    )

    /** [count] nodes on the same spot, which is what a crowded venue looks like. */
    private fun pile(count: Int, latitude: Double = 36.13, longitude: Double = -115.16) =
        (1..count).map { node(it, latitude, longitude) }

    private fun unclustered(nodes: List<Node>, zoom: Int) =
        unclusteredNodes(nodes, zoom, RADIUS_PX, MIN_POINTS, MAX_CLUSTER_ZOOM)

    @Test
    fun `a pile bigger than the minimum is clustered so none of it stands alone`() {
        assertTrue(unclustered(pile(MIN_POINTS), zoom = 10).isEmpty())
        assertTrue(unclustered(pile(2500), zoom = 10).isEmpty())
    }

    @Test
    fun `a pile smaller than the minimum is left alone as the Google flavor leaves it`() {
        // Below MIN_CLUSTER_SIZE the Google renderer draws the markers themselves rather than a bubble.
        assertEquals(MIN_POINTS - 1, unclustered(pile(MIN_POINTS - 1), zoom = 10).size)
    }

    @Test
    fun `a node far from the pile stands alone even though the pile clusters`() {
        val nodes = pile(50) + node(9999, latitude = 36.60, longitude = -115.60)

        val alone = unclustered(nodes, zoom = 10)

        assertEquals(listOf(9999), alone.map { it.num })
    }

    @Test
    fun `zooming in far enough separates a pile that was clustered`() {
        // The same points measured in tile pixels: distance grows with zoom, so eventually nobody has enough
        // neighbours left inside the radius.
        val spread = (1..20).map { node(it, latitude = 36.13 + it * 0.0002, longitude = -115.16) }

        assertTrue(unclustered(spread, zoom = 10).isEmpty())
        assertEquals(spread.size, unclustered(spread, zoom = 19).size)
    }

    @Test
    fun `above the cluster ceiling nothing is clustered at all`() {
        // The source stops clustering past its max zoom, so every node is drawn individually whatever the density.
        assertEquals(2500, unclustered(pile(2500), zoom = MAX_CLUSTER_ZOOM + 1).size)
    }
}
