/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeManagementActions
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
    private val radioController: RadioController,
    private val alertManager: AlertManager,
) {
    fun requestRemoveNode(scope: CoroutineScope, node: Node) {
        alertManager.showAlert(
            titleRes = Res.string.remove,
            messageRes = Res.string.remove_node_text,
            onConfirm = { removeNode(scope, node.num) },
        )
    }

    fun removeNode(scope: CoroutineScope, nodeNum: Int) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Removing node '$nodeNum'" }
            val packetId = radioController.getPacketId()
            radioController.removeByNodenum(packetId, nodeNum)
            nodeRepository.deleteNode(nodeNum)
        }
    }

    fun requestIgnoreNode(scope: CoroutineScope, node: Node) {
        scope.launch {
            val message =
                getString(
                    if (node.isIgnored) Res.string.ignore_remove else Res.string.ignore_add,
                    node.user.long_name ?: "",
                )
            alertManager.showAlert(
                titleRes = Res.string.ignore,
                message = message,
                onConfirm = { ignoreNode(scope, node) },
            )
        }
    }

    fun ignoreNode(scope: CoroutineScope, node: Node) {
        scope.launch(Dispatchers.IO) { serviceRepository.onServiceAction(ServiceAction.Ignore(node)) }
    }

    fun requestMuteNode(scope: CoroutineScope, node: Node) {
        scope.launch {
            val message =
                getString(if (node.isMuted) Res.string.mute_remove else Res.string.mute_add, node.user.long_name ?: "")
            alertManager.showAlert(
                titleRes = if (node.isMuted) Res.string.unmute else Res.string.mute_notifications,
                message = message,
                onConfirm = { muteNode(scope, node) },
            )
        }
    }

    fun muteNode(scope: CoroutineScope, node: Node) {
        scope.launch(Dispatchers.IO) { serviceRepository.onServiceAction(ServiceAction.Mute(node)) }
    }

    fun requestFavoriteNode(scope: CoroutineScope, node: Node) {
        scope.launch {
            val message =
                getString(
                    if (node.isFavorite) Res.string.favorite_remove else Res.string.favorite_add,
                    node.user.long_name ?: "",
                )
            alertManager.showAlert(
                titleRes = Res.string.favorite,
                message = message,
                onConfirm = { favoriteNode(scope, node) },
            )
        }
    }

    fun favoriteNode(scope: CoroutineScope, node: Node) {
        scope.launch(Dispatchers.IO) { serviceRepository.onServiceAction(ServiceAction.Favorite(node)) }
    }

    fun setNodeNotes(scope: CoroutineScope, nodeNum: Int, notes: String) {
        scope.launch(Dispatchers.IO) {
            try {
                nodeRepository.setNodeNotes(nodeNum, notes)
            } catch (ex: java.io.IOException) {
                Logger.e { "Set node notes IO error: ${ex.message}" }
            } catch (ex: java.sql.SQLException) {
                Logger.e { "Set node notes SQL error: ${ex.message}" }
            }
        }
    }
}
