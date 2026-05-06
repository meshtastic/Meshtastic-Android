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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import org.meshtastic.sdk.MeshTopology
import org.meshtastic.sdk.NeighborInfo
import org.meshtastic.sdk.NodeId

/**
 * Thread-safe wrapper around SDK's [MeshTopology] graph utility.
 *
 * Fed by [SdkStateBridge] whenever a NEIGHBORINFO_APP packet arrives. Exposes reactive
 * topology state for feature modules (map visualization, route analysis, neighbor lists).
 *
 * The graph is incrementally built: each [ingestNeighborInfo] call replaces all edges from
 * the reporting node, keeping the topology fresh as nodes broadcast their neighbor tables.
 */
@Single
class MeshTopologyService {
    private val topology = MeshTopology()
    private val mutex = Mutex()

    private val _edges = MutableStateFlow<List<MeshTopology.Edge>>(emptyList())
    /** All directed edges in the mesh topology graph. */
    val edges: StateFlow<List<MeshTopology.Edge>> = _edges

    private val _nodeCount = MutableStateFlow(0)
    /** Total number of nodes participating in the topology (reporters + reported neighbors). */
    val nodeCount: StateFlow<Int> = _nodeCount

    /**
     * Ingest a [NeighborInfo] report into the topology graph.
     * Replaces all prior edges from the reporting node.
     */
    suspend fun ingestNeighborInfo(info: NeighborInfo) {
        mutex.withLock {
            topology.addNeighborInfo(info)
            _edges.value = topology.allEdges()
            _nodeCount.value = topology.nodes().size
        }
        Logger.d { "[Topology] Ingested neighbors from ${info.nodeId}: ${info.neighbors.size} edges" }
    }

    /** Remove a node from the topology (e.g., when it goes permanently offline). */
    suspend fun removeNode(nodeId: NodeId) {
        mutex.withLock {
            topology.removeNode(nodeId)
            _edges.value = topology.allEdges()
            _nodeCount.value = topology.nodes().size
        }
    }

    /** Get all neighbors of a specific node (thread-safe snapshot). */
    suspend fun getNeighbors(nodeId: NodeId): List<MeshTopology.Edge> =
        mutex.withLock { topology.getNeighbors(nodeId) }

    /** Find the shortest path between two nodes via BFS. */
    suspend fun shortestPath(from: NodeId, to: NodeId): List<NodeId> =
        mutex.withLock { topology.shortestPath(from, to) }

    /** Check if two nodes have a direct edge in either direction. */
    suspend fun isDirectReach(a: NodeId, b: NodeId): Boolean =
        mutex.withLock { topology.isDirectReach(a, b) }

    /** Clear all topology data (e.g., on disconnect). */
    suspend fun clear() {
        mutex.withLock {
            topology.clear()
            _edges.value = emptyList()
            _nodeCount.value = 0
        }
    }
}
