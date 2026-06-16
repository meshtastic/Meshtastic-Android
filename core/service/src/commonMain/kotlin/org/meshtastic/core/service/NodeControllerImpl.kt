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

import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeController
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.proto.AdminMessage

/**
 * [NodeController] implementation: favorite, ignore, mute, and remove nodes.
 *
 * Focused collaborator of [RadioControllerImpl]. Favorite/ignore are idempotent (no-op when already in the requested
 * state), mirroring the SDK's `AdminApi.setFavorite`/`setIgnored`.
 */
internal class NodeControllerImpl(
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val packetRepository: Lazy<PacketRepository>,
    private val scope: CoroutineScope,
) : NodeController {

    override suspend fun setFavorite(nodeNum: Int, favorite: Boolean) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        if (node.isFavorite != favorite) {
            commandSender.sendAdmin(myNum) {
                if (favorite) {
                    AdminMessage(set_favorite_node = node.num)
                } else {
                    AdminMessage(remove_favorite_node = node.num)
                }
            }
            nodeManager.updateNode(node.num) { it.copy(isFavorite = favorite) }
        }
    }

    override suspend fun setIgnored(nodeNum: Int, ignored: Boolean) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        if (node.isIgnored != ignored) {
            commandSender.sendAdmin(myNum) {
                if (ignored) AdminMessage(set_ignored_node = node.num) else AdminMessage(remove_ignored_node = node.num)
            }
            nodeManager.updateNode(node.num) { it.copy(isIgnored = ignored) }
            scope.handledLaunch { packetRepository.value.updateFilteredBySender(node.user.id, ignored) }
        }
    }

    override suspend fun toggleMuted(nodeNum: Int) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        commandSender.sendAdmin(myNum) { AdminMessage(toggle_muted_node = node.num) }
        nodeManager.updateNode(node.num) { it.copy(isMuted = !node.isMuted) }
    }

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {
        nodeManager.removeByNodenum(nodeNum)
        val myNum = nodeManager.myNodeNum.value ?: return
        commandSender.sendAdmin(myNum, packetId) { AdminMessage(remove_by_nodenum = nodeNum) }
    }
}
