package com.geeksville.mesh.util

import com.geeksville.mesh.MeshProtos
import java.util.Locale

fun MeshProtos.MeshPacket.toAnnotatedString(): String {
    return annotateRawMessage(toString(), from, to)
}

fun MeshProtos.NodeInfo.toAnnotatedString(): String {
    return annotateRawMessage(toString(), num)
}

fun MeshProtos.MyNodeInfo.toAnnotatedString(): String {
    return annotateRawMessage(toString(), myNodeNum)
}

/**
 * Annotate the raw message string with the node IDs provided, in hex, if they are present.
 */
private fun annotateRawMessage(rawMessage: String, vararg nodeIds: Int): String {
    val msg = StringBuilder(rawMessage)
    var mutated = false
    nodeIds.forEach { nodeId ->
        mutated = mutated or msg.annotateNodeId(nodeId)
    }
    return if (mutated) {
        return msg.toString()
    } else {
        rawMessage
    }
}

/**
 * Look for a single node ID integer in the string and annotate it with the hex equivalent
 * if found.
 */
private fun StringBuilder.annotateNodeId(nodeId: Int): Boolean {
    val nodeIdStr = nodeId.toUInt().toString()
    indexOf(nodeIdStr).takeIf { it >= 0 }?.let { idx ->
        insert(idx + nodeIdStr.length, " (${nodeId.asNodeId()})")
        return true
    }
    return false
}

private fun Int.asNodeId(): String {
    return "!%08x".format(Locale.getDefault(), this)
}