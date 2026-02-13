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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.util.isLora
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
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
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

    private val ourNodeNumFlow = nodeRepository.nodeDBbyNum.map { it.keys.firstOrNull() }.distinctUntilChanged()

    val uiState: StateFlow<NodeDetailUiState> =
        activeNodeId
            .flatMapLatest { nodeId ->
                if (nodeId == null) return@flatMapLatest flowOf(NodeDetailUiState())

                val nodeFlow = nodeRepository.nodeDBbyNum.map { it[nodeId] }.distinctUntilChanged()
                val telemetryFlow = meshLogRepository.getTelemetryFrom(nodeId).distinctUntilChanged()
                val packetsFlow = meshLogRepository.getMeshPacketsFrom(nodeId).distinctUntilChanged()
                val posPacketsFlow =
                    meshLogRepository.getMeshPacketsFrom(nodeId, PortNum.POSITION_APP.value).distinctUntilChanged()
                val paxLogsFlow =
                    meshLogRepository.getLogsFrom(nodeId, PortNum.PAXCOUNTER_APP.value).distinctUntilChanged()
                val trReqsFlow =
                    meshLogRepository
                        .getLogsFrom(nodeNum = 0, PortNum.TRACEROUTE_APP.value)
                        .map { logs ->
                            logs.filter { log ->
                                val pkt = log.fromRadio.packet
                                val decoded = pkt?.decoded
                                pkt != null &&
                                    decoded != null &&
                                    decoded.want_response == true &&
                                    pkt.from == 0 &&
                                    pkt.to == nodeId
                            }
                        }
                        .distinctUntilChanged()
                val trResFlow =
                    meshLogRepository.getLogsFrom(nodeId, PortNum.TRACEROUTE_APP.value).distinctUntilChanged()
                val niReqsFlow =
                    meshLogRepository
                        .getLogsFrom(nodeNum = 0, PortNum.NEIGHBORINFO_APP.value)
                        .map { logs ->
                            logs.filter { log ->
                                val pkt = log.fromRadio.packet
                                val decoded = pkt?.decoded
                                pkt != null &&
                                    decoded != null &&
                                    decoded.want_response == true &&
                                    pkt.from == 0 &&
                                    pkt.to == nodeId
                            }
                        }
                        .distinctUntilChanged()
                val niResFlow =
                    meshLogRepository.getLogsFrom(nodeId, PortNum.NEIGHBORINFO_APP.value).distinctUntilChanged()

                combine(
                    nodeRepository.ourNodeInfo,
                    ourNodeNumFlow,
                    nodeFlow,
                    nodeRepository.myNodeInfo,
                    radioConfigRepository.deviceProfileFlow,
                    telemetryFlow,
                    packetsFlow,
                    posPacketsFlow,
                    paxLogsFlow,
                    trReqsFlow,
                    trResFlow,
                    niReqsFlow,
                    niResFlow,
                    meshLogRepository.getMyNodeInfo().map { it?.firmware_edition }.distinctUntilChanged(),
                    firmwareReleaseRepository.stableRelease,
                    firmwareReleaseRepository.alphaRelease,
                    nodeRequestActions.lastTracerouteTimes,
                    nodeRequestActions.lastRequestNeighborTimes,
                ) { args ->
                    @Suppress("UNCHECKED_CAST")
                    NodeDetailUiStateData(
                        nodeId = nodeId,
                        actualNode = (args[2] as Node?) ?: createFallbackNode(nodeId),
                        ourNode = args[0] as Node?,
                        ourNodeNum = args[1] as Int?,
                        myInfo = (args[3] as MyNodeEntity?)?.toMyNodeInfo(),
                        profile = args[4] as org.meshtastic.proto.DeviceProfile,
                        telemetry = args[5] as List<Telemetry>,
                        packets = args[6] as List<MeshPacket>,
                        positionPackets = args[7] as List<MeshPacket>,
                        paxLogs = args[8] as List<MeshLog>,
                        tracerouteRequests = args[9] as List<MeshLog>,
                        tracerouteResults = args[10] as List<MeshLog>,
                        neighborInfoRequests = args[11] as List<MeshLog>,
                        neighborInfoResults = args[12] as List<MeshLog>,
                        firmwareEditionArg = args[13] as? FirmwareEdition,
                        stable = args[14] as FirmwareRelease?,
                        alpha = args[15] as FirmwareRelease?,
                        lastTracerouteTime = (args[16] as Map<Int, Long>)[nodeId],
                        lastRequestNeighborsTime = (args[17] as Map<Int, Long>)[nodeId],
                    )
                }
                    .flatMapLatest { data ->
                        val pioEnv = if (data.nodeId == data.ourNodeNum) data.myInfo?.pioEnv else null
                        val hwModel = data.actualNode.user.hw_model?.value ?: 0
                        flow {
                            val hw = deviceHardwareRepository.getDeviceHardwareByModel(hwModel, pioEnv).getOrNull()

                            val moduleConfig = data.profile.module_config
                            val displayUnits = data.profile.config?.display?.units

                            val metricsState =
                                MetricsState(
                                    node = data.actualNode,
                                    isLocal = data.nodeId == data.ourNodeNum,
                                    deviceHardware = hw,
                                    reportedTarget = pioEnv,
                                    isManaged = data.profile.config?.security?.is_managed ?: false,
                                    isFahrenheit =
                                    moduleConfig?.telemetry?.environment_display_fahrenheit == true ||
                                        (displayUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL),
                                    displayUnits = displayUnits ?: Config.DisplayConfig.DisplayUnits.METRIC,
                                    deviceMetrics = data.telemetry.filter { it.device_metrics != null },
                                    powerMetrics = data.telemetry.filter { it.power_metrics != null },
                                    hostMetrics = data.telemetry.filter { it.host_metrics != null },
                                    signalMetrics =
                                    data.packets.filter { pkt ->
                                        (pkt.rx_time ?: 0) > 0 &&
                                            pkt.hop_start == pkt.hop_limit &&
                                            pkt.via_mqtt != true &&
                                            pkt.isLora()
                                    },
                                    positionLogs = data.positionPackets.mapNotNull { it.toPosition() },
                                    paxMetrics = data.paxLogs,
                                    tracerouteRequests = data.tracerouteRequests,
                                    tracerouteResults = data.tracerouteResults,
                                    neighborInfoRequests = data.neighborInfoRequests,
                                    neighborInfoResults = data.neighborInfoResults,
                                    firmwareEdition = data.firmwareEditionArg,
                                    latestStableFirmware = data.stable ?: FirmwareRelease(),
                                    latestAlphaFirmware = data.alpha ?: FirmwareRelease(),
                                )

                            val environmentState =
                                EnvironmentMetricsState(
                                    environmentMetrics =
                                    data.telemetry.filter {
                                        val em = it.environment_metrics
                                        em != null &&
                                            em.relative_humidity != null &&
                                            em.temperature != null &&
                                            em.temperature!!.isNaN().not()
                                    },
                                )

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

                            emit(
                                NodeDetailUiState(
                                    node = metricsState.node,
                                    ourNode = data.ourNode,
                                    metricsState = metricsState,
                                    environmentState = environmentState,
                                    availableLogs = availableLogs,
                                    lastTracerouteTime = data.lastTracerouteTime,
                                    lastRequestNeighborsTime = data.lastRequestNeighborsTime,
                                ),
                            )
                        }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NodeDetailUiState())

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

    @Suppress("MagicNumber")
    private suspend fun createFallbackNode(nodeNum: Int): Node {
        val userId = DataPacket.nodeNumToDefaultId(nodeNum)
        val safeUserId = userId.padStart(4, '0').takeLast(4)
        val longName = "${getString(Res.string.fallback_node_name)}_$safeUserId"
        val defaultUser =
            User(id = userId, long_name = longName, short_name = safeUserId, hw_model = HardwareModel.UNSET)
        return Node(num = nodeNum, user = defaultUser)
    }
}

private data class NodeDetailUiStateData(
    val nodeId: Int,
    val actualNode: Node,
    val ourNode: Node?,
    val ourNodeNum: Int?,
    val myInfo: MyNodeInfo?,
    val profile: org.meshtastic.proto.DeviceProfile,
    val telemetry: List<Telemetry>,
    val packets: List<MeshPacket>,
    val positionPackets: List<MeshPacket>,
    val paxLogs: List<MeshLog>,
    val tracerouteRequests: List<MeshLog>,
    val tracerouteResults: List<MeshLog>,
    val neighborInfoRequests: List<MeshLog>,
    val neighborInfoResults: List<MeshLog>,
    val firmwareEditionArg: FirmwareEdition?,
    val stable: FirmwareRelease?,
    val alpha: FirmwareRelease?,
    val lastTracerouteTime: Long?,
    val lastRequestNeighborsTime: Long?,
)
