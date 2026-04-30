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
package org.meshtastic.feature.node.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.hasValidEnvironmentMetrics
import org.meshtastic.core.model.util.isDirectSignal
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FirmwareReleaseRepository
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.fallback_node_name
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.feature.node.detail.NodeDetailUiState
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.metrics.EnvironmentMetricsState
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry

@Single(binds = [GetNodeDetailsUseCase::class])
class CommonGetNodeDetailsUseCase
constructor(
    private val nodeRepository: NodeRepository,
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val nodeRequestActions: NodeRequestActions,
) : GetNodeDetailsUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override operator fun invoke(nodeId: Int): Flow<NodeDetailUiState> =
        nodeRepository.effectiveLogNodeId(nodeId).flatMapLatest { effectiveNodeId ->
            buildFlow(nodeId, effectiveNodeId)
        }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun buildFlow(nodeId: Int, effectiveNodeId: Int): Flow<NodeDetailUiState> {
        val nodeFlow =
            nodeRepository.nodeDBbyNum.map { it[nodeId] ?: Node.createFallback(nodeId, "") }.distinctUntilChanged()

        // 1. Logs & Metrics Data
        val metricsLogsFlow =
            combine(
                meshLogRepository.getTelemetryFrom(effectiveNodeId).onStart { emit(emptyList()) },
                meshLogRepository.getMeshPacketsFrom(effectiveNodeId).onStart { emit(emptyList()) },
                meshLogRepository.getMeshPacketsFrom(effectiveNodeId, PortNum.POSITION_APP.value).onStart {
                    emit(emptyList())
                },
                meshLogRepository.getLogsFrom(effectiveNodeId, PortNum.PAXCOUNTER_APP.value).onStart {
                    emit(emptyList())
                },
                meshLogRepository.getLogsFrom(effectiveNodeId, PortNum.TRACEROUTE_APP.value).onStart {
                    emit(emptyList())
                },
                meshLogRepository.getLogsFrom(effectiveNodeId, PortNum.NEIGHBORINFO_APP.value).onStart {
                    emit(emptyList())
                },
            ) { args: Array<List<Any?>> ->
                @Suppress("UNCHECKED_CAST")
                LogsGroup(
                    telemetry = args[0] as List<Telemetry>,
                    packets = args[1] as List<MeshPacket>,
                    posPackets = args[2] as List<MeshPacket>,
                    pax = args[3] as List<MeshLog>,
                    trRes = args[4] as List<MeshLog>,
                    niRes = args[5] as List<MeshLog>,
                )
            }

        // 2. Identity & Config
        val identityFlow =
            combine(
                nodeRepository.ourNodeInfo,
                nodeRepository.myNodeInfo,
                radioConfigRepository.deviceProfileFlow.onStart { emit(DeviceProfile()) },
            ) { ourNode, myInfo, profile ->
                IdentityGroup(ourNode, myInfo, profile)
            }

        // 3. Metadata & Request Timestamps
        val metadataFlow =
            combine(
                meshLogRepository
                    .getMyNodeInfo()
                    .map { it?.firmware_edition }
                    .distinctUntilChanged()
                    .onStart { emit(null) },
                firmwareReleaseRepository.stableRelease,
                firmwareReleaseRepository.alphaRelease,
                nodeRequestActions.lastTracerouteTime,
                nodeRequestActions.lastRequestNeighborTimes.map { it[nodeId] },
            ) { edition, stable, alpha, trTime, niTime ->
                MetadataGroup(edition = edition, stable = stable, alpha = alpha, trTime = trTime, niTime = niTime)
            }

        // 4. Requests History (we still query request logs by the target nodeId)
        val requestsFlow =
            combine(
                meshLogRepository.getRequestLogs(nodeId, PortNum.TRACEROUTE_APP).onStart { emit(emptyList()) },
                meshLogRepository.getRequestLogs(nodeId, PortNum.NEIGHBORINFO_APP).onStart { emit(emptyList()) },
            ) { trReqs, niReqs ->
                trReqs to niReqs
            }

        // Assemble final UI state
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

            @Suppress("MagicNumber")
            val nodeName =
                node.user.long_name.takeIf { it.isNotBlank() }?.let { UiText.DynamicString(it) }
                    ?: UiText.Resource(Res.string.fallback_node_name, node.user.id.takeLast(4))

            NodeDetailUiState(
                node = node,
                nodeName = nodeName,
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
}
