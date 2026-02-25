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
package com.geeksville.mesh.widget

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
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_utilization
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.channel_utilization
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.local_stats_bad
import org.meshtastic.core.resources.local_stats_diagnostics_prefix
import org.meshtastic.core.resources.local_stats_dropped
import org.meshtastic.core.resources.local_stats_heap
import org.meshtastic.core.resources.local_stats_heap_value
import org.meshtastic.core.resources.local_stats_noise
import org.meshtastic.core.resources.local_stats_relays
import org.meshtastic.core.resources.local_stats_traffic
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.ServiceRepository
import javax.inject.Inject
import javax.inject.Singleton

data class LocalStatsWidgetUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    // Rendering data
    val statusText: String = "",
    val isConnecting: Boolean = false,
    val showContent: Boolean = false,

    // Static Strings (Resolved in provider for Glance stability)
    val appName: String = "",
    val nodesLabel: String = "",
    val uptimeLabel: String = "",

    // Node Identity
    val nodeShortName: String? = null,
    val nodeColors: Pair<Int, Int>? = null,

    // Battery
    val batteryLabel: String = "",
    val batteryValue: String = "",
    val batteryProgress: Float = 0f,

    // Utilization
    val channelUtilizationLabel: String = "",
    val channelUtilizationValue: String = "",
    val channelUtilizationProgress: Float = 0f,
    val airUtilizationLabel: String = "",
    val airUtilizationValue: String = "",
    val airUtilizationProgress: Float = 0f,

    // Packet Stats Lines
    val trafficText: String? = null,
    val relayText: String? = null,
    val diagnosticsText: String? = null,
    val heapFreeBytes: Int = 0,
    val heapTotalBytes: Int = 0,
    val heapValue: String? = null,
    val heapText: String? = null,

    // Footer
    val nodeCountText: String = "",
    val uptimeText: String = "",
)

@Singleton
class LocalStatsWidgetStateProvider
@Inject
constructor(
    nodeRepository: NodeRepository,
    serviceRepository: ServiceRepository,
) {
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
            .sample(2000) // Don't update the widget more than once every 2 seconds
            .map { input ->
                mapToUiState(input.connectionState, input.totalNodes, input.onlineNodes, input.stats, input.localNode)
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LocalStatsWidgetUiState(),
            )

    private data class StateInput(
        val connectionState: ConnectionState,
        val totalNodes: Int,
        val onlineNodes: Int,
        val stats: org.meshtastic.proto.LocalStats?,
        val localNode: Node?,
    )

    @Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
    private suspend fun mapToUiState(
        connectionState: ConnectionState,
        totalNodes: Int,
        onlineNodes: Int,
        stats: org.meshtastic.proto.LocalStats?,
        localNode: Node?,
    ): LocalStatsWidgetUiState {
        val statusText =
            when (connectionState) {
                is ConnectionState.Disconnected -> getStringSuspend(Res.string.disconnected)
                is ConnectionState.Connecting -> getStringSuspend(Res.string.connecting)
                is ConnectionState.DeviceSleep -> getStringSuspend(Res.string.device_sleeping)
                is ConnectionState.Connected -> ""
            }

        val metrics = localNode?.deviceMetrics
        val batteryLevel = metrics?.battery_level ?: 0
        val isPowered = batteryLevel > 100
        val batteryValue = if (isPowered) getStringSuspend(Res.string.powered) else "$batteryLevel%"

        val channelUtil = stats?.channel_utilization ?: metrics?.channel_utilization ?: 0f
        val airUtilTx = stats?.air_util_tx ?: metrics?.air_util_tx ?: 0f

        val diag = mutableListOf<String>()
        stats?.let {
            if (it.noise_floor != 0) {
                diag.add(getStringSuspend(Res.string.local_stats_noise, it.noise_floor))
            }
            if (it.num_packets_rx_bad > 0) {
                diag.add(getStringSuspend(Res.string.local_stats_bad, it.num_packets_rx_bad))
            }
            if (it.num_tx_dropped > 0) {
                diag.add(getStringSuspend(Res.string.local_stats_dropped, it.num_tx_dropped))
            }
        }

        val uptimeSecs = stats?.uptime_seconds?.toLong() ?: metrics?.uptime_seconds?.toLong() ?: 0L

        return LocalStatsWidgetUiState(
            connectionState = connectionState,
            statusText = statusText,
            isConnecting = connectionState is ConnectionState.Connecting,
            showContent = connectionState is ConnectionState.Connected,
            appName = getStringSuspend(Res.string.meshtastic_app_name),
            nodesLabel = getStringSuspend(Res.string.nodes),
            uptimeLabel = getStringSuspend(Res.string.uptime),
            nodeShortName = localNode?.user?.short_name,
            nodeColors = localNode?.colors,
            batteryLabel = getStringSuspend(Res.string.battery),
            batteryValue = batteryValue,
            batteryProgress = (batteryLevel / 100f).coerceIn(0f, 1f),
            channelUtilizationLabel = getStringSuspend(Res.string.channel_utilization),
            channelUtilizationValue = "%.1f%%".format(channelUtil),
            channelUtilizationProgress = (channelUtil / 100f).coerceIn(0f, 1f),
            airUtilizationLabel = getStringSuspend(Res.string.air_utilization),
            airUtilizationValue = "%.1f%%".format(airUtilTx),
            airUtilizationProgress = (airUtilTx / 100f).coerceIn(0f, 1f),
            trafficText =
            stats?.let {
                getStringSuspend(
                    Res.string.local_stats_traffic,
                    it.num_packets_tx,
                    it.num_packets_rx,
                    it.num_rx_dupe,
                )
            },
            relayText =
            stats
                ?.takeIf { it.num_tx_relay > 0 || it.num_tx_relay_canceled > 0 }
                ?.let {
                    getStringSuspend(Res.string.local_stats_relays, it.num_tx_relay, it.num_tx_relay_canceled)
                },
            diagnosticsText =
            if (diag.isNotEmpty()) {
                getStringSuspend(Res.string.local_stats_diagnostics_prefix, diag.joinToString(" | "))
            } else {
                null
            },
            heapFreeBytes = stats?.heap_free_bytes ?: 0,
            heapTotalBytes = stats?.heap_total_bytes ?: 0,
            heapValue =
            stats?.let {
                getStringSuspend(Res.string.local_stats_heap_value, it.heap_free_bytes, it.heap_total_bytes)
            },
            heapText =
            stats?.let { getStringSuspend(Res.string.local_stats_heap, it.heap_free_bytes, it.heap_total_bytes) },
            nodeCountText = "$onlineNodes/$totalNodes",
            uptimeText = formatUptime(uptimeSecs.toInt()),
        )
    }
}
