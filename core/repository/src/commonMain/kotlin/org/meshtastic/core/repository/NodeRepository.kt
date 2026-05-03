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
 *
 * This component provides access to the mesh's node database, local device information, and mesh-wide statistics. It
 * supports reactive queries for node lists, counts, and filtered/sorted views.
 *
 * This interface is shared across platforms via Kotlin Multiplatform (KMP).
 */
@Suppress("TooManyFunctions")
interface NodeRepository {
    /** Reactive flow of hardware info about our local radio device. */
    val myNodeInfo: StateFlow<MyNodeInfo?>

    /**
     * Reactive flow of information about the locally connected node as seen by the mesh.
     *
     * This includes its position, telemetry, and user information as reflected in the mesh's node DB.
     */
    val ourNodeInfo: StateFlow<Node?>

    /** The unique userId (hex string, e.g., "!1234abcd") of our local node. */
    val myId: StateFlow<String?>

    /** Reactive flow of the latest local stats telemetry received from the radio. */
    val localStats: StateFlow<LocalStats>

    /** A reactive map of all known nodes in the mesh, keyed by their 32-bit node number. */
    val nodeDBbyNum: StateFlow<Map<Int, Node>>

    /** Flow emitting the count of nodes currently considered "online" (heard from recently). */
    val onlineNodeCount: Flow<Int>

    /** Flow emitting the total number of nodes in the database. */
    val totalNodeCount: Flow<Int>

    /**
     * Updates the cached local stats telemetry.
     *
     * @param stats The new [LocalStats].
     */
    fun updateLocalStats(stats: LocalStats)

    /**
     * Returns the node number used for log queries.
     *
     * Maps the local node's number to a constant (e.g., 0) to distinguish it from remote logs.
     */
    fun effectiveLogNodeId(nodeNum: Int): Flow<Int>

    /**
     * Returns the [Node] associated with a given [userId].
     *
     * @param userId The hex string identifier.
     * @return The found [Node] or a fallback object.
     */
    fun getNode(userId: String): Node

    /**
     * Returns the [User] info for a given [nodeNum].
     *
     * @param nodeNum The 32-bit node number.
     * @return The associated [User] proto.
     */
    fun getUser(nodeNum: Int): User

    /**
     * Returns the [User] info for a given [userId].
     *
     * @param userId The hex string identifier.
     * @return The associated [User] proto.
     */
    fun getUser(userId: String): User

    /**
     * Returns a reactive flow of nodes filtered and sorted according to the parameters.
     *
     * @param sort The [NodeSortOption] to apply.
     * @param filter A search string for filtering by name or ID.
     * @param includeUnknown Whether to include nodes with unset hardware models.
     * @param onlyOnline Whether to include only nodes currently considered online.
     * @param onlyDirect Whether to include only nodes heard directly (0 hops away).
     */
    fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
        onlyOnline: Boolean = false,
        onlyDirect: Boolean = false,
    ): Flow<List<Node>>

    /** Returns all nodes that haven't been heard from since the given timestamp. */
    suspend fun getNodesOlderThan(lastHeard: Int): List<Node>

    /** Returns all nodes with unknown hardware models. */
    suspend fun getUnknownNodes(): List<Node>

    /**
     * Deletes all nodes from the database.
     *
     * @param preserveFavorites If true, nodes marked as favorite will not be deleted.
     */
    suspend fun clearNodeDB(preserveFavorites: Boolean = false)

    /** Clears the local node's connection info from the cache. */
    suspend fun clearMyNodeInfo()

    /**
     * Deletes a specific node by its node number.
     *
     * @param num The node number to delete.
     */
    suspend fun deleteNode(num: Int)

    /**
     * Deletes multiple nodes by their node numbers.
     *
     * @param nodeNums The list of node numbers to delete.
     */
    suspend fun deleteNodes(nodeNums: List<Int>)

    /**
     * Updates the personal notes for a node.
     *
     * @param num The node number.
     * @param notes The human-readable notes to persist.
     */
    suspend fun setNodeNotes(num: Int, notes: String)

    /**
     * Upserts a [Node] into the persistent database.
     *
     * @param node The [Node] model to save.
     */
    suspend fun upsert(node: Node)

    /**
     * Installs initial configuration data (local info and remote nodes) into the database.
     *
     * Used during the initial connection handshake.
     */
    suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>)

    /**
     * Persists hardware metadata for a node.
     *
     * @param nodeNum The node number.
     * @param metadata The [DeviceMetadata] to save.
     */
    suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata)
}
