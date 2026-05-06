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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.meshtastic.sdk.MeshTopology
import org.meshtastic.sdk.NeighborInfo
import org.meshtastic.sdk.NodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MeshTopologyServiceTest {

    @Test
    fun `ingest neighbor info creates graph edges and node count`() = runTest {
        val service = MeshTopologyService()

        service.ingestNeighborInfo(neighborInfo(1, 2 to 7.5f, 3 to -1.0f, lastUpdated = 99))

        assertEquals(
            setOf(
                edge(1, 2, 7.5f, 99),
                edge(1, 3, -1.0f, 99),
            ),
            service.edges.value.toSet(),
        )
        assertEquals(3, service.nodeCount.value)
    }

    @Test
    fun `shortest path returns the bfs route`() = runTest {
        val service = MeshTopologyService()

        service.ingestNeighborInfo(neighborInfo(1, 2 to 5.0f, 3 to 4.0f))
        service.ingestNeighborInfo(neighborInfo(2, 4 to 3.0f))
        service.ingestNeighborInfo(neighborInfo(3, 5 to 2.0f))
        service.ingestNeighborInfo(neighborInfo(5, 4 to 1.0f))

        assertEquals(
            listOf(NodeId(1), NodeId(2), NodeId(4)),
            service.shortestPath(NodeId(1), NodeId(4)),
        )
    }

    @Test
    fun `direct reach is true for one hop neighbors`() = runTest {
        val service = MeshTopologyService()

        service.ingestNeighborInfo(neighborInfo(1, 2 to 6.0f))

        assertTrue(service.isDirectReach(NodeId(1), NodeId(2)))
        assertTrue(service.isDirectReach(NodeId(2), NodeId(1)))
        assertFalse(service.isDirectReach(NodeId(1), NodeId(3)))
    }

    @Test
    fun `remove node cleans all associated edges`() = runTest {
        val service = MeshTopologyService()

        service.ingestNeighborInfo(neighborInfo(1, 2 to 5.0f, 3 to 1.0f))
        service.ingestNeighborInfo(neighborInfo(4, 2 to 2.5f))

        service.removeNode(NodeId(2))

        assertEquals(setOf(edge(1, 3, 1.0f)), service.edges.value.toSet())
        assertEquals(3, service.nodeCount.value)
        assertFalse(service.isDirectReach(NodeId(1), NodeId(2)))
        assertEquals(emptyList(), service.getNeighbors(NodeId(2)))
    }

    @Test
    fun `concurrent access keeps reporter edges consistent`() = runTest {
        val service = MeshTopologyService()
        val firstSnapshot = neighborInfo(1, 2 to 5.0f, 3 to 4.0f)
        val secondSnapshot = neighborInfo(1, 4 to 9.0f)
        val expectedFirst = setOf(edge(1, 2, 5.0f), edge(1, 3, 4.0f))
        val expectedSecond = setOf(edge(1, 4, 9.0f))

        coroutineScope {
            repeat(100) { index ->
                launch {
                    if (index % 2 == 0) {
                        service.ingestNeighborInfo(firstSnapshot)
                    } else {
                        service.ingestNeighborInfo(secondSnapshot)
                    }
                    service.shortestPath(NodeId(1), NodeId(4))
                    service.isDirectReach(NodeId(1), NodeId(2))
                }
            }
        }

        val actualNeighbors = service.getNeighbors(NodeId(1)).toSet()
        assertTrue(actualNeighbors == expectedFirst || actualNeighbors == expectedSecond)
        assertEquals(actualNeighbors, service.edges.value.toSet())
        assertTrue(service.nodeCount.value == 3 || service.nodeCount.value == 2)
    }

    @Test
    fun `circular topology path search terminates`() = runTest {
        val service = MeshTopologyService()

        service.ingestNeighborInfo(neighborInfo(1, 2 to 1.0f))
        service.ingestNeighborInfo(neighborInfo(2, 3 to 1.0f))
        service.ingestNeighborInfo(neighborInfo(3, 1 to 1.0f, 4 to 1.0f))

        val path = withTimeout(1.seconds) { service.shortestPath(NodeId(1), NodeId(4)) }

        assertEquals(NodeId(1), path.first())
        assertEquals(NodeId(4), path.last())
        assertTrue(path.size in 3..4)
    }

    @Test
    fun `empty graph returns empty path and no direct reach`() = runTest {
        val service = MeshTopologyService()

        assertEquals(emptyList(), service.shortestPath(NodeId(1), NodeId(2)))
        assertFalse(service.isDirectReach(NodeId(1), NodeId(2)))
        assertEquals(emptyList(), service.edges.value)
        assertEquals(0, service.nodeCount.value)
    }

    @Test
    fun `clear removes all topology state`() = runTest {
        val service = MeshTopologyService()

        service.ingestNeighborInfo(neighborInfo(1, 2 to 5.0f))
        service.clear()

        assertEquals(emptyList(), service.edges.value)
        assertEquals(0, service.nodeCount.value)
        assertEquals(emptyList(), service.getNeighbors(NodeId(1)))
    }

    private fun neighborInfo(
        reporter: Int,
        vararg neighbors: Pair<Int, Float>,
        lastUpdated: Int = 0,
    ): NeighborInfo = NeighborInfo(
        nodeId = NodeId(reporter),
        neighbors = neighbors.map { (neighbor, snr) -> NeighborInfo.Neighbor(NodeId(neighbor), snr) },
        lastUpdated = lastUpdated,
    )

    private fun edge(
        from: Int,
        to: Int,
        snr: Float,
        lastUpdated: Int = 0,
    ): MeshTopology.Edge = MeshTopology.Edge(NodeId(from), NodeId(to), snr, lastUpdated)
}
