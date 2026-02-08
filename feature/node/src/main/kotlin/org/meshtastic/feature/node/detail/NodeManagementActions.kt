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

import android.os.RemoteException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.favorite
import org.meshtastic.core.strings.favorite_add
import org.meshtastic.core.strings.favorite_remove
import org.meshtastic.core.strings.ignore
import org.meshtastic.core.strings.ignore_add
import org.meshtastic.core.strings.ignore_remove
import org.meshtastic.core.strings.mute_add
import org.meshtastic.core.strings.mute_notifications
import org.meshtastic.core.strings.mute_remove
import org.meshtastic.core.strings.remove
import org.meshtastic.core.strings.remove_node_text
import org.meshtastic.core.strings.unmute
import org.meshtastic.core.ui.util.AlertManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeManagementActions
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
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
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.removeByNodenum(packetId, nodeNum)
                nodeRepository.deleteNode(nodeNum)
            } catch (ex: RemoteException) {
                Logger.e { "Remove node error: ${ex.message}" }
            }
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
        scope.launch(Dispatchers.IO) {
            try {
                serviceRepository.onServiceAction(ServiceAction.Ignore(node))
            } catch (ex: RemoteException) {
                Logger.e(ex) { "Ignore node error" }
            }
        }
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
        scope.launch(Dispatchers.IO) {
            try {
                serviceRepository.onServiceAction(ServiceAction.Mute(node))
            } catch (ex: RemoteException) {
                Logger.e(ex) { "Mute node error" }
            }
        }
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
        scope.launch(Dispatchers.IO) {
            try {
                serviceRepository.onServiceAction(ServiceAction.Favorite(node))
            } catch (ex: RemoteException) {
                Logger.e(ex) { "Favorite node error" }
            }
        }
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
