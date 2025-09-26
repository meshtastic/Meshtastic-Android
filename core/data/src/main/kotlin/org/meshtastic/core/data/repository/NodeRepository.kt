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

package org.meshtastic.core.data.repository

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.MeshProtos
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.dao.NodeInfoDao
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.NodeSortOption
import org.meshtastic.core.di.annotation.IoDispatcher
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.onlineTimeThreshold
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.map

@Singleton
@Suppress("TooManyFunctions")
class NodeRepository
@Inject
constructor(
    processLifecycle: Lifecycle,
    private val nodeInfoDao: NodeInfoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?> =
        nodeInfoDao
            .getMyNodeInfo()
            .flowOn(ioDispatcher)
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, null)

    // our node info
    private val _ourNodeInfo = MutableStateFlow<Node?>(null)
    val ourNodeInfo: StateFlow<Node?>
        get() = _ourNodeInfo

    // The unique userId of our node
    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?>
        get() = _myId

    fun getNodeDBbyNum() = nodeInfoDao.nodeDBbyNum().map { map -> map.mapValues { (_, it) -> it.toEntity() } }

    // A map from nodeNum to Node
    val nodeDBbyNum: StateFlow<Map<Int, Node>> =
        nodeInfoDao
            .nodeDBbyNum()
            .mapLatest { map -> map.mapValues { (_, it) -> it.toModel() } }
            .onEach {
                val ourNodeInfo = it.values.firstOrNull()
                _ourNodeInfo.value = ourNodeInfo
                _myId.value = ourNodeInfo?.user?.id
            }
            .flowOn(ioDispatcher)
            .conflate()
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptyMap())

    fun getNode(userId: String): Node = nodeDBbyNum.value.values.find { it.user.id == userId }
        ?: Node(num = DataPacket.idToDefaultNodeNum(userId) ?: 0, user = getUser(userId))

    fun getUser(nodeNum: Int): MeshProtos.User = getUser(DataPacket.nodeNumToDefaultId(nodeNum))

    fun getUser(userId: String): MeshProtos.User = nodeDBbyNum.value.values.find { it.user.id == userId }?.user
        ?: MeshProtos.User.newBuilder()
            .setId(userId)
            .setLongName("Meshtastic ${userId.takeLast(n = 4)}")
            .setShortName(userId.takeLast(n = 4))
            .setHwModel(MeshProtos.HardwareModel.UNSET)
            .build()

    fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
        onlyOnline: Boolean = false,
        onlyDirect: Boolean = false,
    ) = nodeInfoDao
        .getNodes(
            sort = sort.sqlValue,
            filter = filter,
            includeUnknown = includeUnknown,
            hopsAwayMax = if (onlyDirect) 0 else -1,
            lastHeardMin = if (onlyOnline) onlineTimeThreshold() else -1,
        )
        .mapLatest { list -> list.map { it.toModel() } }
        .flowOn(ioDispatcher)
        .conflate()

    suspend fun upsert(node: NodeEntity) = withContext(ioDispatcher) { nodeInfoDao.upsert(node) }

    suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>) =
        withContext(ioDispatcher) { nodeInfoDao.installConfig(mi, nodes) }

    suspend fun clearNodeDB() = withContext(ioDispatcher) { nodeInfoDao.clearNodeInfo() }

    suspend fun deleteNode(num: Int) = withContext(ioDispatcher) {
        nodeInfoDao.deleteNode(num)
        nodeInfoDao.deleteMetadata(num)
    }

    suspend fun deleteNodes(nodeNums: List<Int>) = withContext(ioDispatcher) {
        nodeInfoDao.deleteNodes(nodeNums)
        nodeNums.forEach { nodeInfoDao.deleteMetadata(it) }
    }

    suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity> =
        withContext(ioDispatcher) { nodeInfoDao.getNodesOlderThan(lastHeard) }

    suspend fun getUnknownNodes(): List<NodeEntity> = withContext(ioDispatcher) { nodeInfoDao.getUnknownNodes() }

    suspend fun insertMetadata(metadata: MetadataEntity) = withContext(ioDispatcher) { nodeInfoDao.upsert(metadata) }

    val onlineNodeCount: Flow<Int> =
        nodeInfoDao
            .nodeDBbyNum()
            .mapLatest { map -> map.values.count { it.node.lastHeard > onlineTimeThreshold() } }
            .flowOn(ioDispatcher)
            .conflate()

    val totalNodeCount: Flow<Int> =
        nodeInfoDao.nodeDBbyNum().mapLatest { map -> map.values.count() }.flowOn(ioDispatcher).conflate()

    suspend fun setNodeNotes(num: Int, notes: String) =
        withContext(ioDispatcher) { nodeInfoDao.setNodeNotes(num, notes) }
}
