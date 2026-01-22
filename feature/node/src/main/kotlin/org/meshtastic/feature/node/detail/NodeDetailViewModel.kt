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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.fallback_node_name
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.metrics.EnvironmentMetricsState
import org.meshtastic.feature.node.metrics.safeNumber
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.ClientOnlyProtos.DeviceProfile
import org.meshtastic.proto.ConfigProtos.Config
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums.PortNum
import org.meshtastic.proto.TelemetryProtos.Telemetry
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

private const val DEFAULT_ID_SUFFIX_LENGTH = 4

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

    private val manualNodeId = MutableStateFlow<Int?>(null)
    private val activeNodeId =
        combine(MutableStateFlow(nodeIdFromRoute), manualNodeId) { fromRoute, manual -> fromRoute ?: manual }
            .distinctUntilChanged()

    @Suppress("UNCHECKED_CAST", "MagicNumber")
    private val metricsStateFlow: Flow<MetricsState> =
        activeNodeId.filterNotNull().flatMapLatest { destNum ->
            combine(
                nodeRepository.nodeDBbyNum.map { it[destNum] }.distinctUntilChanged(),
                nodeRepository.myNodeInfo,
                radioConfigRepository.deviceProfileFlow,
                meshLogRepository.getTelemetryFrom(destNum),
                meshLogRepository.getMeshPacketsFrom(destNum),
                meshLogRepository.getMeshPacketsFrom(destNum, PortNum.POSITION_APP_VALUE),
                meshLogRepository.getLogsFrom(destNum, PortNum.PAXCOUNTER_APP_VALUE),
                firmwareReleaseRepository.stableRelease,
                firmwareReleaseRepository.alphaRelease,
            ) { args ->
                val node = args[0] as Node?
                val myInfo = args[1] as MyNodeEntity?
                val profile = args[2] as DeviceProfile
                val telemetry = args[3] as List<Telemetry>
                val packets = args[4] as List<MeshProtos.MeshPacket>
                val positionPackets = args[5] as List<MeshProtos.MeshPacket>
                val paxLogs = args[6] as List<MeshLog>
                val stable = args[7] as FirmwareRelease?
                val alpha = args[8] as FirmwareRelease?

                val ourNodeNum = nodeRepository.nodeDBbyNum.value.keys.firstOrNull()
                val actualNode = node ?: createFallbackNode(destNum)
                val pioEnv = if (destNum == ourNodeNum) myInfo?.pioEnv else null
                val deviceHardware =
                    actualNode.user.hwModel.safeNumber().let {
                        deviceHardwareRepository.getDeviceHardwareByModel(it, target = pioEnv)
                    }

                val moduleConfig = profile.moduleConfig
                val displayUnits = profile.config.display.units

                MetricsState(
                    node = actualNode,
                    isLocal = destNum == ourNodeNum,
                    deviceHardware = deviceHardware.getOrNull(),
                    reportedTarget = pioEnv,
                    isManaged = profile.config.security.isManaged,
                    isFahrenheit =
                    moduleConfig.telemetry.environmentDisplayFahrenheit ||
                        (displayUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL),
                    displayUnits = displayUnits,
                    deviceMetrics = telemetry.filter { it.hasDeviceMetrics() },
                    powerMetrics = telemetry.filter { it.hasPowerMetrics() },
                    hostMetrics = telemetry.filter { it.hasHostMetrics() },
                    signalMetrics = packets.filter { it.rxTime > 0 },
                    positionLogs = positionPackets.mapNotNull { it.toPosition() },
                    paxMetrics = paxLogs,
                    latestStableFirmware = stable ?: FirmwareRelease(),
                    latestAlphaFirmware = alpha ?: FirmwareRelease(),
                )
            }
        }

    private val environmentStateFlow: Flow<EnvironmentMetricsState> =
        activeNodeId.filterNotNull().flatMapLatest { id ->
            meshLogRepository.getTelemetryFrom(id).map { telemetry ->
                EnvironmentMetricsState(
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

    val uiState: StateFlow<NodeDetailUiState> =
        activeNodeId
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf(NodeDetailUiState())
                } else {
                    combine(
                        nodeRepository.ourNodeInfo,
                        metricsStateFlow,
                        environmentStateFlow,
                        nodeRequestActions.lastTracerouteTimes,
                        nodeRequestActions.lastRequestNeighborTimes,
                    ) { ourNode, metrics, env, tracerouteTimes, neighborTimes ->
                        NodeDetailUiState(
                            node = metrics.node,
                            ourNode = ourNode,
                            metricsState = metrics,
                            environmentState = env,
                            availableLogs = getAvailableLogs(metrics, env),
                            lastTracerouteTime = tracerouteTimes[id],
                            lastRequestNeighborsTime = neighborTimes[id],
                        )
                    }
                }
            }
            .stateInWhileSubscribed(NodeDetailUiState())

    val effects: SharedFlow<NodeRequestEffect> = nodeRequestActions.effects

    fun start(nodeId: Int) {
        if (manualNodeId.value != nodeId) {
            manualNodeId.value = nodeId
        }
    }

    private suspend fun createFallbackNode(nodeNum: Int): Node {
        val userId = DataPacket.nodeNumToDefaultId(nodeNum)
        val safeUserId = userId.padStart(DEFAULT_ID_SUFFIX_LENGTH, '0').takeLast(DEFAULT_ID_SUFFIX_LENGTH)
        val longName = getString(Res.string.fallback_node_name) + "  $safeUserId"
        val defaultUser =
            MeshProtos.User.newBuilder()
                .setId(userId)
                .setLongName(longName)
                .setShortName(safeUserId)
                .setHwModel(MeshProtos.HardwareModel.UNSET)
                .build()
        return Node(num = nodeNum, user = defaultUser)
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
