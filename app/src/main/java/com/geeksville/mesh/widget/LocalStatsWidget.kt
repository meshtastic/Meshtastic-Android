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

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.geeksville.mesh.service.ConnectionStateHandler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.MyNodeEntity
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
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.local_stats_bad
import org.meshtastic.core.resources.local_stats_diagnostics_prefix
import org.meshtastic.core.resources.local_stats_dropped
import org.meshtastic.core.resources.local_stats_noise
import org.meshtastic.core.resources.local_stats_relays
import org.meshtastic.core.resources.local_stats_traffic
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.service.ConnectionState
import java.util.Locale

class LocalStatsWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocalStatsWidgetEntryPoint {
        fun nodeRepository(): NodeRepository
        fun connectionStateHandler(): ConnectionStateHandler
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, LocalStatsWidgetEntryPoint::class.java)
        val nodeRepository = entryPoint.nodeRepository()
        val connectionStateHandler = entryPoint.connectionStateHandler()

        provideContent {
            val myNodeInfo by nodeRepository.myNodeInfo.collectAsState(null)
            val nodes by nodeRepository.nodeDBbyNum.collectAsState(emptyMap())
            val connectionState by connectionStateHandler.connectionState.collectAsState()

            GlanceTheme { WidgetContent(myNodeInfo, nodes, connectionState) }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    @androidx.compose.runtime.Composable
    private fun WidgetContent(myNodeInfo: MyNodeEntity?, nodes: Map<Int, Node>, connectionState: ConnectionState) {
        val localNode = myNodeInfo?.let { nodes[it.myNodeNum] }
        val telemetry = localNode?.toEntity()?.deviceTelemetry
        val metrics = localNode?.deviceMetrics
        val stats = telemetry?.local_stats

        val batteryLevel = metrics?.battery_level ?: 0
        val isPowered = batteryLevel > 100
        val batteryText = if (isPowered) getString(Res.string.powered) else "$batteryLevel%"

        val channelUtil = stats?.channel_utilization ?: metrics?.channel_utilization ?: 0f
        val airUtilTx = stats?.air_util_tx ?: metrics?.air_util_tx ?: 0f
        val onlineNodes = nodes.values.count { it.lastHeard > onlineTimeThreshold() }
        val totalNodes = nodes.size
        val uptimeSecs = stats?.uptime_seconds ?: metrics?.uptime_seconds ?: 0L

        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Top,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(com.geeksville.mesh.R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp).padding(end = 8.dp),
                )
                Text(
                    text = getString(Res.string.meshtastic_app_name),
                    modifier = GlanceModifier.defaultWeight(),
                    style =
                    TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                localNode?.let { NodeChip(it) }
            }

            Spacer(GlanceModifier.height(8.dp))

            if (connectionState !is ConnectionState.Connected) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val statusText =
                        when (connectionState) {
                            is ConnectionState.Disconnected -> getString(Res.string.disconnected)
                            is ConnectionState.Connecting -> getString(Res.string.connecting)
                            is ConnectionState.DeviceSleep -> getString(Res.string.device_sleeping)
                            is ConnectionState.Connected -> ""
                        }
                    Text(
                        text = statusText,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 16.sp),
                    )
                }
            } else {
                // Battery
                StatRow(
                    label = getString(Res.string.battery),
                    value = batteryText,
                    progress = (batteryLevel / 100f).coerceIn(0f, 1f),
                )

                Spacer(GlanceModifier.height(4.dp))

                // Channel Utilization
                StatRow(
                    label = getString(Res.string.channel_utilization),
                    value = String.format(Locale.ROOT, "%.1f%%", channelUtil),
                    progress = (channelUtil / 100f).coerceIn(0f, 1f),
                )

                Spacer(GlanceModifier.height(4.dp))

                // Air Utilization TX
                StatRow(
                    label = getString(Res.string.air_utilization),
                    value = String.format(Locale.ROOT, "%.1f%%", airUtilTx),
                    progress = (airUtilTx / 100f).coerceIn(0f, 1f),
                )

                Spacer(GlanceModifier.height(8.dp))

                // Traffic & Relay Stats (Condensed)
                if (stats != null) {
                    if (stats.num_packets_tx > 0 || stats.num_packets_rx > 0) {
                        Text(
                            text =
                            getString(
                                Res.string.local_stats_traffic,
                                stats.num_packets_tx,
                                stats.num_packets_rx,
                                stats.num_rx_dupe,
                            ),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }
                    if (stats.num_tx_relay > 0) {
                        Text(
                            text =
                            getString(
                                Res.string.local_stats_relays,
                                stats.num_tx_relay,
                                stats.num_tx_relay_canceled,
                            ),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }

                    // Diagnostics
                    val diag = mutableListOf<String>()
                    if (stats.noise_floor != 0) diag.add(getString(Res.string.local_stats_noise, stats.noise_floor))
                    if (stats.num_packets_rx_bad > 0) {
                        diag.add(getString(Res.string.local_stats_bad, stats.num_packets_rx_bad))
                    }
                    if (stats.num_tx_dropped > 0) {
                        diag.add(getString(Res.string.local_stats_dropped, stats.num_tx_dropped))
                    }

                    if (diag.isNotEmpty()) {
                        Text(
                            text = getString(Res.string.local_stats_diagnostics_prefix, diag.joinToString(" | ")),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }
                    Spacer(GlanceModifier.height(8.dp))
                }

                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "Nodes",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                        )
                        Text(
                            text = "$onlineNodes / $totalNodes",
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                        )
                    }
                    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End) {
                        Text(
                            text = getString(Res.string.uptime),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                        )
                        Text(
                            text = formatUptime(uptimeSecs.toInt()),
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @androidx.compose.runtime.Composable
    private fun NodeChip(node: Node) {
        val (fg, bg) = node.colors
        Row(
            modifier =
            GlanceModifier.background(Color(bg)).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.user.short_name,
                style = TextStyle(color = ColorProvider(Color(fg)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun StatRow(label: String, value: String, progress: Float) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(text = value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp))
            }
            Spacer(GlanceModifier.height(2.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
    }
}
