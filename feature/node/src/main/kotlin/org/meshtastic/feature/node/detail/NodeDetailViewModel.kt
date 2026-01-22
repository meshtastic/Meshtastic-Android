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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.node.component.NodeMenuAction
import javax.inject.Inject

@HiltViewModel
class NodeDetailViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val nodeManagementActions: NodeManagementActions,
    private val nodeRequestActions: NodeRequestActions,
) : ViewModel() {

    fun start(nodeId: Int) {
        nodeManagementActions.start(viewModelScope, nodeId)
        nodeRequestActions.start(viewModelScope)
    }

    init {
        // Initial start if no nodeId provided yet (though Screen calls start(nodeId))
        nodeManagementActions.start(viewModelScope)
        nodeRequestActions.start(viewModelScope)
    }

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val effects: SharedFlow<NodeRequestEffect> = nodeRequestActions.effects

    val lastTraceRouteTime: StateFlow<Long?> =
        nodeRequestActions.lastTracerouteTimes.map { it[nodeManagementActions.nodeId] }.stateInWhileSubscribed(null)

    val lastRequestNeighborsTime: StateFlow<Long?> =
        nodeRequestActions.lastRequestNeighborTimes
            .map { it[nodeManagementActions.nodeId] }
            .stateInWhileSubscribed(null)

    fun handleNodeMenuAction(action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> nodeManagementActions.removeNode(action.node.num)
            is NodeMenuAction.Ignore -> nodeManagementActions.ignoreNode(action.node)
            is NodeMenuAction.Mute -> nodeManagementActions.muteNode(action.node)
            is NodeMenuAction.Favorite -> nodeManagementActions.favoriteNode(action.node)
            is NodeMenuAction.RequestUserInfo ->
                nodeRequestActions.requestUserInfo(action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestNeighborInfo -> {
                nodeRequestActions.requestNeighborInfo(action.node.num, action.node.user.longName)
            }
            is NodeMenuAction.RequestPosition ->
                nodeRequestActions.requestPosition(action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestTelemetry ->
                nodeRequestActions.requestTelemetry(action.node.num, action.node.user.longName, action.type)
            is NodeMenuAction.TraceRoute -> {
                nodeRequestActions.requestTraceroute(action.node.num, action.node.user.longName)
            }
            else -> {}
        }
    }

    fun setNodeNotes(nodeNum: Int, notes: String) {
        nodeManagementActions.setNodeNotes(nodeNum, notes)
    }
}
