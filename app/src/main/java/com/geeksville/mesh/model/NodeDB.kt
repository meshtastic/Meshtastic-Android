package com.geeksville.mesh.model

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeDB @Inject constructor(
    processLifecycle: Lifecycle,
    private val nodeInfoDao: NodeInfoDao,
) {
    // hardware info about our local device (can be null)
    private val _myNodeInfo = MutableStateFlow<MyNodeEntity?>(null)
    val myNodeInfo: StateFlow<MyNodeEntity?> get() = _myNodeInfo

    // our node info
    private val _ourNodeInfo = MutableStateFlow<NodeEntity?>(null)
    val ourNodeInfo: StateFlow<NodeEntity?> get() = _ourNodeInfo

    // The unique userId of our node
    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?> get() = _myId

    // A map from nodeNum to NodeEntity
    private val _nodeDBbyNum = MutableStateFlow<Map<Int, NodeEntity>>(mapOf())
    val nodeDBbyNum: StateFlow<Map<Int, NodeEntity>> get() = _nodeDBbyNum

    fun getUser(userId: String?) = userId?.let { id ->
        nodeDBbyNum.value.values.find { it.user.id == id }?.user
    }

    init {
        nodeInfoDao.getMyNodeInfo().onEach { _myNodeInfo.value = it }
            .launchIn(processLifecycle.coroutineScope)

        nodeInfoDao.nodeDBbyNum().onEach {
            _nodeDBbyNum.value = it
            val ourNodeInfo = it.values.firstOrNull()
            _ourNodeInfo.value = ourNodeInfo
            _myId.value = ourNodeInfo?.user?.id
        }.launchIn(processLifecycle.coroutineScope)
    }

    fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
    ) = nodeInfoDao.getNodes(
        sort = sort.sqlValue,
        filter = filter,
        includeUnknown = includeUnknown,
    )

    suspend fun upsert(node: NodeEntity) = withContext(Dispatchers.IO) {
        nodeInfoDao.upsert(node)
    }

    suspend fun installNodeDB(mi: MyNodeEntity, nodes: List<NodeEntity>) = withContext(Dispatchers.IO) {
        nodeInfoDao.clearMyNodeInfo()
        nodeInfoDao.setMyNodeInfo(mi) // set MyNodeEntity first
        nodeInfoDao.clearNodeInfo()
        nodeInfoDao.putAll(nodes)
    }

    suspend fun deleteNode(num: Int) = withContext(Dispatchers.IO) {
        nodeInfoDao.deleteNode(num)
    }
}
