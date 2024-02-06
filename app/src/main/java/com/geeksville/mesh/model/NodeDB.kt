package com.geeksville.mesh.model

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.database.dao.NodeInfoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val _myNodeInfo = MutableStateFlow<MyNodeInfo?>(null)
    val myNodeInfo: StateFlow<MyNodeInfo?> get() = _myNodeInfo

    // our node info
    private val _ourNodeInfo = MutableStateFlow<NodeInfo?>(null)
    val ourNodeInfo: StateFlow<NodeInfo?> get() = _ourNodeInfo

    // The unique userId of our node
    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?> get() = _myId

    // A map from nodeNum to NodeInfo
    private val _nodeDBbyNum = MutableStateFlow<Map<Int, NodeInfo>>(mapOf())
    val nodeDBbyNum: StateFlow<Map<Int, NodeInfo>> get() = _nodeDBbyNum
    val nodesByNum get() = nodeDBbyNum.value

    // A map from userId to NodeInfo
    private val _nodeDBbyID = MutableStateFlow<Map<String, NodeInfo>>(mapOf())
    val nodeDBbyID: StateFlow<Map<String, NodeInfo>> get() = _nodeDBbyID
    val nodes get() = nodeDBbyID

    init {
        nodeInfoDao.getMyNodeInfo().onEach { _myNodeInfo.value = it }
            .launchIn(processLifecycle.coroutineScope)

        nodeInfoDao.nodeDBbyNum().onEach { _nodeDBbyNum.value = it }
            .launchIn(processLifecycle.coroutineScope)

        nodeInfoDao.nodeDBbyID().onEach { _nodeDBbyID.value = it }
            .launchIn(processLifecycle.coroutineScope)
    }

    fun myNodeInfoFlow(): Flow<MyNodeInfo?> = nodeInfoDao.getMyNodeInfo()
    fun nodeInfoFlow(): Flow<List<NodeInfo>> = nodeInfoDao.getNodes()
    suspend fun upsert(node: NodeInfo) = withContext(Dispatchers.IO) {
        nodeInfoDao.upsert(node)
    }

    suspend fun installNodeDB(mi: MyNodeInfo, nodes: List<NodeInfo>) = withContext(Dispatchers.IO) {
        nodeInfoDao.apply {
            clearNodeInfo()
            clearMyNodeInfo()
            putAll(nodes)
            setMyNodeInfo(mi) // set MyNodeInfo last
        }
        val ourNodeInfo = nodes.find { it.num == mi.myNodeNum }
        _ourNodeInfo.value = ourNodeInfo
        _myId.value = ourNodeInfo?.user?.id
    }
}
