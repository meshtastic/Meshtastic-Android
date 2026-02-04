/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.data.repository

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.datasource.NodeInfoWriteDataSource
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.NodeSortOption
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.di.ProcessLifecycle
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class NodeRepository
@Inject
constructor(
    @ProcessLifecycle processLifecycle: Lifecycle,
    private val nodeInfoReadDataSource: NodeInfoReadDataSource,
    private val nodeInfoWriteDataSource: NodeInfoWriteDataSource,
    private val dispatchers: CoroutineDispatchers,
) {
    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?> =
        nodeInfoReadDataSource
            .myNodeInfoFlow()
            .flowOn(dispatchers.io)
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, null)

    // our node info
    private val _ourNodeInfo = MutableStateFlow<Node?>(null)
    val ourNodeInfo: StateFlow<Node?>
        get() = _ourNodeInfo

    // The unique userId of our node
    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?>
        get() = _myId

    // A map from nodeNum to Node
    val nodeDBbyNum: StateFlow<Map<Int, Node>> =
        nodeInfoReadDataSource
            .nodeDBbyNumFlow()
            .mapLatest { map -> map.mapValues { (_, it) -> it.toModel() } }
            .flowOn(dispatchers.io)
            .conflate()
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptyMap())

    init {
        // Backfill denormalized name columns for existing nodes on startup
        processLifecycle.coroutineScope.launch {
            processLifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                withContext(dispatchers.io) { nodeInfoWriteDataSource.backfillDenormalizedNames() }
            }
        }

        // Keep ourNodeInfo and myId correctly updated based on current connection and node DB
        combine(nodeDBbyNum, myNodeInfo) { db, info -> info?.myNodeNum?.let { db[it] } }
            .onEach { node ->
                _ourNodeInfo.value = node
                _myId.value = node?.user?.id
            }
            .launchIn(processLifecycle.coroutineScope)
    }

    fun getNodeDBbyNum() =
        nodeInfoReadDataSource.nodeDBbyNumFlow().map { map -> map.mapValues { (_, it) -> it.toEntity() } }

    fun getNode(userId: String): Node = nodeDBbyNum.value.values.find { it.user.id == userId }
        ?: Node(num = DataPacket.idToDefaultNodeNum(userId) ?: 0, user = getUser(userId))

    fun getUser(nodeNum: Int): User = getUser(DataPacket.nodeNumToDefaultId(nodeNum))

    fun getUser(userId: String): User = nodeDBbyNum.value.values.find { it.user.id == userId }?.user
        ?: User(
            id = userId,
            long_name =
            if (userId == DataPacket.ID_LOCAL) {
                ourNodeInfo.value?.user?.long_name ?: "Local"
            } else {
                "Meshtastic ${userId.takeLast(n = 4)}"
            },
            short_name =
            if (userId == DataPacket.ID_LOCAL) {
                ourNodeInfo.value?.user?.short_name ?: "Local"
            } else {
                userId.takeLast(n = 4)
            },
            hw_model = HardwareModel.UNSET,
        )

    fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
        onlyOnline: Boolean = false,
        onlyDirect: Boolean = false,
    ) = nodeInfoReadDataSource
        .getNodesFlow(
            sort = sort.sqlValue,
            filter = filter,
            includeUnknown = includeUnknown,
            hopsAwayMax = if (onlyDirect) 0 else -1,
            lastHeardMin = if (onlyOnline) onlineTimeThreshold() else -1,
        )
        .mapLatest { list -> list.map { it.toModel() } }
        .flowOn(dispatchers.io)
        .conflate()

    suspend fun upsert(node: NodeEntity) = withContext(dispatchers.io) { nodeInfoWriteDataSource.upsert(node) }

    suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.installConfig(mi, nodes) }

    suspend fun clearNodeDB(preserveFavorites: Boolean = false) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.clearNodeDB(preserveFavorites) }

    suspend fun clearMyNodeInfo() = withContext(dispatchers.io) { nodeInfoWriteDataSource.clearMyNodeInfo() }

    suspend fun deleteNode(num: Int) = withContext(dispatchers.io) {
        nodeInfoWriteDataSource.deleteNode(num)
        nodeInfoWriteDataSource.deleteMetadata(num)
    }

    suspend fun deleteNodes(nodeNums: List<Int>) = withContext(dispatchers.io) {
        nodeInfoWriteDataSource.deleteNodes(nodeNums)
        nodeNums.forEach { nodeInfoWriteDataSource.deleteMetadata(it) }
    }

    suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity> =
        withContext(dispatchers.io) { nodeInfoReadDataSource.getNodesOlderThan(lastHeard) }

    suspend fun getUnknownNodes(): List<NodeEntity> =
        withContext(dispatchers.io) { nodeInfoReadDataSource.getUnknownNodes() }

    suspend fun insertMetadata(metadata: MetadataEntity) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.upsert(metadata) }

    val onlineNodeCount: Flow<Int> =
        nodeInfoReadDataSource
            .nodeDBbyNumFlow()
            .mapLatest { map -> map.values.count { it.node.lastHeard > onlineTimeThreshold() } }
            .flowOn(dispatchers.io)
            .conflate()

    val totalNodeCount: Flow<Int> =
        nodeInfoReadDataSource
            .nodeDBbyNumFlow()
            .mapLatest { map -> map.values.count() }
            .flowOn(dispatchers.io)
            .conflate()

    suspend fun setNodeNotes(num: Int, notes: String) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.setNodeNotes(num, notes) }
}
