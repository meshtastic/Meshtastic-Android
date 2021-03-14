package com.geeksville.mesh.model

import androidx.lifecycle.MutableLiveData
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position


/// NodeDB lives inside the UIViewModel, but it needs a backpointer to reach the service
class NodeDB(private val ui: UIViewModel) {
    private val testPositions = arrayOf(
        Position(32.776665, -96.796989, 35, 123, 40), // dallas
        Position(32.960758, -96.733521, 35, 456, 50), // richardson
        Position(
            32.912901,
            -96.781776,
            35,
            789,
            60
        ) // north dallas
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

    val testNodes = testPositions.mapIndexed { index, it ->
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
    val myId = object : MutableLiveData<String?>(if (seedWithTestNodes) "+16508765309" else null) {}

    /// A map from nodeid to to nodeinfo
    val nodes =
        object :
            MutableLiveData<Map<String, NodeInfo>>(mapOf(*(if (seedWithTestNodes) testNodes else listOf()).map { it.user!!.id to it }
                .toTypedArray())) {}

    /// Could be null if we haven't received our node DB yet
    val ourNodeInfo get() = nodes.value?.get(myId.value)
}