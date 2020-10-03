package com.geeksville.mesh.service

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.service.MeshService.Companion.NODENUM_BROADCAST

class MeshServiceNodeDatabase(
    // The database of active nodes, index is the node number
    private val nodeInfoByNum: MutableMap<Int, NodeInfo> = mutableMapOf(),
    /// The database of active nodes, index is the node user ID string
    /// NOTE: some NodeInfos might be in only nodeDBbyNodeNum (because we don't yet know
    /// an ID).  But if a NodeInfo is in both maps, it must be one instance shared by
    /// both datastructures.
    private val nodeInfoById: MutableMap<String, NodeInfo> = mutableMapOf()
) {
    val nodes: List<NodeInfo> = nodeInfoById.values.toList()
    val count get() = nodeInfoByNum.size
    val countOnline get() = nodeInfoByNum.values.count { it.isOnline }

    fun putAll(nodes: Array<NodeInfo>) {
        // put our node array into our two different map representations
        nodeInfoByNum.putAll(nodes.map { Pair(it.num, it) })
        nodeInfoById.putAll(nodes.mapNotNull {
            it.user?.let { user -> // ignore records that don't have a valid user
                Pair(
                    user.id,
                    it
                )
            }
        })
    }

    fun clear() {
        nodeInfoByNum.clear()
        nodeInfoById.clear()
    }

    fun findNodeInfoOrNull(userId: String) = nodeInfoById[userId]

    /// Map a user ID to a node/ node num, or throw an exception if not found
    fun findNodeInfo(userId: String) = nodeInfoById[userId] ?: throw MeshService.Companion.IdNotFoundException(userId)

    /// Map a nodenum to a node, or throw an exception if not found
    fun findNodeInfo(nodeNum: Int) = nodeInfoByNum[nodeNum] ?: throw MeshService.Companion.NodeNumNotFoundException(nodeNum)

    /// Map a nodeNum to the  string, or return null if not present or no id found
    fun findNodeIdOrNull(nodeNum: Int) = if (nodeNum == NODENUM_BROADCAST) DataPacket.ID_BROADCAST else nodeInfoByNum[nodeNum]?.user?.id

    /// given a nodenum, return a db entry - creating if necessary
    fun getOrCreateNodeInfo(nodeNum: Int): NodeInfo {
        val nodeInfo = nodeInfoByNum.getOrPut(nodeNum) { NodeInfo(nodeNum) }
        // This might have been the first time we know an ID for this node, so also update the by ID map
        val userId = nodeInfo.user?.id.orEmpty()
        if (userId.isNotEmpty()) {
            nodeInfoById[userId] = nodeInfo
        }
        return nodeInfo
    }
}
