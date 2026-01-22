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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
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
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.metrics.EnvironmentMetricsState
import org.meshtastic.feature.node.metrics.safeNumber
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.ConfigProtos.Config
import org.meshtastic.proto.Portnums.PortNum
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

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val _manualNodeId = MutableStateFlow<Int?>(null)
    private val activeNodeId =
        combine(MutableStateFlow(nodeIdFromRoute), _manualNodeId) { fromRoute, manual ->
            fromRoute ?: manual
        }

    private val _metricsState = MutableStateFlow(MetricsState.Empty)
    private val _environmentState = MutableStateFlow(EnvironmentMetricsState())

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<NodeDetailUiState> =
        combine(
                nodeRepository.ourNodeInfo,
                _metricsState,
                _environmentState,
                nodeRequestActions.lastTracerouteTimes,
                nodeRequestActions.lastRequestNeighborTimes,
                activeNodeId,
            ) { args ->
                val ourNode = args[0] as Node?
                val metrics = args[1] as MetricsState
                val env = args[2] as EnvironmentMetricsState
                val tracerouteTimes = args[3] as Map<Int, Long>
                val neighborTimes = args[4] as Map<Int, Long>
                val id = args[5] as Int?

                NodeDetailUiState(
                    node = metrics.node,
                    ourNode = ourNode,
                    metricsState = metrics,
                    environmentState = env,
                    availableLogs = getAvailableLogs(metrics, env),
                    lastTracerouteTime = id?.let { tracerouteTimes[it] },
                    lastRequestNeighborsTime = id?.let { neighborTimes[it] },
                )
            }
            .stateInWhileSubscribed(NodeDetailUiState())

    val effects: SharedFlow<NodeRequestEffect> = nodeRequestActions.effects

    fun start(nodeId: Int) {
        if (_manualNodeId.value != nodeId) {
            _manualNodeId.value = nodeId
            nodeManagementActions.start(viewModelScope, nodeId)
            nodeRequestActions.start(viewModelScope)
            initializeFlows(nodeId)
        }
    }

    init {
        nodeIdFromRoute?.let {
            nodeManagementActions.start(viewModelScope, it)
            initializeFlows(it)
        }
        nodeRequestActions.start(viewModelScope)
    }

    @Suppress("LongMethod")
    private fun initializeFlows(destNum: Int) {
        viewModelScope.launch {
            launch {
                combine(nodeRepository.nodeDBbyNum, nodeRepository.myNodeInfo) { nodes, myInfo ->
                        nodes[destNum] to (nodes.keys.firstOrNull() to myInfo)
                    }
                    .collect { (node, localData) ->
                        val (ourNodeNum, myInfo) = localData
                        val actualNode = node
                        val pioEnv = if (destNum == ourNodeNum) myInfo?.pioEnv else null
                        val deviceHardware =
                            actualNode?.user?.hwModel?.safeNumber()?.let {
                                deviceHardwareRepository.getDeviceHardwareByModel(it, target = pioEnv)
                            }
                        _metricsState.update { state ->
                            state.copy(
                                node = actualNode,
                                isLocal = destNum == ourNodeNum,
                                deviceHardware = deviceHardware?.getOrNull(),
                                reportedTarget = pioEnv,
                            )
                        }
                    }
            }

            launch {
                radioConfigRepository.deviceProfileFlow.collect { profile ->
                    val moduleConfig = profile.moduleConfig
                    val displayUnits = profile.config.display.units
                    _metricsState.update { state ->
                        state.copy(
                            isManaged = profile.config.security.isManaged,
                            isFahrenheit =
                                moduleConfig.telemetry.environmentDisplayFahrenheit ||
                                    (displayUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL),
                            displayUnits = displayUnits,
                        )
                    }
                }
            }

            launch {
                meshLogRepository.getTelemetryFrom(destNum).collect { telemetry ->
                    _metricsState.update { state ->
                        state.copy(
                            deviceMetrics = telemetry.filter { it.hasDeviceMetrics() },
                            powerMetrics = telemetry.filter { it.hasPowerMetrics() },
                            hostMetrics = telemetry.filter { it.hasHostMetrics() },
                        )
                    }
                    _environmentState.update { state ->
                        state.copy(
                            environmentMetrics =
                                telemetry.filter {
                                    it.hasEnvironmentMetrics() &&
                                        it.environmentMetrics.hasRelativeHumidity() &&
                                        it.environmentMetrics.hasTemperature() &&
                                        !it.environmentMetrics.temperature.isNaN()
                                },
                        )
                    }
                }
            }

            launch {
                meshLogRepository.getMeshPacketsFrom(destNum).collect { meshPackets ->
                    _metricsState.update { state ->
                        state.copy(signalMetrics = meshPackets.filter { it.rxTime > 0 })
                    }
                }
            }

            launch {
                meshLogRepository.getMeshPacketsFrom(destNum, PortNum.POSITION_APP_VALUE).collect { packets ->
                    val distinctPositions = packets.mapNotNull { it.toPosition() }
                    _metricsState.update { state -> state.copy(positionLogs = distinctPositions) }
                }
            }

            launch {
                meshLogRepository.getLogsFrom(destNum, PortNum.PAXCOUNTER_APP_VALUE).collect { logs ->
                    _metricsState.update { state -> state.copy(paxMetrics = logs) }
                }
            }

            launch {
                firmwareReleaseRepository.stableRelease.filterNotNull().collect { latestStable ->
                    _metricsState.update { state -> state.copy(latestStableFirmware = latestStable) }
                }
            }

            launch {
                firmwareReleaseRepository.alphaRelease.filterNotNull().collect { latestAlpha ->
                    _metricsState.update { state -> state.copy(latestAlphaFirmware = latestAlpha) }
                }
            }
        }
    }

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

    fun onServiceAction(action: ServiceAction) =
        viewModelScope.launch { serviceRepository.onServiceAction(action) }

    fun setNodeNotes(nodeNum: Int, notes: String) {
        nodeManagementActions.setNodeNotes(nodeNum, notes)
    }

    fun getDirectMessageRoute(node: Node, ourNode: Node?): String {
        val hasPKC = ourNode?.hasPKC == true
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        return "${channel}${node.user.id}"
    }

    private fun getAvailableLogs(metricsState: MetricsState, envState: EnvironmentMetricsState): Set<LogsType> =
        buildSet {
            if (metricsState.hasDeviceMetrics()) add(LogsType.DEVICE)
            if (metricsState.hasPositionLogs()) {
                add(LogsType.NODE_MAP)
                add(LogsType.POSITIONS)
            }
            if (envState.hasEnvironmentMetrics()) add(LogsType.ENVIRONMENT)
            if (metricsState.hasSignalMetrics()) add(LogsType.SIGNAL)
            if (metricsState.hasPowerMetrics()) add(LogsType.POWER)
            if (metricsState.hasTracerouteLogs()) add(LogsType.TRACEROUTE)
            if (metricsState.hasHostMetrics()) add(LogsType.HOST)
            if (metricsState.hasPaxMetrics()) add(LogsType.PAX)
        }
}
