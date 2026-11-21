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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeController
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.sdk.NodeId

/**
 * [NodeController] implementation: favorite, ignore, mute, and remove nodes.
 *
 * Focused collaborator of [RadioControllerImpl]. Commands go to the local node through the session's SDK admin API
 * without waiting for its answer, and the local node DB is updated optimistically. Favorite/ignore are idempotent
 * (no-op when already in the requested state).
 */
internal class NodeControllerImpl(
    private val sessionAdmin: SessionAdmin,
    private val nodeManager: NodeManager,
    private val serviceNotifications: MeshNotificationManager,
    private val packetRepository: Lazy<PacketRepository>,
    private val scope: CoroutineScope,
) : NodeController {

    override suspend fun setFavorite(nodeNum: Int, favorite: Boolean) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        if (node.isFavorite != favorite) {
            sessionAdmin.launch("Set favorite") { setFavorite(NodeId(node.num), favorite) }
            nodeManager.updateNode(node.num) { it.copy(isFavorite = favorite) }
            // Only favorites are warned about, so a warning left on an unfavorited node would never clear.
            if (!favorite && node.num != myNum) serviceNotifications.cancelLowBatteryNotification(node.num)
        }
    }

    override suspend fun setIgnored(nodeNum: Int, ignored: Boolean) {
        nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        if (node.isIgnored != ignored) {
            sessionAdmin.launch("Set ignored") { setIgnored(NodeId(node.num), ignored) }
            nodeManager.updateNode(node.num) { it.copy(isIgnored = ignored) }
            scope.handledLaunch { packetRepository.value.updateFilteredBySender(node.user.id, ignored) }
        }
    }

    override suspend fun toggleMuted(nodeNum: Int) {
        nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        sessionAdmin.launch("Toggle muted") { toggleMuted(NodeId(node.num)) }
        nodeManager.updateNode(node.num) { it.copy(isMuted = !node.isMuted) }
    }

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {
        nodeManager.removeByNodenum(nodeNum)
        nodeManager.myNodeNum.value ?: return
        try {
            sessionAdmin.launch("Remove node") { removeNode(NodeId(nodeNum)) }
        } catch (e: PacketQueueRejectedException) {
            // Node removal has always been local-first and is allowed while disconnected; the radio cleanup is
            // best-effort.
            Logger.w(e) { "Remove-node admin command for $nodeNum was not admitted; local removal retained" }
        }
    }
}
