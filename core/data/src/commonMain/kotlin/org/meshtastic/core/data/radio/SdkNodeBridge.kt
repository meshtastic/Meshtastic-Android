/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.NodeChange

internal class SdkNodeBridge(
    private val nodeRepository: NodeRepository,
    private val topologyService: MeshTopologyService,
) {
    fun observe(accessor: RadioClientAccessor, scope: CoroutineScope) {
        accessor.client
            .flatMapLatest { client -> client?.nodes ?: emptyFlow() }
            .onEach(::handleNodeChange)
            .launchIn(scope)

        accessor.client
            .flatMapLatest { client -> client?.ownNode ?: flowOf(null) }
            .onEach { ownNode -> if (ownNode != null) nodeRepository.setMyNodeNum(ownNode.num) }
            .launchIn(scope)

        accessor.client
            .flatMapLatest { client -> client?.packets ?: emptyFlow() }
            .filter { it.decoded?.portnum == PortNum.NODE_STATUS_APP }
            .onEach(::handleNodeStatusPacket)
            .launchIn(scope)
    }

    internal suspend fun handleNodeChange(change: NodeChange) {
        when (change) {
            is NodeChange.Snapshot -> {
                nodeRepository.clear()
                topologyService.clear()
                change.nodes.forEach { (_, nodeInfo) ->
                    nodeRepository.installNodeInfo(nodeInfo, withBroadcast = false)
                }
                nodeRepository.setNodeDbReady(true)
            }

            is NodeChange.Added -> nodeRepository.installNodeInfo(change.node, withBroadcast = true)
            is NodeChange.Updated -> nodeRepository.installNodeInfo(change.node, withBroadcast = true)
            is NodeChange.Removed -> nodeRepository.removeByNodenum(change.nodeId.raw)
            is NodeChange.WentOffline -> handleWentOffline(change)
            is NodeChange.CameOnline -> handleCameOnline(change)
        }
    }

    internal fun handleNodeStatusPacket(packet: MeshPacket) {
        val status = packet.decoded?.payload?.utf8() ?: return
        nodeRepository.updateNode(packet.from) { it.copy(nodeStatus = status) }
    }

    private fun handleWentOffline(change: NodeChange.WentOffline) {
        val nodeNum = change.nodeId.raw
        Logger.d {
            "[SdkBridge] Node ${DataPacket.nodeNumToDefaultId(nodeNum)} went offline (last heard: ${change.lastHeard})"
        }
        if (nodeRepository.nodeDBbyNodeNum.containsKey(nodeNum)) {
            nodeRepository.updateNode(nodeNum) { node ->
                node.copy(lastHeard = minOf(node.lastHeard, change.lastHeard, onlineTimeThreshold()))
            }
        }
    }

    private fun handleCameOnline(change: NodeChange.CameOnline) {
        val nodeNum = change.nodeId.raw
        Logger.d { "[SdkBridge] Node ${DataPacket.nodeNumToDefaultId(nodeNum)} came online" }
        if (nodeRepository.nodeDBbyNodeNum.containsKey(nodeNum)) {
            nodeRepository.updateNode(nodeNum) { node ->
                node.copy(lastHeard = maxOf(node.lastHeard, nowSeconds.toInt()))
            }
        }
    }
}
