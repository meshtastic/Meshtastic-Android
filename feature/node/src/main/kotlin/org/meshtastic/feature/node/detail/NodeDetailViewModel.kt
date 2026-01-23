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

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.metrics.EnvironmentMetricsState
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import javax.inject.Inject

data class NodeDetailUiState(
    val node: Node? = null,
    val ourNode: Node? = null,
    val metricsState: MetricsState = MetricsState.Empty,
    val environmentState: EnvironmentMetricsState = EnvironmentMetricsState(),
    val availableLogs: Set<LogsType> = emptySet(),
    val lastTracerouteTime: Long? = null,
    val lastRequestNeighborsTime: Long? = null,
)

@HiltViewModel
class NodeDetailViewModel
@Inject
@Suppress("LongParameterList")
constructor(
    savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    private val nodeManagementActions: NodeManagementActions,
    private val nodeRequestActions: NodeRequestActions,
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    private val nodeIdFromRoute: Int? =
        runCatching { savedStateHandle.toRoute<NodesRoutes.NodeDetailGraph>().destNum }.getOrNull()

    private val manualNodeId = MutableStateFlow<Int?>(null)
    private val activeNodeId =
        combine(MutableStateFlow(nodeIdFromRoute), manualNodeId) { fromRoute, manual -> fromRoute ?: manual }
            .distinctUntilChanged()

    val uiState: StateFlow<NodeDetailUiState> =
        viewModelScope.launchMolecule(mode = RecompositionMode.Immediate) {
            val nodeId by activeNodeId.collectAsState(null)

            NodeDetailPresenter(
                nodeId = nodeId,
                nodeRepository = nodeRepository,
                meshLogRepository = meshLogRepository,
                radioConfigRepository = radioConfigRepository,
                deviceHardwareRepository = deviceHardwareRepository,
                firmwareReleaseRepository = firmwareReleaseRepository,
                nodeRequestActions = nodeRequestActions,
            )
        }

    val effects: SharedFlow<NodeRequestEffect> = nodeRequestActions.effects

    fun start(nodeId: Int) {
        if (manualNodeId.value != nodeId) {
            manualNodeId.value = nodeId
        }
    }

    fun handleNodeMenuAction(action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> nodeManagementActions.removeNode(viewModelScope, action.node.num)
            is NodeMenuAction.Ignore -> nodeManagementActions.ignoreNode(viewModelScope, action.node)
            is NodeMenuAction.Mute -> nodeManagementActions.muteNode(viewModelScope, action.node)
            is NodeMenuAction.Favorite -> nodeManagementActions.favoriteNode(viewModelScope, action.node)
            is NodeMenuAction.RequestUserInfo ->
                nodeRequestActions.requestUserInfo(viewModelScope, action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestNeighborInfo ->
                nodeRequestActions.requestNeighborInfo(viewModelScope, action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestPosition ->
                nodeRequestActions.requestPosition(viewModelScope, action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestTelemetry ->
                nodeRequestActions.requestTelemetry(
                    viewModelScope,
                    action.node.num,
                    action.node.user.longName,
                    action.type,
                )
            is NodeMenuAction.TraceRoute ->
                nodeRequestActions.requestTraceroute(viewModelScope, action.node.num, action.node.user.longName)
            else -> {}
        }
    }

    fun onServiceAction(action: ServiceAction) = viewModelScope.launch { serviceRepository.onServiceAction(action) }

    fun setNodeNotes(nodeNum: Int, notes: String) {
        nodeManagementActions.setNodeNotes(viewModelScope, nodeNum, notes)
    }

    fun getDirectMessageRoute(node: Node, ourNode: Node?): String {
        val hasPKC = ourNode?.hasPKC == true
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        return "${channel}${node.user.id}"
    }
}
