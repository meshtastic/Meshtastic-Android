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

package com.geeksville.mesh.database

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.entity.MetadataEntity
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.NodeSortOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRepository @Inject constructor(
    processLifecycle: Lifecycle,
    private val nodeInfoDao: NodeInfoDao,
    private val dispatchers: CoroutineDispatchers,
) {
    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?> = nodeInfoDao.getMyNodeInfo()
        .flowOn(dispatchers.io)
        .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, null)

    // our node info
    private val _ourNodeInfo = MutableStateFlow<Node?>(null)
    val ourNodeInfo: StateFlow<Node?> get() = _ourNodeInfo

    // The unique userId of our node
    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?> get() = _myId

    fun getNodeDBbyNum() = nodeInfoDao.nodeDBbyNum()
        .map { map -> map.mapValues { (_, it) -> it.toEntity() } }

    // A map from nodeNum to Node
    @OptIn(ExperimentalCoroutinesApi::class)
    val nodeDBbyNum: StateFlow<Map<Int, Node>> = nodeInfoDao.nodeDBbyNum()
        .mapLatest { map -> map.mapValues { (_, it) -> it.toModel() } }
        .onEach {
            val ourNodeInfo = it.values.firstOrNull()
            _ourNodeInfo.value = ourNodeInfo
            _myId.value = ourNodeInfo?.user?.id
        }
        .flowOn(dispatchers.io)
        .conflate()
        .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptyMap())

    fun getNode(userId: String): Node = nodeDBbyNum.value.values.find { it.user.id == userId }
        ?: Node(
            num = DataPacket.idToDefaultNodeNum(userId) ?: 0,
            user = getUser(userId),
        )

    fun getUser(nodeNum: Int): MeshProtos.User = getUser(DataPacket.nodeNumToDefaultId(nodeNum))

    fun getUser(userId: String): MeshProtos.User =
        nodeDBbyNum.value.values.find { it.user.id == userId }?.user
            ?: MeshProtos.User.newBuilder()
                .setId(userId)
                .setLongName("Meshtastic ${userId.takeLast(n = 4)}")
                .setShortName(userId.takeLast(n = 4))
                .setHwModel(MeshProtos.HardwareModel.UNSET)
                .build()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
    ) = nodeInfoDao.getNodes(
        sort = sort.sqlValue,
        filter = filter,
        includeUnknown = includeUnknown,
    ).mapLatest { list -> list.map { it.toModel() } }.flowOn(dispatchers.io).conflate()

    suspend fun upsert(node: NodeEntity) = withContext(dispatchers.io) {
        nodeInfoDao.upsert(node)
    }

    suspend fun installNodeDB(mi: MyNodeEntity, nodes: List<NodeEntity>) = withContext(dispatchers.io) {
        nodeInfoDao.clearMyNodeInfo()
        nodeInfoDao.setMyNodeInfo(mi) // set MyNodeEntity first
        nodeInfoDao.clearNodeInfo()
        nodeInfoDao.putAll(nodes)
    }

    suspend fun deleteNode(num: Int) = withContext(dispatchers.io) {
        nodeInfoDao.deleteNode(num)
        nodeInfoDao.deleteMetadata(num)
    }

    suspend fun insertMetadata(metadata: MetadataEntity) = withContext(dispatchers.io) {
        nodeInfoDao.upsert(metadata)
    }
}
