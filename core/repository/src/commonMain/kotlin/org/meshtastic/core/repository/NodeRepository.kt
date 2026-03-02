/*
 * Copyright (c) 2025 Meshtastic LLC
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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.User

/**
 * Repository interface for managing node-related data.
 * This interface is shared across platforms via KMP.
 */
interface NodeRepository {
    /** Hardware info about our local device. */
    val myNodeInfo: StateFlow<MyNodeInfo?>

    /** Information about the locally connected node, as seen from the mesh. */
    val ourNodeInfo: StateFlow<Node?>

    /** The unique userId (hex string) of our local node. */
    val myId: StateFlow<String?>

    /** The latest local stats telemetry received from the locally connected node. */
    val localStats: StateFlow<LocalStats>

    /** A reactive map from nodeNum to [Node] objects, representing the entire mesh. */
    val nodeDBbyNum: StateFlow<Map<Int, Node>>

    /** Flow emitting the count of nodes currently considered "online". */
    val onlineNodeCount: Flow<Int>

    /** Flow emitting the total number of nodes in the database. */
    val totalNodeCount: Flow<Int>

    /** Update the cached local stats telemetry. */
    fun updateLocalStats(stats: LocalStats)

    /** Returns the node number used for log queries. */
    fun effectiveLogNodeId(nodeNum: Int): Flow<Int>

    /** Returns the [Node] associated with a given [userId]. */
    fun getNode(userId: String): Node

    /** Returns the [User] info for a given [nodeNum]. */
    fun getUser(nodeNum: Int): User

    /** Returns the [User] info for a given [userId]. */
    fun getUser(userId: String): User

    /** Returns a flow of nodes filtered and sorted according to the parameters. */
    fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
        onlyOnline: Boolean = false,
        onlyDirect: Boolean = false,
    ): Flow<List<Node>>

    suspend fun getNodesOlderThan(lastHeard: Int): List<Node>

    suspend fun getUnknownNodes(): List<Node>

    /** Deletes all nodes from the database. */
    suspend fun clearNodeDB(preserveFavorites: Boolean = false)

    /** Clears the local node's connection info. */
    suspend fun clearMyNodeInfo()

    /** Deletes a node by its number. */
    suspend fun deleteNode(num: Int)

    /** Deletes multiple nodes. */
    suspend fun deleteNodes(nodeNums: List<Int>)

    /** Updates the personal notes for a node. */
    suspend fun setNodeNotes(num: Int, notes: String)

    /** Installs initial configuration data (local info and remote nodes) into the database. */
    suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>)

    /** Persists hardware metadata for a node. */
    suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata)
}
