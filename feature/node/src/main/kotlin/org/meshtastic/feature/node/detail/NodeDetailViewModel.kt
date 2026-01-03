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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.feature.node.component.NodeMenuAction
import javax.inject.Inject

@HiltViewModel
class NodeDetailViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    private val _lastTraceRouteTime = MutableStateFlow<Long?>(null)
    val lastTraceRouteTime: StateFlow<Long?> = _lastTraceRouteTime.asStateFlow()

    private val _lastRequestNeighborsTime = MutableStateFlow<Long?>(null)
    val lastRequestNeighborsTime: StateFlow<Long?> = _lastRequestNeighborsTime.asStateFlow()

    fun handleNodeMenuAction(action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> removeNode(action.node.num)
            is NodeMenuAction.Ignore -> ignoreNode(action.node)
            is NodeMenuAction.Favorite -> favoriteNode(action.node)
            is NodeMenuAction.RequestUserInfo -> requestUserInfo(action.node.num)
            is NodeMenuAction.RequestNeighborInfo -> {
                requestNeighborInfo(action.node.num)
                _lastRequestNeighborsTime.value = System.currentTimeMillis()
            }
            is NodeMenuAction.RequestPosition -> requestPosition(action.node.num)
            is NodeMenuAction.RequestTelemetry -> requestTelemetry(action.node.num, action.type)
            is NodeMenuAction.TraceRoute -> {
                requestTraceroute(action.node.num)
                _lastTraceRouteTime.value = System.currentTimeMillis()
            }

            else -> {}
        }
    }

    fun setNodeNotes(nodeNum: Int, notes: String) = viewModelScope.launch {
        try {
            nodeRepository.setNodeNotes(nodeNum, notes)
        } catch (ex: java.io.IOException) {
            Logger.e { "Set node notes IO error: ${ex.message}" }
        } catch (ex: java.sql.SQLException) {
            Logger.e { "Set node notes SQL error: ${ex.message}" }
        }
    }

    private fun removeNode(nodeNum: Int) = viewModelScope.launch {
        Logger.i { "Removing node '$nodeNum'" }
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return@launch
            withContext(Dispatchers.IO) {
                serviceRepository.meshService?.removeByNodenum(packetId, nodeNum)
            }
            nodeRepository.deleteNode(nodeNum)
        } catch (ex: RemoteException) {
            Logger.e { "Remove node error: ${ex.message}" }
        }
    }

    private fun ignoreNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Ignore(node))
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Ignore node error" }
        }
    }

    private fun favoriteNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Favorite(node))
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Favorite node error" }
        }
    }

    private fun requestUserInfo(destNum: Int) {
        Logger.i { "Requesting UserInfo for '$destNum'" }
        try {
            serviceRepository.meshService?.requestUserInfo(destNum)
        } catch (ex: RemoteException) {
            Logger.e { "Request NodeInfo error: ${ex.message}" }
        }
    }

    private fun requestNeighborInfo(destNum: Int) {
        Logger.i { "Requesting NeighborInfo for '$destNum'" }
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return
            serviceRepository.meshService?.requestNeighborInfo(packetId, destNum)
        } catch (ex: RemoteException) {
            Logger.e { "Request NeighborInfo error: ${ex.message}" }
        }
    }

    private fun requestPosition(destNum: Int, position: Position = Position(0.0, 0.0, 0)) {
        Logger.i { "Requesting position for '$destNum'" }
        try {
            serviceRepository.meshService?.requestPosition(destNum, position)
        } catch (ex: RemoteException) {
            Logger.e { "Request position error: ${ex.message}" }
        }
    }

    private fun requestTelemetry(destNum: Int, type: TelemetryType) {
        Logger.i { "Requesting telemetry for '$destNum'" }
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return
            serviceRepository.meshService?.requestTelemetry(packetId, destNum, type.ordinal)
        } catch (ex: RemoteException) {
            Logger.e { "Request telemetry error: ${ex.message}" }
        }
    }

    private fun requestTraceroute(destNum: Int) {
        Logger.i { "Requesting traceroute for '$destNum'" }
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return
            serviceRepository.meshService?.requestTraceroute(packetId, destNum)
        } catch (ex: RemoteException) {
            Logger.e { "Request traceroute error: ${ex.message}" }
        }
    }
}
