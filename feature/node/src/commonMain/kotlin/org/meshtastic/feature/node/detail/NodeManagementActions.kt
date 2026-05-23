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
package org.meshtastic.feature.node.detail

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Single
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.favorite
import org.meshtastic.core.resources.favorite_add
import org.meshtastic.core.resources.favorite_remove
import org.meshtastic.core.resources.ignore
import org.meshtastic.core.resources.ignore_add
import org.meshtastic.core.resources.ignore_remove
import org.meshtastic.core.resources.mute_add
import org.meshtastic.core.resources.mute_notifications
import org.meshtastic.core.resources.mute_remove
import org.meshtastic.core.resources.remove
import org.meshtastic.core.resources.remove_node_text
import org.meshtastic.core.resources.unmute
import org.meshtastic.core.ui.util.AlertManager
import kotlin.coroutines.cancellation.CancellationException

@Single
open class NodeManagementActions
constructor(
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
    private val alertManager: AlertManager,
) {
    open fun requestRemoveNode(scope: CoroutineScope, node: Node, onAfterRemove: () -> Unit = {}) {
        alertManager.showAlert(
            titleRes = Res.string.remove,
            messageRes = Res.string.remove_node_text,
            onConfirm = {
                scope.launch { removeNode(node.num) }
                onAfterRemove()
            },
        )
    }

    open suspend fun removeNode(nodeNum: Int) {
        Logger.i { "Removing node '$nodeNum'" }
        val packetId = radioController.getPacketId()
        radioController.removeByNodenum(packetId, nodeNum)
        nodeRepository.deleteNode(nodeNum)
    }

    open fun requestIgnoreNode(scope: CoroutineScope, node: Node) {
        scope.launch {
            val message =
                getString(if (node.isIgnored) Res.string.ignore_remove else Res.string.ignore_add, node.user.long_name)
            alertManager.showAlert(
                titleRes = Res.string.ignore,
                message = message,
                onConfirm = { scope.launch { ignoreNode(node.num) } },
            )
        }
    }

    open suspend fun ignoreNode(nodeNum: Int) {
        radioController.ignoreNode(nodeNum)
    }

    open fun requestMuteNode(scope: CoroutineScope, node: Node) {
        scope.launch {
            val message =
                getString(if (node.isMuted) Res.string.mute_remove else Res.string.mute_add, node.user.long_name)
            alertManager.showAlert(
                titleRes = if (node.isMuted) Res.string.unmute else Res.string.mute_notifications,
                message = message,
                onConfirm = { scope.launch { muteNode(node.num) } },
            )
        }
    }

    open suspend fun muteNode(nodeNum: Int) {
        radioController.muteNode(nodeNum)
    }

    open fun requestFavoriteNode(scope: CoroutineScope, node: Node) {
        scope.launch {
            val message =
                getString(
                    if (node.isFavorite) Res.string.favorite_remove else Res.string.favorite_add,
                    node.user.long_name,
                )
            alertManager.showAlert(
                titleRes = Res.string.favorite,
                message = message,
                onConfirm = { scope.launch { favoriteNode(node.num) } },
            )
        }
    }

    open suspend fun favoriteNode(nodeNum: Int) {
        radioController.favoriteNode(nodeNum)
    }

    open suspend fun setNodeNotes(nodeNum: Int, notes: String) {
        try {
            nodeRepository.setNodeNotes(nodeNum, notes)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e(ex) { "Set node notes error" }
        }
    }
}
