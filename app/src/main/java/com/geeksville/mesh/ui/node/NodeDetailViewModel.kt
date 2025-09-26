/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.node

import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Position
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NodeDetailViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    private val _lastTraceRouteTime = MutableStateFlow<Long?>(null)
    val lastTraceRouteTime: StateFlow<Long?> = _lastTraceRouteTime.asStateFlow()

    fun handleNodeMenuAction(action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> removeNode(action.node.num)
            is NodeMenuAction.Ignore -> ignoreNode(action.node)
            is NodeMenuAction.Favorite -> favoriteNode(action.node)
            is NodeMenuAction.RequestUserInfo -> requestUserInfo(action.node.num)
            is NodeMenuAction.RequestPosition -> requestPosition(action.node.num)
            is NodeMenuAction.TraceRoute -> {
                requestTraceroute(action.node.num)
                _lastTraceRouteTime.value = System.currentTimeMillis()
            }

            else -> {}
        }
    }

    fun setNodeNotes(nodeNum: Int, notes: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            nodeRepository.setNodeNotes(nodeNum, notes)
        } catch (ex: java.io.IOException) {
            Timber.e("Set node notes IO error: ${ex.message}")
        } catch (ex: java.sql.SQLException) {
            Timber.e("Set node notes SQL error: ${ex.message}")
        }
    }

    private fun removeNode(nodeNum: Int) = viewModelScope.launch(Dispatchers.IO) {
        Timber.i("Removing node '$nodeNum'")
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return@launch
            serviceRepository.meshService?.removeByNodenum(packetId, nodeNum)
            nodeRepository.deleteNode(nodeNum)
        } catch (ex: RemoteException) {
            Timber.e("Remove node error: ${ex.message}")
        }
    }

    private fun ignoreNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Ignore(node))
        } catch (ex: RemoteException) {
            Timber.e(ex, "Ignore node error")
        }
    }

    private fun favoriteNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Favorite(node))
        } catch (ex: RemoteException) {
            Timber.e(ex, "Favorite node error")
        }
    }

    private fun requestUserInfo(destNum: Int) {
        Timber.i("Requesting UserInfo for '$destNum'")
        try {
            serviceRepository.meshService?.requestUserInfo(destNum)
        } catch (ex: RemoteException) {
            Timber.e("Request NodeInfo error: ${ex.message}")
        }
    }

    private fun requestPosition(destNum: Int, position: Position = Position(0.0, 0.0, 0)) {
        Timber.i("Requesting position for '$destNum'")
        try {
            serviceRepository.meshService?.requestPosition(destNum, position)
        } catch (ex: RemoteException) {
            Timber.e("Request position error: ${ex.message}")
        }
    }

    private fun requestTraceroute(destNum: Int) {
        Timber.i("Requesting traceroute for '$destNum'")
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return
            serviceRepository.meshService?.requestTraceroute(packetId, destNum)
        } catch (ex: RemoteException) {
            Timber.e("Request traceroute error: ${ex.message}")
        }
    }
}
