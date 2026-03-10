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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.UiText
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.metrics.EnvironmentMetricsState
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState

/** UI state for the Node Details screen. */
@androidx.compose.runtime.Stable
data class NodeDetailUiState(
    val node: Node? = null,
    val nodeName: UiText = UiText.DynamicString(""),
    val ourNode: Node? = null,
    val metricsState: MetricsState = MetricsState.Empty,
    val environmentState: EnvironmentMetricsState = EnvironmentMetricsState(),
    val availableLogs: Set<LogsType> = emptySet(),
    val lastTracerouteTime: Long? = null,
    val lastRequestNeighborsTime: Long? = null,
)

/**
 * ViewModel for the Node Details screen, coordinating data from the node database, mesh logs, and radio configuration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class NodeDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val nodeManagementActions: NodeManagementActions,
    private val nodeRequestActions: NodeRequestActions,
    private val serviceRepository: ServiceRepository,
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase,
) : ViewModel() {

    private val nodeIdFromRoute: Int? =
        runCatching { savedStateHandle.toRoute<NodesRoutes.NodeDetail>().destNum }
            .getOrElse { runCatching { savedStateHandle.toRoute<NodesRoutes.NodeDetailGraph>().destNum }.getOrNull() }

    private val manualNodeId = MutableStateFlow<Int?>(null)
    private val activeNodeId =
        combine(MutableStateFlow(nodeIdFromRoute), manualNodeId) { fromRoute, manual -> manual ?: fromRoute }
            .distinctUntilChanged()

    /** Primary UI state stream, combining identity, metrics, and global device metadata. */
    val uiState: StateFlow<NodeDetailUiState> =
        activeNodeId
            .flatMapLatest { nodeId ->
                if (nodeId == null) return@flatMapLatest flowOf(NodeDetailUiState())
                getNodeDetailsUseCase(nodeId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NodeDetailUiState())

    val effects: SharedFlow<NodeRequestEffect> = nodeRequestActions.effects

    fun start(nodeId: Int) {
        if (manualNodeId.value != nodeId) {
            manualNodeId.value = nodeId
        }
    }

    /** Dispatches high-level node management actions like removal, muting, or favoriting. */
    fun handleNodeMenuAction(action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> nodeManagementActions.requestRemoveNode(viewModelScope, action.node)
            is NodeMenuAction.Ignore -> nodeManagementActions.requestIgnoreNode(viewModelScope, action.node)
            is NodeMenuAction.Mute -> nodeManagementActions.requestMuteNode(viewModelScope, action.node)
            is NodeMenuAction.Favorite -> nodeManagementActions.requestFavoriteNode(viewModelScope, action.node)
            is NodeMenuAction.RequestUserInfo ->
                nodeRequestActions.requestUserInfo(viewModelScope, action.node.num, action.node.user.long_name ?: "")
            is NodeMenuAction.RequestNeighborInfo ->
                nodeRequestActions.requestNeighborInfo(
                    viewModelScope,
                    action.node.num,
                    action.node.user.long_name ?: "",
                )
            is NodeMenuAction.RequestPosition ->
                nodeRequestActions.requestPosition(viewModelScope, action.node.num, action.node.user.long_name ?: "")
            is NodeMenuAction.RequestTelemetry ->
                nodeRequestActions.requestTelemetry(
                    viewModelScope,
                    action.node.num,
                    action.node.user.long_name ?: "",
                    action.type,
                )
            is NodeMenuAction.TraceRoute ->
                nodeRequestActions.requestTraceroute(viewModelScope, action.node.num, action.node.user.long_name ?: "")
            else -> {}
        }
    }

    fun onServiceAction(action: ServiceAction) = viewModelScope.launch { serviceRepository.onServiceAction(action) }

    fun setNodeNotes(nodeNum: Int, notes: String) {
        nodeManagementActions.setNodeNotes(viewModelScope, nodeNum, notes)
    }

    /** Returns the type-safe navigation route for a direct message to this node. */
    fun getDirectMessageRoute(node: Node, ourNode: Node?): String {
        val hasPKC = ourNode?.hasPKC == true
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        return "${channel}${node.user.id}"
    }
}
