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
package org.meshtastic.feature.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.LocalStats

data class LocalStatsWidgetUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isConnecting: Boolean = false,
    val showContent: Boolean = false,

    // Node Identity
    val nodeShortName: String? = null,
    val nodeColors: Pair<Int, Int>? = null,

    // Battery
    val batteryLevel: Int = 0,
    val hasBattery: Boolean = false,
    val batteryProgress: Float = 0f,

    // Utilization
    val channelUtilization: Float = 0f,
    val channelUtilizationProgress: Float = 0f,
    val airUtilization: Float = 0f,
    val airUtilizationProgress: Float = 0f,

    // Stats
    val hasStats: Boolean = false,
    val numPacketsTx: Int = 0,
    val numPacketsRx: Int = 0,
    val numRxDupe: Int = 0,
    val numTxRelay: Int = 0,
    val numTxRelayCanceled: Int = 0,
    val noiseFloor: Int = 0,
    val numPacketsRxBad: Int = 0,
    val numTxDropped: Int = 0,
    val heapFreeBytes: Int = 0,
    val heapTotalBytes: Int = 0,

    // Footer
    val totalNodes: Int = 0,
    val onlineNodes: Int = 0,
    val uptimeSecs: Long = 0,
    val updateTimeMillis: Long = 0,
)

@Single
class LocalStatsWidgetStateProvider(nodeRepository: NodeRepository, serviceRepository: ServiceRepository) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val state: StateFlow<LocalStatsWidgetUiState> =
        combine(
            serviceRepository.connectionState,
            nodeRepository.nodeDBbyNum
                .map { nodes ->
                    val online = nodes.values.count { it.lastHeard > onlineTimeThreshold() }
                    nodes.size to online
                }
                .distinctUntilChanged(),
            nodeRepository.localStats,
            nodeRepository.ourNodeInfo,
        ) { connectionState, (totalNodes, onlineNodes), stats, localNode ->
            StateInput(connectionState, totalNodes, onlineNodes, stats, localNode)
        }
            .map { input ->
                mapToUiState(input.connectionState, input.totalNodes, input.onlineNodes, input.stats, input.localNode)
            }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = LocalStatsWidgetUiState())

    private data class StateInput(
        val connectionState: ConnectionState,
        val totalNodes: Int,
        val onlineNodes: Int,
        val stats: LocalStats,
        val localNode: Node?,
    )

    @Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
    private fun mapToUiState(
        connectionState: ConnectionState,
        totalNodes: Int,
        onlineNodes: Int,
        stats: LocalStats,
        localNode: Node?,
    ): LocalStatsWidgetUiState {
        val metrics = localNode?.deviceMetrics
        val batteryLevel = metrics?.battery_level ?: 0

        val hasStats = stats.uptime_seconds != 0
        val channelUtil = if (hasStats) stats.channel_utilization else metrics?.channel_utilization ?: 0f
        val airUtilTx = if (hasStats) stats.air_util_tx else metrics?.air_util_tx ?: 0f
        val uptimeSecs = if (hasStats) stats.uptime_seconds.toLong() else metrics?.uptime_seconds?.toLong() ?: 0L

        return LocalStatsWidgetUiState(
            connectionState = connectionState,
            isConnecting = connectionState is ConnectionState.Connecting,
            showContent = connectionState is ConnectionState.Connected,
            nodeShortName = localNode?.user?.short_name,
            nodeColors = localNode?.colors,
            batteryLevel = batteryLevel,
            hasBattery = metrics?.battery_level != null,
            batteryProgress = (batteryLevel / 100f).coerceIn(0f, 1f),
            channelUtilization = channelUtil,
            channelUtilizationProgress = (channelUtil / 100f).coerceIn(0f, 1f),
            airUtilization = airUtilTx,
            airUtilizationProgress = (airUtilTx / 100f).coerceIn(0f, 1f),
            hasStats = hasStats,
            numPacketsTx = stats.num_packets_tx,
            numPacketsRx = stats.num_packets_rx,
            numRxDupe = stats.num_rx_dupe,
            numTxRelay = stats.num_tx_relay,
            numTxRelayCanceled = stats.num_tx_relay_canceled,
            noiseFloor = stats.noise_floor,
            numPacketsRxBad = stats.num_packets_rx_bad,
            numTxDropped = stats.num_tx_dropped,
            heapFreeBytes = stats.heap_free_bytes,
            heapTotalBytes = stats.heap_total_bytes,
            totalNodes = totalNodes,
            onlineNodes = onlineNodes,
            uptimeSecs = uptimeSecs,
            updateTimeMillis = nowMillis,
        )
    }
}
