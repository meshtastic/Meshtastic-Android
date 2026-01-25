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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.meshtastic.core.strings.getString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.fallback_node_name
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.feature.node.metrics.EnvironmentMetricsState
import org.meshtastic.feature.node.metrics.safeNumber
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.ClientOnlyProtos.DeviceProfile
import org.meshtastic.proto.ConfigProtos.Config
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums.PortNum

private const val DEFAULT_ID_SUFFIX_LENGTH = 4

@Composable
@Suppress("LongMethod", "FunctionName")
fun NodeDetailPresenter(
    nodeId: Int?,
    nodeRepository: NodeRepository,
    meshLogRepository: MeshLogRepository,
    radioConfigRepository: RadioConfigRepository,
    deviceHardwareRepository: DeviceHardwareRepository,
    firmwareReleaseRepository: FirmwareReleaseRepository,
    nodeRequestActions: NodeRequestActions,
): NodeDetailUiState {
    if (nodeId == null) return NodeDetailUiState()

    val ourNode by nodeRepository.ourNodeInfo.collectAsState(null)
    val ourNodeNum by remember { nodeRepository.nodeDBbyNum.map { it.keys.firstOrNull() } }.collectAsState(null)

    val specificNode by remember(nodeId) { nodeRepository.nodeDBbyNum.map { it[nodeId] } }.collectAsState(null)

    val myInfo by nodeRepository.myNodeInfo.collectAsState(null)
    val profile by radioConfigRepository.deviceProfileFlow.collectAsState(DeviceProfile.getDefaultInstance())

    val telemetry by remember(nodeId) { meshLogRepository.getTelemetryFrom(nodeId) }.collectAsState(emptyList())
    val packets by remember(nodeId) { meshLogRepository.getMeshPacketsFrom(nodeId) }.collectAsState(emptyList())
    val positionPackets by
        remember(nodeId) { meshLogRepository.getMeshPacketsFrom(nodeId, PortNum.POSITION_APP_VALUE) }
            .collectAsState(emptyList())
    val paxLogs by
        remember(nodeId) { meshLogRepository.getLogsFrom(nodeId, PortNum.PAXCOUNTER_APP_VALUE) }
            .collectAsState(emptyList())

    val tracerouteRequests by
        remember(nodeId) {
            meshLogRepository.getLogsFrom(nodeNum = 0, PortNum.TRACEROUTE_APP_VALUE).map { logs ->
                logs.filter { log ->
                    with(log.fromRadio.packet) { hasDecoded() && decoded.wantResponse && from == 0 && to == nodeId }
                }
            }
        }
            .collectAsState(emptyList())

    val tracerouteResults by
        remember(nodeId) { meshLogRepository.getLogsFrom(nodeId, PortNum.TRACEROUTE_APP_VALUE) }
            .collectAsState(emptyList())

    val firmwareEdition by
        remember { meshLogRepository.getMyNodeInfo().map { it?.firmwareEdition }.distinctUntilChanged() }
            .collectAsState(null)

    val stable by firmwareReleaseRepository.stableRelease.collectAsState(null)
    val alpha by firmwareReleaseRepository.alphaRelease.collectAsState(null)

    val lastTracerouteTime by nodeRequestActions.lastTracerouteTimes.collectAsState(emptyMap())
    val lastRequestNeighborsTime by nodeRequestActions.lastRequestNeighborTimes.collectAsState(emptyMap())

    val fallbackNameString = remember { getString(Res.string.fallback_node_name) }

    val metricsState =
        remember(
            specificNode,
            ourNodeNum,
            myInfo,
            profile,
            telemetry,
            packets,
            positionPackets,
            paxLogs,
            tracerouteRequests,
            tracerouteResults,
            firmwareEdition,
            stable,
            alpha,
            nodeId,
            fallbackNameString, // Dependency for fallback creation
        ) {
            val actualNode = specificNode ?: createFallbackNode(nodeId, fallbackNameString)
            val pioEnv = if (nodeId == ourNodeNum) myInfo?.pioEnv else null

            val moduleConfig = profile.moduleConfig
            val displayUnits = profile.config.display.units

            Triple(actualNode, pioEnv, moduleConfig to displayUnits)
        }

    val (actualNode, pioEnv, configPair) = metricsState
    val (moduleConfig, displayUnits) = configPair

    val deviceHardware by
        produceState<DeviceHardware?>(initialValue = null, key1 = actualNode.user.hwModel, key2 = pioEnv) {
            val hwModel = actualNode.user.hwModel.safeNumber()
            value = deviceHardwareRepository.getDeviceHardwareByModel(hwModel, target = pioEnv).getOrNull()
        }

    val finalMetricsState =
        remember(
            metricsState, // triggers when actualNode or pioEnv or configs change
            deviceHardware,
            telemetry,
            packets,
            positionPackets,
            paxLogs,
            tracerouteRequests,
            tracerouteResults,
            firmwareEdition,
            stable,
            alpha,
        ) {
            MetricsState(
                node = actualNode,
                isLocal = nodeId == ourNodeNum,
                deviceHardware = deviceHardware,
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
                tracerouteRequests = tracerouteRequests,
                tracerouteResults = tracerouteResults,
                firmwareEdition = firmwareEdition,
                latestStableFirmware = stable ?: FirmwareRelease(),
                latestAlphaFirmware = alpha ?: FirmwareRelease(),
            )
        }

    val environmentState =
        remember(telemetry) {
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

    val availableLogs =
        remember(finalMetricsState, environmentState) { getAvailableLogs(finalMetricsState, environmentState) }

    return NodeDetailUiState(
        node = finalMetricsState.node,
        ourNode = ourNode,
        metricsState = finalMetricsState,
        environmentState = environmentState,
        availableLogs = availableLogs,
        lastTracerouteTime = lastTracerouteTime[nodeId],
        lastRequestNeighborsTime = lastRequestNeighborsTime[nodeId],
    )
}

private fun createFallbackNode(nodeNum: Int, fallbackName: String): Node {
    val userId = DataPacket.nodeNumToDefaultId(nodeNum)
    val safeUserId = userId.padStart(DEFAULT_ID_SUFFIX_LENGTH, '0').takeLast(DEFAULT_ID_SUFFIX_LENGTH)
    val longName = "$fallbackName  $safeUserId"
    val defaultUser =
        MeshProtos.User.newBuilder()
            .setId(userId)
            .setLongName(longName)
            .setShortName(safeUserId)
            .setHwModel(MeshProtos.HardwareModel.UNSET)
            .build()
    return Node(num = nodeNum, user = defaultUser)
}

private fun getAvailableLogs(metricsState: MetricsState, envState: EnvironmentMetricsState): Set<LogsType> = buildSet {
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
