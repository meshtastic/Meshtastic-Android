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
package org.meshtastic.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
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
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.formatDuration
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.local_stats_bad
import org.meshtastic.core.resources.local_stats_diagnostics_prefix
import org.meshtastic.core.resources.local_stats_dropped
import org.meshtastic.core.resources.local_stats_heap
import org.meshtastic.core.resources.local_stats_heap_value
import org.meshtastic.core.resources.local_stats_noise
import org.meshtastic.core.resources.local_stats_relays
import org.meshtastic.core.resources.local_stats_traffic
import org.meshtastic.core.resources.local_stats_updated_at
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.unknown_error
import org.meshtastic.core.resources.updated
import org.meshtastic.core.resources.uptime

class LocalStatsWidget :
    GlanceAppWidget(),
    KoinComponent {

    override val sizeMode: SizeMode = WidgetSizes.responsive
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = WidgetSizes.responsive

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

    /**
     * Renders our own last-resort UI when the Glance composition throws, replacing Glance's default "Can't show
     * content" layout, and logs the failure so a broken widget stops being silent.
     *
     * Overriding this is the only option available: Glance's `errorUiLayout` constructor parameter is `internal` in
     * 1.2.0-rc01, so the approach shown in the official error-handling guide does not compile from outside the library.
     * Note that overriding this method means `errorUiLayout` is never read at all.
     *
     * The message comes from the Crowdin-managed Compose-resources catalog via the project's blocking [getString], so
     * it stays translated even though a `RemoteViews` layout cannot reference Compose resources itself.
     */
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Logger.e(throwable) { "LocalStatsWidget composition failed; rendering error UI" }
        val errorViews =
            RemoteViews(context.packageName, R.layout.widget_composition_error).apply {
                setTextViewText(R.id.widget_composition_error_text, getString(Res.string.unknown_error))
            }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, errorViews)
    }

    @Composable
    internal fun WidgetContent(state: LocalStatsWidgetUiState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val tall = size.height >= WidgetSizes.TALL.height
        val wide = size.width >= WidgetSizes.WIDE.width
        // Mandatory: without this bridge every stringResource call below throws. See WidgetResourceEnvironment.
        WidgetResourceEnvironment {
            GlanceTheme {
                Scaffold(
                    modifier =
                    GlanceModifier.fillMaxSize()
                        .clickable(
                            actionStartActivity(
                                context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    ?: Intent().setClassName(context.packageName, "org.meshtastic.app.MainActivity"),
                            ),
                        ),
                ) {
                    when {
                        !state.showContent -> Disconnected(state)

                        size.height < WidgetSizes.SQUARE.height && wide -> WideRowStats(state)

                        size.height < WidgetSizes.SQUARE.height -> CompactStats(state)

                        tall ->
                            FullStatsContent(
                                state,
                                wide = wide,
                                showHeap = size.height >= WidgetSizes.EXTRA_TALL.height,
                            )

                        else -> SquareStats(state)
                    }
                }
            }
        }
    }

    @Composable
    private fun SquareStats(state: LocalStatsWidgetUiState) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(top = 4.dp)) {
            SummaryHeader(state, isSmall = true)
            UtilizationRow(state, isSmall = true)
            Spacer(GlanceModifier.defaultWeight())
            Footer(state, showUpdated = false)
        }
    }

    @Composable
    @Suppress("LongMethod")
    private fun FullStatsContent(state: LocalStatsWidgetUiState, wide: Boolean, showHeap: Boolean) {
        val isSmall = false
        Column(modifier = GlanceModifier.fillMaxSize().padding(top = 4.dp)) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                SummaryHeader(state, isSmall, showRefresh = true, stackBattery = !wide)
                Spacer(GlanceModifier.height(2.dp))
                UtilizationRow(state, isSmall)

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
                        state.noiseFloor?.let { diag.add(stringResource(Res.string.local_stats_noise, it)) }
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

                        if (showHeap) {
                            val heapProgress =
                                if (state.heapTotalBytes > 0) {
                                    state.heapFreeBytes.toFloat() / state.heapTotalBytes
                                } else {
                                    0f
                                }
                            val heapValue =
                                stringResource(
                                    Res.string.local_stats_heap_value,
                                    state.heapFreeBytes,
                                    state.heapTotalBytes,
                                )
                            StatRow(stringResource(Res.string.local_stats_heap), heapValue, heapProgress, isSmall)
                        }
                    }
                }
            }

            // Footer (Nodes + Uptime - Pinned to bottom)
            Footer(state, showUpdated = true)
        }
    }

    @Composable
    private fun StatText(text: String, isSmall: Boolean) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = if (isSmall) 10.sp else 11.sp),
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Disconnected(state: LocalStatsWidgetUiState) {
        val statusText =
            when (state.connectionState) {
                is ConnectionState.Disconnected -> stringResource(Res.string.disconnected)
                is ConnectionState.Connecting -> stringResource(Res.string.connecting)
                is ConnectionState.DeviceSleep -> stringResource(Res.string.device_sleeping)
                is ConnectionState.Connected -> ""
            }
        val statusStyle =
            TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (LocalSize.current.height < WidgetSizes.SQUARE.height) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusIcon(state, iconSize = 24.dp)
                Spacer(GlanceModifier.width(8.dp))
                Text(text = statusText, maxLines = 1, style = statusStyle)
            }
            return
        }
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusIcon(state, iconSize = 32.dp)
            Text(text = statusText, style = statusStyle)
        }
    }

    @Suppress("LongMethod")
    @Composable
    private fun Footer(state: LocalStatsWidgetUiState, showUpdated: Boolean) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = stringResource(Res.string.nodes),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                    Text(
                        text = "${state.onlineNodes}/${state.totalNodes}",
                        maxLines = 1,
                        style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(Res.string.uptime),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                    Text(
                        text = formatDuration(state.uptimeSecs),
                        maxLines = 1,
                        style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            if (!showUpdated) return@Column
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
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    modifier = GlanceModifier.padding(bottom = 2.dp),
                    maxLines = 1,
                )
            }
        }
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
