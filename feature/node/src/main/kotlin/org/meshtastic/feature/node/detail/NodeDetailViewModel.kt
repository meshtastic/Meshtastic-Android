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
import com.meshtastic.core.strings.getString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.util.hasValidEnvironmentMetrics
import org.meshtastic.core.model.util.isDirectSignal
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.fallback_node_name
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.metrics.EnvironmentMetricsState
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
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
        runCatching { savedStateHandle.toRoute<NodesRoutes.NodeDetail>().destNum }
            .getOrElse { runCatching { savedStateHandle.toRoute<NodesRoutes.NodeDetailGraph>().destNum }.getOrNull() }

    private val manualNodeId = MutableStateFlow<Int?>(null)
    private val activeNodeId =
        combine(MutableStateFlow(nodeIdFromRoute), manualNodeId) { fromRoute, manual -> manual ?: fromRoute }
            .distinctUntilChanged()

    val uiState: StateFlow<NodeDetailUiState> =
        activeNodeId
            .flatMapLatest { nodeId ->
                if (nodeId == null) return@flatMapLatest flowOf(NodeDetailUiState())
                buildUiStateFlow(nodeId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NodeDetailUiState())

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun buildUiStateFlow(nodeId: Int): Flow<NodeDetailUiState> {
        val logNodeIdFlow = nodeRepository.effectiveLogNodeId(nodeId)
        val nodeFlow =
            nodeRepository.nodeDBbyNum
                .map { it[nodeId] ?: Node.createFallback(nodeId, getString(Res.string.fallback_node_name)) }
                .distinctUntilChanged()

        // 1. Logs & Metrics Data
        val metricsLogsFlow =
            logNodeIdFlow.flatMapLatest { logId ->
                val telemetry = meshLogRepository.getTelemetryFrom(logId)
                val packets = meshLogRepository.getMeshPacketsFrom(logId)
                val posPackets = meshLogRepository.getMeshPacketsFrom(logId, PortNum.POSITION_APP.value)
                val pax = meshLogRepository.getLogsFrom(logId, PortNum.PAXCOUNTER_APP.value)
                val trRes = meshLogRepository.getLogsFrom(logId, PortNum.TRACEROUTE_APP.value)
                val niRes = meshLogRepository.getLogsFrom(logId, PortNum.NEIGHBORINFO_APP.value)

                @Suppress("UNCHECKED_CAST")
                combine(telemetry, packets, posPackets, pax, trRes, niRes) { args: Array<Any?> ->
                    LogsGroup(
                        telemetry = args[0] as List<Telemetry>,
                        packets = args[1] as List<MeshPacket>,
                        posPackets = args[2] as List<MeshPacket>,
                        pax = args[3] as List<MeshLog>,
                        trRes = args[4] as List<MeshLog>,
                        niRes = args[5] as List<MeshLog>,
                    )
                }
            }

        // 2. Identity & Config
        val identityFlow =
            combine(nodeRepository.ourNodeInfo, nodeRepository.myNodeInfo, radioConfigRepository.deviceProfileFlow) {
                    ourNode,
                    myInfo,
                    profile,
                ->
                IdentityGroup(ourNode, myInfo?.toMyNodeInfo(), profile)
            }

        // 3. Metadata & Times
        val metadataFlow =
            combine(
                meshLogRepository.getMyNodeInfo().map { it?.firmware_edition }.distinctUntilChanged(),
                firmwareReleaseRepository.stableRelease,
                firmwareReleaseRepository.alphaRelease,
                nodeRequestActions.lastTracerouteTimes.map { it[nodeId] },
                nodeRequestActions.lastRequestNeighborTimes.map { it[nodeId] },
            ) { edition, stable, alpha, trTime, niTime ->
                MetadataGroup(edition, stable, alpha, trTime, niTime)
            }

        // 4. Requests (History)
        val requestsFlow =
            combine(
                meshLogRepository.getRequestLogs(nodeId, PortNum.TRACEROUTE_APP),
                meshLogRepository.getRequestLogs(nodeId, PortNum.NEIGHBORINFO_APP),
            ) { trReqs, niReqs ->
                trReqs to niReqs
            }

        return combine(nodeFlow, metricsLogsFlow, identityFlow, metadataFlow, requestsFlow) {
                node,
                logs,
                identity,
                metadata,
                requests,
            ->
            val (trReqs, niReqs) = requests
            val isLocal = node.num == identity.ourNode?.num
            val pioEnv = if (isLocal) identity.myInfo?.pioEnv else null
            val hw = deviceHardwareRepository.getDeviceHardwareByModel(node.user.hw_model.value, pioEnv).getOrNull()

            val moduleConfig = identity.profile.module_config
            val displayUnits = identity.profile.config?.display?.units ?: Config.DisplayConfig.DisplayUnits.METRIC

            val metricsState =
                MetricsState(
                    node = node,
                    isLocal = isLocal,
                    deviceHardware = hw,
                    reportedTarget = pioEnv,
                    isManaged = identity.profile.config?.security?.is_managed ?: false,
                    isFahrenheit =
                    moduleConfig?.telemetry?.environment_display_fahrenheit == true ||
                        (displayUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL),
                    displayUnits = displayUnits,
                    deviceMetrics = logs.telemetry.filter { it.device_metrics != null },
                    powerMetrics = logs.telemetry.filter { it.power_metrics != null },
                    hostMetrics = logs.telemetry.filter { it.host_metrics != null },
                    signalMetrics = logs.packets.filter { it.isDirectSignal() },
                    positionLogs = logs.posPackets.mapNotNull { it.toPosition() },
                    paxMetrics = logs.pax,
                    tracerouteRequests = trReqs,
                    tracerouteResults = logs.trRes,
                    neighborInfoRequests = niReqs,
                    neighborInfoResults = logs.niRes,
                    firmwareEdition = metadata.edition,
                    latestStableFirmware = metadata.stable ?: FirmwareRelease(),
                    latestAlphaFirmware = metadata.alpha ?: FirmwareRelease(),
                )

            val environmentState =
                EnvironmentMetricsState(environmentMetrics = logs.telemetry.filter { it.hasValidEnvironmentMetrics() })

            val availableLogs = buildSet {
                if (metricsState.hasDeviceMetrics()) add(LogsType.DEVICE)
                if (metricsState.hasPositionLogs()) {
                    add(LogsType.NODE_MAP)
                    add(LogsType.POSITIONS)
                }
                if (environmentState.hasEnvironmentMetrics()) add(LogsType.ENVIRONMENT)
                if (metricsState.hasSignalMetrics()) add(LogsType.SIGNAL)
                if (metricsState.hasPowerMetrics()) add(LogsType.POWER)
                if (metricsState.hasTracerouteLogs()) add(LogsType.TRACEROUTE)
                if (metricsState.hasNeighborInfoLogs()) add(LogsType.NEIGHBOR_INFO)
                if (metricsState.hasHostMetrics()) add(LogsType.HOST)
                if (metricsState.hasPaxMetrics()) add(LogsType.PAX)
            }

            NodeDetailUiState(
                node = node,
                ourNode = identity.ourNode,
                metricsState = metricsState,
                environmentState = environmentState,
                availableLogs = availableLogs,
                lastTracerouteTime = metadata.trTime,
                lastRequestNeighborsTime = metadata.niTime,
            )
        }
    }

    private data class LogsGroup(
        val telemetry: List<Telemetry>,
        val packets: List<MeshPacket>,
        val posPackets: List<MeshPacket>,
        val pax: List<MeshLog>,
        val trRes: List<MeshLog>,
        val niRes: List<MeshLog>,
    )

    private data class IdentityGroup(val ourNode: Node?, val myInfo: MyNodeInfo?, val profile: DeviceProfile)

    private data class MetadataGroup(
        val edition: FirmwareEdition?,
        val stable: FirmwareRelease?,
        val alpha: FirmwareRelease?,
        val trTime: Long?,
        val niTime: Long?,
    )

    val effects: SharedFlow<NodeRequestEffect> = nodeRequestActions.effects

    fun start(nodeId: Int) {
        if (manualNodeId.value != nodeId) {
            manualNodeId.value = nodeId
        }
    }

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

    fun getDirectMessageRoute(node: Node, ourNode: Node?): String {
        val hasPKC = ourNode?.hasPKC == true
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        return "${channel}${node.user.id}"
    }
}
