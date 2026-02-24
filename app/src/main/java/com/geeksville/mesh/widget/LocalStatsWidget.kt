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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
import org.meshtastic.core.service.ConnectionState
import java.util.Locale

class LocalStatsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocalStatsWidgetEntryPoint {
        fun widgetStateProvider(): LocalStatsWidgetStateProvider
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, LocalStatsWidgetEntryPoint::class.java)
        val stateProvider = entryPoint.widgetStateProvider()

        provideContent {
            val state by stateProvider.state.collectAsState()
            GlanceTheme { WidgetContent(state) }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    @Composable
    private fun WidgetContent(state: LocalStatsWidgetUiState) {
        val size = LocalSize.current
        val isCondensedHeight = size.height < 170.dp
        val isVeryCondensedHeight = size.height < 110.dp
        val isCondensedWidth = size.width < 150.dp
        val isVeryCondensedWidth = size.width < 100.dp

        val localNode = state.localNode
        val metrics = localNode?.deviceMetrics

        val batteryLevel = metrics?.battery_level ?: 0
        val isPowered = batteryLevel > 100
        val batteryText = if (isPowered) getString(Res.string.powered) else "$batteryLevel%"

        val channelUtil = state.stats?.channel_utilization ?: metrics?.channel_utilization ?: 0f
        val airUtilTx = state.stats?.air_util_tx ?: metrics?.air_util_tx ?: 0f
        val onlineNodes = state.nodes.values.count { it.lastHeard > onlineTimeThreshold() }
        val totalNodes = state.nodes.size
        val uptimeSecs = state.stats?.uptime_seconds ?: metrics?.uptime_seconds ?: 0L

        Scaffold(
            modifier = GlanceModifier.fillMaxSize(),
            backgroundColor = GlanceTheme.colors.widgetBackground,
            titleBar = {
                TitleBar(
                    startIcon = ImageProvider(com.geeksville.mesh.R.drawable.app_icon),
                    title = if (isCondensedWidth) "" else getString(Res.string.meshtastic_app_name),
                    actions = {
                        if (!isCondensedWidth && localNode != null) {
                            NodeChip(localNode.user.short_name, localNode.colors)
                        }
                    },
                )
            },
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Top,
            ) {
                if (isCondensedWidth && localNode != null) {
                    NodeChip(localNode.user.short_name, localNode.colors)
                    Spacer(GlanceModifier.height(2.dp))
                }

                if (state.connectionState !is ConnectionState.Connected) {
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val statusText =
                            when (state.connectionState) {
                                is ConnectionState.Disconnected -> getString(Res.string.disconnected)
                                is ConnectionState.Connecting -> getString(Res.string.connecting)
                                is ConnectionState.DeviceSleep -> getString(Res.string.device_sleeping)
                                is ConnectionState.Connected -> ""
                            }
                        if (state.connectionState is ConnectionState.Connecting) {
                            CircularProgressIndicator(modifier = GlanceModifier.size(24.dp))
                            Spacer(GlanceModifier.height(8.dp))
                        }
                        Text(
                            text = statusText,
                            style =
                            TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                } else {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        StatRow(
                            label = if (isVeryCondensedWidth) "Bat" else getString(Res.string.battery),
                            value = batteryText,
                            progress = (batteryLevel / 100f).coerceIn(0f, 1f),
                            condensed = isVeryCondensedHeight,
                        )

                        StatRow(
                            label = if (isCondensedWidth) "ChU" else getString(Res.string.channel_utilization),
                            value = String.format(Locale.ROOT, "%.1f%%", channelUtil),
                            progress = (channelUtil / 100f).coerceIn(0f, 1f),
                            condensed = isVeryCondensedHeight,
                        )

                        if (!isVeryCondensedHeight) {
                            StatRow(
                                label = if (isCondensedWidth) "AirU" else getString(Res.string.air_utilization),
                                value = String.format(Locale.ROOT, "%.1f%%", airUtilTx),
                                progress = (airUtilTx / 100f).coerceIn(0f, 1f),
                                condensed = isVeryCondensedHeight,
                            )
                        }
                    }

                    if (!isCondensedHeight) {
                        Spacer(GlanceModifier.height(2.dp))

                        if (state.stats != null) {
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                if (state.stats.num_packets_tx > 0 || state.stats.num_packets_rx > 0) {
                                    Text(
                                        text =
                                        getString(
                                            Res.string.local_stats_traffic,
                                            state.stats.num_packets_tx,
                                            state.stats.num_packets_rx,
                                            state.stats.num_rx_dupe,
                                        ),
                                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        maxLines = 1,
                                    )
                                }

                                if (size.height > 190.dp) {
                                    if (state.stats.num_tx_relay > 0) {
                                        Text(
                                            text =
                                            getString(
                                                Res.string.local_stats_relays,
                                                state.stats.num_tx_relay,
                                                state.stats.num_tx_relay_canceled,
                                            ),
                                            style =
                                            TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                                            modifier = GlanceModifier.fillMaxWidth(),
                                            maxLines = 1,
                                        )
                                    }

                                    val diag = mutableListOf<String>()
                                    if (state.stats.noise_floor != 0) {
                                        diag.add(getString(Res.string.local_stats_noise, state.stats.noise_floor))
                                    }
                                    if (state.stats.num_packets_rx_bad > 0) {
                                        diag.add(getString(Res.string.local_stats_bad, state.stats.num_packets_rx_bad))
                                    }
                                    if (state.stats.num_tx_dropped > 0) {
                                        diag.add(getString(Res.string.local_stats_dropped, state.stats.num_tx_dropped))
                                    }

                                    if (diag.isNotEmpty()) {
                                        Text(
                                            text =
                                            getString(
                                                Res.string.local_stats_diagnostics_prefix,
                                                diag.joinToString(" | "),
                                            ),
                                            style =
                                            TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                                            modifier = GlanceModifier.fillMaxWidth(),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isVeryCondensedHeight) {
                        Spacer(GlanceModifier.defaultWeight())
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = GlanceModifier.defaultWeight(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "N: ",
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                                )
                                Text(
                                    text = "$onlineNodes/$totalNodes",
                                    maxLines = 1,
                                    style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                            Row(
                                modifier = GlanceModifier.defaultWeight(),
                                horizontalAlignment = Alignment.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Up: ",
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                                )
                                Text(
                                    text = formatUptime(uptimeSecs.toInt()),
                                    maxLines = 1,
                                    style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun NodeChip(shortName: String, colors: Pair<Int, Int>) {
        val (fg, bg) = colors
        Row(
            modifier =
            GlanceModifier.background(Color(bg)).cornerRadius(4.dp).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shortName,
                style = TextStyle(color = ColorProvider(Color(fg)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    @Composable
    private fun StatRow(label: String, value: String, progress: Float, condensed: Boolean = false) {
        val verticalPadding = if (condensed) 1.dp else 2.dp
        Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = verticalPadding)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = value,
                    style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (!condensed) {
                Spacer(GlanceModifier.height(2.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth().height(3.dp).cornerRadius(2.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
            }
        }
    }
}
