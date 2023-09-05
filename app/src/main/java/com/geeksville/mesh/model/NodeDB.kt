package com.geeksville.mesh.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position
import kotlinx.coroutines.flow.MutableStateFlow

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
            MeshProtos.HardwareModel.ANDROID_SIM,
            false
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
                MeshProtos.HardwareModel.ANDROID_SIM,
                false
            ),
            it
        )
    }.associateBy { it.user?.id!! }

    private val seedWithTestNodes = false

    // The unique userId of our node
    private val _myId = MutableLiveData<String?>(if (seedWithTestNodes) "+16508765309" else null)
    val myId: LiveData<String?> get() = _myId

    fun setMyId(myId: String?) {
        _myId.value = myId
    }

    // A map from userId to NodeInfo
    private val _nodes = MutableStateFlow(if (seedWithTestNodes) testNodes else mapOf())
    val nodes: LiveData<Map<String, NodeInfo>> = _nodes.asLiveData()
    val nodesByNum get() = nodes.value?.values?.associateBy { it.num }

    fun setNodes(nodes: Map<String, NodeInfo>) {
        _nodes.value = nodes
    }

    fun setNodes(list: List<NodeInfo>) {
        setNodes(list.associateBy { it.user?.id!! })
    }
}
