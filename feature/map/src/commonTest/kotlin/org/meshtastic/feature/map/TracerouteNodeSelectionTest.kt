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
package org.meshtastic.feature.map

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TracerouteNodeSelectionTest {

    private fun nodeWithPosition(num: Int, latI: Int = num * 100000, lonI: Int = num * 200000): Node =
        Node(num = num, position = Position(latitude_i = latI, longitude_i = lonI))

    private fun nodeWithoutPosition(num: Int): Node = Node(num = num, position = Position())

    private val defaultGetNodeOrFallback: (Int) -> Node = { num -> Node(num = num) }

    // ---- Null overlay (no traceroute active) ----

    @Test
    fun nullOverlay_returnsAllNodesUnfiltered() {
        val nodes = listOf(nodeWithPosition(1), nodeWithPosition(2), nodeWithPosition(3))
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = null,
                tracerouteNodePositions = emptyMap(),
                nodes = nodes,
                getNodeOrFallback = defaultGetNodeOrFallback,
            )

        assertEquals(emptySet(), result.overlayNodeNums)
        assertEquals(3, result.nodesForMarkers.size)
        assertEquals(nodes.map { it.num }.toSet(), result.nodesForMarkers.map { it.num }.toSet())
    }

    @Test
    fun nullOverlay_nodeLookupContainsOnlyNodesWithValidPositions() {
        val nodes = listOf(nodeWithPosition(1), nodeWithoutPosition(2), nodeWithPosition(3))
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = null,
                tracerouteNodePositions = emptyMap(),
                nodes = nodes,
                getNodeOrFallback = defaultGetNodeOrFallback,
            )

        // nodeLookup filters to validPosition nodes when no snapshot
        assertEquals(setOf(1, 3), result.nodeLookup.keys)
    }

    // ---- Overlay with snapshot positions ----

    @Test
    fun overlayWithSnapshot_usesSnapshotPositions() {
        val overlay = TracerouteOverlay(requestId = 1, forwardRoute = listOf(10, 20), returnRoute = listOf(20, 10))
        val snapshotPositions =
            mapOf(
                10 to Position(latitude_i = 400000000, longitude_i = -700000000),
                20 to Position(latitude_i = 410000000, longitude_i = -710000000),
            )
        val liveNodes =
            listOf(
                nodeWithPosition(10, latI = 100000000, lonI = -100000000),
                nodeWithPosition(20, latI = 200000000, lonI = -200000000),
                nodeWithPosition(30),
            )
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = overlay,
                tracerouteNodePositions = snapshotPositions,
                nodes = liveNodes,
                getNodeOrFallback = { num -> liveNodes.find { it.num == num } ?: Node(num = num) },
            )

        // Should use snapshot positions, not live ones
        assertEquals(setOf(10, 20), result.overlayNodeNums)
        assertEquals(2, result.nodesForMarkers.size)
        assertEquals(400000000, result.nodesForMarkers.first { it.num == 10 }.position.latitude_i)
        assertEquals(410000000, result.nodesForMarkers.first { it.num == 20 }.position.latitude_i)
    }

    @Test
    fun overlayWithSnapshot_nodeLookupUsesSnapshotNodes() {
        val overlay = TracerouteOverlay(requestId = 1, forwardRoute = listOf(10, 20))
        val snapshotPositions =
            mapOf(
                10 to Position(latitude_i = 400000000, longitude_i = -700000000),
                20 to Position(latitude_i = 410000000, longitude_i = -710000000),
            )
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = overlay,
                tracerouteNodePositions = snapshotPositions,
                nodes = emptyList(),
                getNodeOrFallback = { num -> Node(num = num) },
            )

        assertEquals(2, result.nodeLookup.size)
        assertEquals(400000000, result.nodeLookup[10]?.position?.latitude_i)
    }

    @Test
    fun overlayWithSnapshot_filtersToOverlayNodes() {
        // Snapshot has node 30 which is NOT in the overlay routes
        val overlay = TracerouteOverlay(requestId = 1, forwardRoute = listOf(10, 20))
        val snapshotPositions =
            mapOf(
                10 to Position(latitude_i = 400000000, longitude_i = -700000000),
                20 to Position(latitude_i = 410000000, longitude_i = -710000000),
                30 to Position(latitude_i = 420000000, longitude_i = -720000000),
            )
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = overlay,
                tracerouteNodePositions = snapshotPositions,
                nodes = emptyList(),
                getNodeOrFallback = { num -> Node(num = num) },
            )

        // nodesForMarkers should only contain nodes in the overlay (10, 20), not 30
        assertEquals(setOf(10, 20), result.nodesForMarkers.map { it.num }.toSet())
        // but nodeLookup has all snapshot nodes (for polyline drawing)
        assertEquals(3, result.nodeLookup.size)
    }

    // ---- Overlay without snapshot positions (live fallback) ----

    @Test
    fun overlayWithoutSnapshot_filtersLiveNodesToOverlayNums() {
        val overlay = TracerouteOverlay(requestId = 1, forwardRoute = listOf(10, 20), returnRoute = listOf(30))
        val liveNodes = listOf(nodeWithPosition(10), nodeWithPosition(20), nodeWithPosition(30), nodeWithPosition(40))
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = overlay,
                tracerouteNodePositions = emptyMap(),
                nodes = liveNodes,
                getNodeOrFallback = defaultGetNodeOrFallback,
            )

        assertEquals(setOf(10, 20, 30), result.overlayNodeNums)
        assertEquals(setOf(10, 20, 30), result.nodesForMarkers.map { it.num }.toSet())
    }

    @Test
    fun overlayWithoutSnapshot_nodeLookupFiltersToValidPositions() {
        val overlay = TracerouteOverlay(requestId = 1, forwardRoute = listOf(10, 20))
        val liveNodes = listOf(nodeWithPosition(10), nodeWithoutPosition(20))
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = overlay,
                tracerouteNodePositions = emptyMap(),
                nodes = liveNodes,
                getNodeOrFallback = defaultGetNodeOrFallback,
            )

        // nodeLookup only includes nodes with validPosition
        assertEquals(setOf(10), result.nodeLookup.keys)
    }

    // ---- Edge cases ----

    @Test
    fun emptyOverlayRoutes_yieldsEmptySelection() {
        val overlay = TracerouteOverlay(requestId = 1, forwardRoute = emptyList(), returnRoute = emptyList())
        val liveNodes = listOf(nodeWithPosition(10), nodeWithPosition(20))
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = overlay,
                tracerouteNodePositions = emptyMap(),
                nodes = liveNodes,
                getNodeOrFallback = defaultGetNodeOrFallback,
            )

        assertTrue(result.overlayNodeNums.isEmpty())
        assertTrue(result.nodesForMarkers.isEmpty())
    }

    @Test
    fun getNodeOrFallback_usedForSnapshotNodeLookup() {
        val overlay = TracerouteOverlay(requestId = 1, forwardRoute = listOf(10))
        val snapshotPositions = mapOf(10 to Position(latitude_i = 400000000, longitude_i = -700000000))
        var lookupCalledWith: Int? = null
        val result =
            tracerouteNodeSelection(
                tracerouteOverlay = overlay,
                tracerouteNodePositions = snapshotPositions,
                nodes = emptyList(),
                getNodeOrFallback = { num ->
                    lookupCalledWith = num
                    Node(num = num)
                },
            )

        assertEquals(10, lookupCalledWith)
        assertEquals(1, result.nodesForMarkers.size)
    }
}
