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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import org.meshtastic.core.domain.usecase.session.EnsureSessionResult
import org.meshtastic.core.domain.usecase.session.ObserveRemoteAdminSessionStatusUseCase
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.connect_radio_for_remote_admin
import org.meshtastic.core.resources.remote_admin_unreachable
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
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
    val sessionStatus: SessionStatus = SessionStatus.NoSession,
    val isEnsuringSession: Boolean = false,
)

/**
 * ViewModel for the Node Details screen, coordinating data from the node database, mesh logs, and radio configuration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
@Suppress("LongParameterList")
class NodeDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val nodeManagementActions: NodeManagementActions,
    private val nodeRequestActions: NodeRequestActions,
    private val serviceRepository: ServiceRepository,
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase,
    private val ensureRemoteAdminSession: EnsureRemoteAdminSessionUseCase,
    private val observeRemoteAdminSessionStatus: ObserveRemoteAdminSessionStatusUseCase,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    private val nodeIdFromRoute: Int? = savedStateHandle.get<Int>("destNum")

    private val manualNodeId = MutableStateFlow<Int?>(null)
    private val activeNodeId =
        combine(MutableStateFlow(nodeIdFromRoute), manualNodeId) { fromRoute, manual -> manual ?: fromRoute }
            .distinctUntilChanged()

    private val isEnsuringSession = MutableStateFlow(false)

    private val sessionStatusFlow =
        activeNodeId.flatMapLatest { nodeId ->
            if (nodeId == null) flowOf(SessionStatus.NoSession) else observeRemoteAdminSessionStatus(nodeId)
        }

    /** One-shot navigation events from session-bearing actions (e.g. successful remote-admin opens). */
    private val _navigationEvents = Channel<Route>(capacity = Channel.BUFFERED)
    val navigationEvents: Flow<Route> = _navigationEvents.receiveAsFlow()

    /** Primary UI state stream, combining identity, metrics, and global device metadata. */
    val uiState: StateFlow<NodeDetailUiState> =
        activeNodeId
            .flatMapLatest { nodeId ->
                if (nodeId == null) {
                    flowOf(NodeDetailUiState())
                } else {
                    combine(getNodeDetailsUseCase(nodeId), sessionStatusFlow, isEnsuringSession) {
                            base,
                            sessionStatus,
                            ensuring,
                        ->
                        base.copy(sessionStatus = sessionStatus, isEnsuringSession = ensuring)
                    }
                }
            }
            .stateInWhileSubscribed(initialValue = NodeDetailUiState())

    fun start(nodeId: Int) {
        if (manualNodeId.value != nodeId) {
            manualNodeId.value = nodeId
        }
    }

    /** Dispatches high-level node management actions like removal, muting, or favoriting. */
    fun handleNodeMenuAction(action: NodeMenuAction, onAfterRemove: () -> Unit = {}) {
        when (action) {
            is NodeMenuAction.Remove ->
                nodeManagementActions.requestRemoveNode(viewModelScope, action.node, onAfterRemove)

            is NodeMenuAction.Ignore -> nodeManagementActions.requestIgnoreNode(viewModelScope, action.node)

            is NodeMenuAction.Mute -> nodeManagementActions.requestMuteNode(viewModelScope, action.node)

            is NodeMenuAction.Favorite -> nodeManagementActions.requestFavoriteNode(viewModelScope, action.node)

            is NodeMenuAction.RequestUserInfo ->
                nodeRequestActions.requestUserInfo(viewModelScope, action.node.num, action.node.user.long_name)

            is NodeMenuAction.RequestNeighborInfo ->
                nodeRequestActions.requestNeighborInfo(viewModelScope, action.node.num, action.node.user.long_name)

            is NodeMenuAction.RequestPosition ->
                nodeRequestActions.requestPosition(viewModelScope, action.node.num, action.node.user.long_name)

            is NodeMenuAction.RequestTelemetry ->
                nodeRequestActions.requestTelemetry(
                    viewModelScope,
                    action.node.num,
                    action.node.user.long_name,
                    action.type,
                )

            is NodeMenuAction.TraceRoute ->
                nodeRequestActions.requestTraceroute(viewModelScope, action.node.num, action.node.user.long_name)

            else -> {}
        }
    }

    fun onServiceAction(action: ServiceAction) = viewModelScope.launch { serviceRepository.onServiceAction(action) }

    /**
     * Ensure a remote-admin session passkey is fresh, then request navigation to the remote-admin screen. Surfaces a
     * snackbar with the appropriate guidance on [EnsureSessionResult.Disconnected] or [EnsureSessionResult.Timeout].
     */
    fun openRemoteAdmin(destNum: Int) {
        if (isEnsuringSession.value) return
        viewModelScope.launch {
            isEnsuringSession.value = true
            try {
                when (ensureRemoteAdminSession(destNum)) {
                    EnsureSessionResult.AlreadyActive,
                    EnsureSessionResult.Refreshed,
                    -> _navigationEvents.trySend(SettingsRoute.Settings(destNum))

                    EnsureSessionResult.Disconnected ->
                        snackbarManager.showSnackbar(
                            UiText.Resource(Res.string.connect_radio_for_remote_admin).resolve(),
                        )

                    EnsureSessionResult.Timeout ->
                        snackbarManager.showSnackbar(UiText.Resource(Res.string.remote_admin_unreachable).resolve())
                }
            } finally {
                isEnsuringSession.value = false
            }
        }
    }

    /**
     * Re-fetch device metadata (firmware/edition/role) for [destNum]. Refreshes the session passkey as a side effect.
     */
    fun refreshMetadata(destNum: Int) = onServiceAction(ServiceAction.GetDeviceMetadata(destNum))

    fun setNodeNotes(nodeNum: Int, notes: String) {
        nodeManagementActions.setNodeNotes(viewModelScope, nodeNum, notes)
    }

    /** Returns the type-safe navigation route for a direct message to this node. */
    fun getDirectMessageRoute(node: Node, ourNode: Node?): String {
        val hasPKC = ourNode?.hasPKC == true && node.hasPKC
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        return "${channel}${node.user.id}"
    }
}
