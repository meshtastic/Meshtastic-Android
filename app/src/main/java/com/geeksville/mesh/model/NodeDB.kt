package com.geeksville.mesh.model

import androidx.compose.mutableStateOf
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position

object NodeDB {
    private val testPositions = arrayOf(
        Position(32.776665, -96.796989, 35), // dallas
        Position(32.960758, -96.733521, 35), // richardson
        Position(
            32.912901,
            -96.781776,
            35
        ) // north dallas
    )

    val testNodeNoPosition = NodeInfo(
        8,
        MeshUser(
            "+16508765308".format(8),
            "Kevin MesterNoLoc",
            "KLO"
        ),
        null
    )

    val testNodes = testPositions.mapIndexed { index, it ->
        NodeInfo(
            9 + index,
            MeshUser(
                "+165087653%02d".format(9 + index),
                "Kevin Mester$index",
                "KM$index"
            ),
            it
        )
    }

    /// The unique ID of our node
    val myId = mutableStateOf("+16508765309")

    /// A map from nodeid to to nodeinfo
    val nodes = mutableMapOf(* testNodes.map { it.user!!.id to it }.toTypedArray())

    /// Could be null if we haven't received our node DB yet
    val ourNodeInfo get() = nodes[myId.value]
}