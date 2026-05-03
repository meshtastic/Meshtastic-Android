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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.User

/**
 * A test double for [NodeRepository] that provides an in-memory implementation.
 *
 * Tracks node operations and exposes mutable state for assertions in tests.
 *
 * Example:
 * ```kotlin
 * val nodeRepository = FakeNodeRepository()
 * nodeRepository.setNodes(TestDataFactory.createTestNodes(3))
 * assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
 * ```
 */
@Suppress("TooManyFunctions")
class FakeNodeRepository :
    BaseFake(),
    NodeRepository {

    private val _myNodeInfo = mutableStateFlow<MyNodeInfo?>(null)
    override val myNodeInfo: StateFlow<MyNodeInfo?> = _myNodeInfo

    private val _ourNodeInfo = mutableStateFlow<Node?>(null)
    override val ourNodeInfo: StateFlow<Node?> = _ourNodeInfo

    private val _myId = mutableStateFlow<String?>(null)
    override val myId: StateFlow<String?> = _myId

    private val _localStats = mutableStateFlow(LocalStats())
    override val localStats: StateFlow<LocalStats> = _localStats

    private val _nodeDBbyNum = mutableStateFlow<Map<Int, Node>>(emptyMap())
    override val nodeDBbyNum: StateFlow<Map<Int, Node>> = _nodeDBbyNum

    override val onlineNodeCount: Flow<Int> = _nodeDBbyNum.map { it.size }
    override val totalNodeCount: Flow<Int> = _nodeDBbyNum.map { it.size }

    override fun updateLocalStats(stats: LocalStats) {
        _localStats.value = stats
    }

    override fun effectiveLogNodeId(nodeNum: Int): Flow<Int> = MutableStateFlow(0)

    override fun getNode(userId: String): Node =
        _nodeDBbyNum.value.values.find { it.user.id == userId } ?: Node(num = 0, user = User(id = userId))

    override fun getUser(nodeNum: Int): User = _nodeDBbyNum.value[nodeNum]?.user ?: User()

    override fun getUser(userId: String): User = _nodeDBbyNum.value.values.find { it.user.id == userId }?.user ?: User()

    override fun getNodes(
        sort: NodeSortOption,
        filter: String,
        includeUnknown: Boolean,
        onlyOnline: Boolean,
        onlyDirect: Boolean,
    ): Flow<List<Node>> = _nodeDBbyNum.map { db ->
        db.values
            .asSequence()
            .filter { filterNode(it, filter, includeUnknown, onlyOnline, onlyDirect) }
            .toList()
            .let { nodes ->
                when (sort) {
                    NodeSortOption.ALPHABETICAL -> nodes.sortedBy { it.user.long_name.lowercase() }

                    NodeSortOption.LAST_HEARD -> nodes.sortedByDescending { it.lastHeard }

                    NodeSortOption.DISTANCE -> nodes.sortedBy { it.position.latitude_i }

                    // Simplified
                    NodeSortOption.HOPS_AWAY -> nodes.sortedBy { it.hopsAway }

                    NodeSortOption.CHANNEL -> nodes.sortedBy { it.channel }

                    NodeSortOption.VIA_MQTT -> nodes.sortedBy { if (it.viaMqtt) 0 else 1 }

                    NodeSortOption.VIA_FAVORITE -> nodes.sortedBy { if (it.isFavorite) 0 else 1 }
                }
            }
    }

    private fun filterNode(
        node: Node,
        filter: String,
        includeUnknown: Boolean,
        onlyOnline: Boolean,
        onlyDirect: Boolean,
    ): Boolean {
        val matchesFilter =
            filter.isBlank() ||
                node.user.long_name.contains(filter, ignoreCase = true) ||
                node.user.id.contains(filter, ignoreCase = true)
        val matchesUnknown = includeUnknown || !node.isUnknownUser
        val matchesOnline = !onlyOnline || node.isOnline
        val matchesDirect = !onlyDirect || node.hopsAway == 0

        return matchesFilter && matchesUnknown && matchesOnline && matchesDirect
    }

    override suspend fun getNodesOlderThan(lastHeard: Int): List<Node> =
        _nodeDBbyNum.value.values.filter { it.lastHeard < lastHeard }

    override suspend fun getUnknownNodes(): List<Node> = _nodeDBbyNum.value.values.filter { it.isUnknownUser }

    override suspend fun clearNodeDB(preserveFavorites: Boolean) {
        if (preserveFavorites) {
            _nodeDBbyNum.value = _nodeDBbyNum.value.filter { it.value.isFavorite }
        } else {
            _nodeDBbyNum.value = emptyMap()
        }
    }

    override suspend fun clearMyNodeInfo() {
        _myNodeInfo.value = null
    }

    override suspend fun deleteNode(num: Int) {
        _nodeDBbyNum.value = _nodeDBbyNum.value - num
    }

    override suspend fun deleteNodes(nodeNums: List<Int>) {
        _nodeDBbyNum.value = _nodeDBbyNum.value - nodeNums.toSet()
    }

    override suspend fun setNodeNotes(num: Int, notes: String) {
        val node = _nodeDBbyNum.value[num] ?: return
        _nodeDBbyNum.value = _nodeDBbyNum.value + (num to node.copy(notes = notes))
    }

    override suspend fun upsert(node: Node) {
        _nodeDBbyNum.value = _nodeDBbyNum.value + (node.num to node)
    }

    override suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>) {
        _myNodeInfo.value = mi
        _nodeDBbyNum.value = nodes.associateBy { it.num }
    }

    override suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata) {
        val node = _nodeDBbyNum.value[nodeNum] ?: return
        _nodeDBbyNum.value = _nodeDBbyNum.value + (nodeNum to node.copy(metadata = metadata))
    }

    // --- Helper methods for testing ---

    fun setNodes(nodes: List<Node>) {
        _nodeDBbyNum.value = nodes.associateBy { it.num }
    }

    fun setMyId(id: String) {
        _myId.value = id
    }

    fun setOurNode(node: Node?) {
        _ourNodeInfo.value = node
    }

    fun setMyNodeInfo(info: MyNodeInfo?) {
        _myNodeInfo.value = info
    }
}
