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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_utilization
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.channel_utilization
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.local_stats_bad
import org.meshtastic.core.resources.local_stats_diagnostics_prefix
import org.meshtastic.core.resources.local_stats_dropped
import org.meshtastic.core.resources.local_stats_noise
import org.meshtastic.core.resources.local_stats_relays
import org.meshtastic.core.resources.local_stats_traffic
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.uptime
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
            WidgetContent(state)
        }
    }

    @Composable
    private fun WidgetContent(state: LocalStatsWidgetUiState) {
        val context = LocalContext.current
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides context,
            LocalConfiguration provides context.resources.configuration,
            LocalDensity provides Density(context.resources.displayMetrics.density),
        ) {
            val localNode = state.localNode
            val metrics = localNode?.deviceMetrics
            val onlineNodes = state.nodes.values.count { it.lastHeard > onlineTimeThreshold() }
            val totalNodes = state.nodes.size
            val uptimeSecs: Long = state.stats?.uptime_seconds?.toLong() ?: metrics?.uptime_seconds?.toLong() ?: 0L
            GlanceTheme {
                Scaffold(
                    titleBar = {
                        TitleBar(
                            startIcon = ImageProvider(com.geeksville.mesh.R.drawable.app_icon),
                            title = stringResource(Res.string.meshtastic_app_name),
                            actions = {
                                CircleIconButton(
                                    imageProvider = ImageProvider(com.geeksville.mesh.R.drawable.ic_refresh),
                                    contentDescription = "Refresh",
                                    onClick = actionRunCallback<RefreshLocalStatsAction>(),
                                    backgroundColor = null,
                                )
                            },
                        )
                    },
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (state.connectionState is ConnectionState.Connected) {
                            StatsContent(state = state, modifier = GlanceModifier.defaultWeight())
                            Footer(onlineNodes = onlineNodes, totalNodes = totalNodes, uptimeSecs = uptimeSecs)
                        } else {
                            Disconnected(state, GlanceModifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    @Composable
    @Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
    private fun StatsContent(state: LocalStatsWidgetUiState, modifier: GlanceModifier) {
        val localNode = state.localNode
        val metrics = localNode?.deviceMetrics

        val batteryLevel = metrics?.battery_level ?: 0
        val isPowered = batteryLevel > 100
        val batteryText = if (isPowered) stringResource(Res.string.powered) else "$batteryLevel%"

        val channelUtil = state.stats?.channel_utilization ?: metrics?.channel_utilization ?: 0f
        val airUtilTx = state.stats?.air_util_tx ?: metrics?.air_util_tx ?: 0f

        Column(modifier = modifier) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                localNode?.let { NodeChip(shortName = it.user.short_name, colors = it.colors) }
                Spacer(GlanceModifier.width(8.dp))
                StatRow(
                    label = stringResource(Res.string.battery),
                    value = batteryText,
                    progress = (batteryLevel / 100f).coerceIn(0f, 1f),
                )
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatRow(
                    label = stringResource(Res.string.channel_utilization),
                    value = String.format(Locale.ROOT, "%.1f%%", channelUtil),
                    progress = (channelUtil / 100f).coerceIn(0f, 1f),
                    modifier = GlanceModifier.defaultWeight().padding(end = 4.dp),
                )
                StatRow(
                    label = stringResource(Res.string.air_utilization),
                    value = String.format(Locale.ROOT, "%.1f%%", airUtilTx),
                    progress = (airUtilTx / 100f).coerceIn(0f, 1f),
                    modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                )
            }
            if (state.stats != null) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    if (state.stats.num_packets_tx > 0 || state.stats.num_packets_rx > 0) {
                        Text(
                            text =
                            stringResource(
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

                    if (state.stats.num_tx_relay > 0) {
                        Text(
                            text =
                            stringResource(
                                Res.string.local_stats_relays,
                                state.stats.num_tx_relay,
                                state.stats.num_tx_relay_canceled,
                            ),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                            modifier = GlanceModifier.fillMaxWidth(),
                            maxLines = 1,
                        )

                        val diag = mutableListOf<String>()
                        if (state.stats.noise_floor != 0) {
                            diag.add(stringResource(Res.string.local_stats_noise, state.stats.noise_floor))
                        }
                        if (state.stats.num_packets_rx_bad > 0) {
                            diag.add(stringResource(Res.string.local_stats_bad, state.stats.num_packets_rx_bad))
                        }
                        if (state.stats.num_tx_dropped > 0) {
                            diag.add(stringResource(Res.string.local_stats_dropped, state.stats.num_tx_dropped))
                        }

                        if (diag.isNotEmpty()) {
                            Text(
                                text =
                                stringResource(Res.string.local_stats_diagnostics_prefix, diag.joinToString(" | ")),
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                                modifier = GlanceModifier.fillMaxWidth(),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Disconnected(state: LocalStatsWidgetUiState, modifier: GlanceModifier) {
        Column(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val statusText =
                when (state.connectionState) {
                    is ConnectionState.Disconnected -> stringResource(Res.string.disconnected)
                    is ConnectionState.Connecting -> stringResource(Res.string.connecting)
                    is ConnectionState.DeviceSleep -> stringResource(Res.string.device_sleeping)
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
    }

    @Composable
    private fun Footer(onlineNodes: Int, totalNodes: Int, uptimeSecs: Long) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.nodes),
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
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.uptime),
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

    @SuppressLint("RestrictedApi")
    @Composable
    private fun NodeChip(shortName: String, colors: Pair<Int, Int>, modifier: GlanceModifier = GlanceModifier) {
        val (fg, bg) = colors
        Row(
            modifier = modifier.background(Color(bg)).cornerRadius(4.dp).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shortName,
                style = TextStyle(color = ColorProvider(Color(fg)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    @Composable
    private fun StatRow(label: String, value: String, progress: Float, modifier: GlanceModifier = GlanceModifier) {
        Column(modifier = modifier.padding(vertical = 4.dp)) {
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
            Spacer(GlanceModifier.height(1.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp).cornerRadius(2.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
    }
}
