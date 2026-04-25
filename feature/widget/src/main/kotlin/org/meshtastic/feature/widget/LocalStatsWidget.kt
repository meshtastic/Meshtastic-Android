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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.util.formatUptime
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
import org.meshtastic.core.resources.local_stats_heap
import org.meshtastic.core.resources.local_stats_heap_value
import org.meshtastic.core.resources.local_stats_noise
import org.meshtastic.core.resources.local_stats_relays
import org.meshtastic.core.resources.local_stats_traffic
import org.meshtastic.core.resources.local_stats_updated_at
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.refresh
import org.meshtastic.core.resources.updated
import org.meshtastic.core.resources.uptime

class LocalStatsWidget :
    GlanceAppWidget(),
    KoinComponent {

    override val sizeMode: SizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

    private val stateProvider: LocalStatsWidgetStateProvider by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state by stateProvider.state.collectAsState()
            WidgetContent(state)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val currentState = stateProvider.state.value

        val stateToRender =
            if (currentState.showContent && currentState.nodeShortName != null) {
                currentState
            } else {
                createMockWidgetState()
            }
        provideContent { WidgetContent(stateToRender) }
    }

    @Composable
    internal fun WidgetContent(state: LocalStatsWidgetUiState) {
        val context = LocalContext.current
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides context,
            LocalConfiguration provides context.resources.configuration,
            LocalDensity provides Density(context.resources.displayMetrics.density),
        ) {
            GlanceTheme {
                Scaffold(
                    titleBar = {
                        TitleBar(
                            startIcon = ImageProvider(R.drawable.widget_app_icon),
                            title = stringResource(Res.string.meshtastic_app_name),
                            actions = {
                                CircleIconButton(
                                    imageProvider = ImageProvider(R.drawable.widget_ic_refresh),
                                    contentDescription = stringResource(Res.string.refresh),
                                    onClick = actionRunCallback<RefreshLocalStatsAction>(),
                                    backgroundColor = null,
                                )
                            },
                        )
                    },
                    modifier =
                    GlanceModifier.fillMaxSize()
                        .clickable(
                            actionStartActivity(
                                context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    ?: Intent().setClassName(context.packageName, "org.meshtastic.app.MainActivity"),
                            ),
                        ),
                ) {
                    if (state.showContent) {
                        FullStatsContent(state)
                    } else {
                        Disconnected(state)
                    }
                }
            }
        }
    }

    @Composable
    @Suppress("LongMethod", "MagicNumber")
    private fun FullStatsContent(state: LocalStatsWidgetUiState) {
        val size = LocalSize.current
        val isNarrow = size.width < 160.dp
        val isShort = size.height < 110.dp
        val isSmall = isNarrow || isShort
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Main Stats Container
            Column(modifier = GlanceModifier.defaultWeight()) {
                // Summary Header: Node Chip + Battery
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    state.nodeShortName?.let { name ->
                        state.nodeColors?.let { colors -> NodeChip(shortName = name, colors = colors) }
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    if (state.hasBattery) {
                        val isPowered = state.batteryLevel > 100
                        val batteryValue =
                            if (isPowered) stringResource(Res.string.powered) else "${state.batteryLevel}%"
                        StatRow(
                            label = stringResource(Res.string.battery),
                            value = batteryValue,
                            progress = state.batteryProgress,
                            isSmall = isSmall,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    } else {
                        Spacer(GlanceModifier.defaultWeight())
                    }
                }

                Spacer(GlanceModifier.height(2.dp))

                // Utilization Stats

                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    StatRow(
                        label = stringResource(Res.string.channel_utilization),
                        value = "%.1f%%".format(state.channelUtilization),
                        progress = state.channelUtilizationProgress,
                        isSmall = isSmall,
                        modifier = GlanceModifier.defaultWeight().padding(end = 4.dp),
                    )
                    StatRow(
                        label = stringResource(Res.string.air_utilization),
                        value = "%.1f%%".format(state.airUtilization),
                        progress = state.airUtilizationProgress,
                        isSmall = isSmall,
                        modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                    )
                }

                // Detailed Traffic/Relay Stats
                Spacer(GlanceModifier.height(2.dp))
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    if (state.hasStats) {
                        StatText(
                            stringResource(
                                Res.string.local_stats_traffic,
                                state.numPacketsTx,
                                state.numPacketsRx,
                                state.numRxDupe,
                            ),
                            isSmall,
                        )
                        if (state.numTxRelay > 0 || state.numTxRelayCanceled > 0) {
                            StatText(
                                stringResource(
                                    Res.string.local_stats_relays,
                                    state.numTxRelay,
                                    state.numTxRelayCanceled,
                                ),
                                isSmall,
                            )
                        }

                        val diag = mutableListOf<String>()
                        if (state.noiseFloor != 0) {
                            diag.add(stringResource(Res.string.local_stats_noise, state.noiseFloor))
                        }
                        if (state.numPacketsRxBad > 0) {
                            diag.add(stringResource(Res.string.local_stats_bad, state.numPacketsRxBad))
                        }
                        if (state.numTxDropped > 0) {
                            diag.add(stringResource(Res.string.local_stats_dropped, state.numTxDropped))
                        }
                        if (diag.isNotEmpty()) {
                            StatText(
                                stringResource(Res.string.local_stats_diagnostics_prefix, diag.joinToString(" | ")),
                                isSmall,
                            )
                        }

                        val heapProgress =
                            if (state.heapTotalBytes > 0) {
                                state.heapFreeBytes.toFloat() / state.heapTotalBytes
                            } else {
                                0f
                            }
                        val heapValue =
                            stringResource(Res.string.local_stats_heap_value, state.heapFreeBytes, state.heapTotalBytes)
                        StatRow(stringResource(Res.string.local_stats_heap), heapValue, heapProgress, isSmall)
                    }
                }
            }

            // Footer (Nodes + Uptime - Pinned to bottom)
            Footer(state)
        }
    }

    @Composable
    private fun StatText(text: String, isSmall: Boolean) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = if (isSmall) 9.sp else 10.sp),
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Disconnected(state: LocalStatsWidgetUiState) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isConnecting) {
                CircularProgressIndicator(modifier = GlanceModifier.size(24.dp))
            } else {
                Image(
                    provider = ImageProvider(R.drawable.widget_app_icon),
                    contentDescription = null,
                    modifier = GlanceModifier.size(32.dp),
                )
            }
            val statusText =
                when (state.connectionState) {
                    is ConnectionState.Disconnected -> stringResource(Res.string.disconnected)
                    is ConnectionState.Connecting -> stringResource(Res.string.connecting)
                    is ConnectionState.DeviceSleep -> stringResource(Res.string.device_sleeping)
                    is ConnectionState.Connected -> ""
                }
            Text(
                text = statusText,
                style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }

    @Suppress("LongMethod")
    @Composable
    private fun Footer(state: LocalStatsWidgetUiState) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = stringResource(Res.string.nodes),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    )
                    Text(
                        text = "${state.onlineNodes}/${state.totalNodes}",
                        maxLines = 1,
                        style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(Res.string.uptime),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    )
                    Text(
                        text = formatUptime(state.uptimeSecs.toInt()),
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
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val updatedLabel = stringResource(Res.string.updated)
                val updatedText =
                    stringResource(
                        Res.string.local_stats_updated_at,
                        DateFormatter.formatShortDate(state.updateTimeMillis),
                    )
                val footerText =
                    if (updatedLabel.isNotEmpty()) {
                        "$updatedLabel $updatedText"
                    } else {
                        updatedText
                    }
                Text(
                    text = footerText,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 8.sp),
                    modifier = GlanceModifier.padding(bottom = 2.dp),
                    maxLines = 1,
                )
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun NodeChip(shortName: String, colors: Pair<Int, Int>, modifier: GlanceModifier = GlanceModifier) {
        val (fg, bg) = colors
        Row(
            modifier =
            modifier
                .width(64.dp)
                .background(Color(bg))
                .cornerRadius(4.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = shortName,
                style = TextStyle(color = ColorProvider(Color(fg)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    @Composable
    private fun StatRow(
        label: String,
        value: String?,
        progress: Float,
        isSmall: Boolean,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Column(modifier = modifier.padding(vertical = 2.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = if (isSmall) 10.sp else 11.sp,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                value?.let {
                    Text(
                        text = it,
                        style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
    }

    companion object {
        private val SMALL_SQUARE = DpSize(100.dp, 100.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(250.dp, 100.dp)
        private val BIG_SQUARE = DpSize(250.dp, 250.dp)

        private val RESPONSIVE_SIZES = setOf(SMALL_SQUARE, HORIZONTAL_RECTANGLE, BIG_SQUARE)
    }
}

internal fun createMockWidgetState() = LocalStatsWidgetUiState(
    connectionState = ConnectionState.Connected,
    showContent = true,
    nodeShortName = "ME",
    nodeColors = 0xFFFFFFFF.toInt() to 0xFF000000.toInt(),
    batteryLevel = 85,
    hasBattery = true,
    batteryProgress = 0.85f,
    channelUtilization = 18.5f,
    channelUtilizationProgress = 0.185f,
    airUtilization = 3.2f,
    airUtilizationProgress = 0.032f,
    hasStats = true,
    numPacketsTx = 145,
    numPacketsRx = 892,
    numRxDupe = 42,
    totalNodes = 3,
    onlineNodes = 2,
    uptimeSecs = 172800L,
    updateTimeMillis = System.currentTimeMillis() - 300000L,
)
