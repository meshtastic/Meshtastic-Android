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

import org.maplibre.spatialk.geojson.BoundingBox
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

    @Test
    fun `a node already left alone is not absorbed into a later cluster`() {
        // At zoom 10 a degree is ~1456 tile pixels, so 0.0309 deg ~ 45px — inside the 50px radius, one hop at a time.
        // The first node's only neighbour is the bridge, so it stands alone and — as supercluster does — leaves the
        // pool. The bridge then counts 9 without it, short of MIN_POINTS, so nothing here may cluster: every node
        // stands alone rather than the early loner being counted into a cluster that only reaches 10 through it.
        val loner = node(1, latitude = 36.13, longitude = -115.2000)
        val bridge = node(2, latitude = 36.13, longitude = -115.1691)
        val pileNearBridge = (3..10).map { node(it, latitude = 36.13, longitude = -115.1382) }
        assertEquals(10, unclustered(listOf(loner, bridge) + pileNearBridge, zoom = 10).size)
    }

    @Test
    fun `isolation ranking puts the loneliest nodes first so the chip budget reaches them`() {
        // The chip budget is spent down this order, so the nodes certain to be drawn on their own have to come first.
        val nodes = pile(30) + node(9999, latitude = 36.60, longitude = -115.60)

        val ranked = nodesByIsolation(nodes, zoom = 10, radiusPx = RADIUS_PX)

        assertEquals(9999, ranked.first().num)
        assertEquals(nodes.size, ranked.size)
    }

    @Test
    fun `isolation ranking survives being narrowed to the viewport afterwards`() {
        // Ranked over the whole mesh and filtered to the viewport second: a node at the edge of the screen with
        // neighbours just off it is not in fact alone, and the filter has to leave that order alone.
        // At zoom 10 a degree is roughly 1456 tile pixels, so 0.01 deg sits inside the 50px cluster radius and
        // 0.16 deg sits well outside it.
        val offScreenPile = pile(30, latitude = 36.13, longitude = -115.16)
        val onScreenEdge = node(8888, latitude = 36.13, longitude = -115.15)
        val onScreenLoner = node(9999, latitude = 36.13, longitude = -115.00)

        val ranked = nodesByIsolation(offScreenPile + onScreenEdge + onScreenLoner, zoom = 10, radiusPx = RADIUS_PX)
        // West edge falls between the pile and the node beside it, so only the latter two are on screen.
        val onScreen = BoundingBox(west = -115.155, south = 36.12, east = -114.99, north = 36.14)

        val visible = nodesInView(ranked, onScreen)

        assertEquals(listOf(9999, 8888), visible.map { it.num })
    }
}
