package com.geeksville.mesh.model

import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position
import com.geeksville.mesh.database.dao.MyNodeInfoDao
import com.geeksville.mesh.database.dao.NodeInfoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeDB @Inject constructor(
    private val myNodeInfoDao: MyNodeInfoDao,
    private val nodeInfoDao: NodeInfoDao,
) {

    fun myNodeInfoFlow(): Flow<MyNodeInfo?> = myNodeInfoDao.getMyNodeInfo()
    private suspend fun setMyNodeInfo(myInfo: MyNodeInfo) = withContext(Dispatchers.IO) {
        myNodeInfoDao.setMyNodeInfo(myInfo)
    }

    fun nodeInfoFlow(): Flow<List<NodeInfo>> = nodeInfoDao.getNodes()
    suspend fun upsert(node: NodeInfo) = withContext(Dispatchers.IO) {
        nodeInfoDao.upsert(node)
    }

    suspend fun installNodeDB(mi: MyNodeInfo, nodes: List<NodeInfo>) {
        myNodeInfoDao.clearMyNodeInfo()
        nodeInfoDao.clearNodeInfo()
        nodeInfoDao.putAll(nodes)
        setMyNodeInfo(mi) // set MyNodeInfo last
    }

    private val testPositions = arrayOf(
        Position(32.776665, -96.796989, 35, 123), // dallas
        Position(32.960758, -96.733521, 35, 456), // richardson
        Position(32.912901, -96.781776, 35, 789), // north dallas
    )

    private val testNodeNoPosition = NodeInfo(
        8,
        MeshUser(
            "+16508765308".format(8),
            "Kevin MesterNoLoc",
            "KLO",
            MeshProtos.HardwareModel.ANDROID_SIM,
            false
        ),
        null
    )

    private val testNodes = (listOf(testNodeNoPosition) + testPositions.mapIndexed { index, it ->
        NodeInfo(
            9 + index,
            MeshUser(
                "+165087653%02d".format(9 + index),
                "Kevin Mester$index",
                "KM$index",
                MeshProtos.HardwareModel.ANDROID_SIM,
                false
            ),
            it
        )
    }).associateBy { it.user?.id!! }

    private val seedWithTestNodes = false

    // The unique userId of our node
    private val _myId = MutableStateFlow(if (seedWithTestNodes) "+16508765309" else null)
    val myId: StateFlow<String?> get() = _myId

    fun setMyId(myId: String?) {
        _myId.value = myId
    }

    // A map from nodeNum to NodeInfo
    private val _nodeDBbyNum = MutableStateFlow<Map<Int, NodeInfo>>(mapOf())
    val nodeDBbyNum: StateFlow<Map<Int, NodeInfo>> get() = _nodeDBbyNum
    val nodesByNum get() = nodeDBbyNum.value

    // A map from userId to NodeInfo
    private val _nodes = MutableStateFlow(if (seedWithTestNodes) testNodes else mapOf())
    val nodes: StateFlow<Map<String, NodeInfo>> get() = _nodes

    fun setNodes(nodes: Map<String, NodeInfo>) {
        _nodes.value = nodes
    }

    fun setNodes(list: List<NodeInfo>) {
        setNodes(list.associateBy { it.user?.id!! })
        _nodeDBbyNum.value = list.associateBy { it.num }
    }
}
