package com.geeksville.mesh.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position


/// NodeDB lives inside the UIViewModel, but it needs a backpointer to reach the service
class NodeDB(private val ui: UIViewModel) {
    private val testPositions = arrayOf(
        Position(32.776665, -96.796989, 35, 123), // dallas
        Position(32.960758, -96.733521, 35, 456), // richardson
        Position(32.912901, -96.781776, 35, 789) // north dallas
    )

    val testNodeNoPosition = NodeInfo(
        8,
        MeshUser(
            "+16508765308".format(8),
            "Kevin MesterNoLoc",
            "KLO",
            MeshProtos.HardwareModel.ANDROID_SIM
        ),
        null
    )

    private val testNodes = testPositions.mapIndexed { index, it ->
        NodeInfo(
            9 + index,
            MeshUser(
                "+165087653%02d".format(9 + index),
                "Kevin Mester$index",
                "KM$index",
                MeshProtos.HardwareModel.ANDROID_SIM
            ),
            it
        )
    }

    private val seedWithTestNodes = false

    /// The unique ID of our node
    private val _myId = MutableLiveData<String?>(if (seedWithTestNodes) "+16508765309" else null)
    val myId: LiveData<String?> get() = _myId

    fun setMyId(myId: String?) {
        _myId.value = myId
    }

    /// A map from nodeid to to nodeinfo
    private val _nodes = MutableLiveData<Map<String, NodeInfo>>(mapOf(*(if (seedWithTestNodes) testNodes else listOf()).map { it.user!!.id to it }
                .toTypedArray()))
    val nodes: LiveData<Map<String, NodeInfo>> get() = _nodes

    fun setNodes(nodes: Map<String, NodeInfo>) {
        _nodes.value = nodes
    }

    /// Could be null if we haven't received our node DB yet
    val ourNodeInfo get() = nodes.value?.get(myId.value)
}