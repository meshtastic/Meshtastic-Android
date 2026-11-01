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

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
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
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_utilization
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.channel_utilization
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.refresh

private const val MAX_BATTERY_PERCENT = 100

/** One row, two cells wide: the node and its battery over channel utilization and nodes online. */
@Composable
internal fun CompactStats(state: LocalStatsWidgetUiState) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NodeChip(state)
            Spacer(GlanceModifier.defaultWeight())
            batteryValue(state)?.let { ValueText(it) }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LabelText(stringResource(Res.string.channel_utilization))
            Spacer(GlanceModifier.width(4.dp))
            ValueText(MetricFormatter.percent(state.channelUtilization))
            Spacer(GlanceModifier.defaultWeight())
            LabelText(stringResource(Res.string.nodes))
            Spacer(GlanceModifier.width(4.dp))
            ValueText(nodesValue(state))
        }
    }
}

/** One row, wide: the node beside battery, channel utilization and nodes online. */
@Composable
internal fun WideRowStats(state: LocalStatsWidgetUiState) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        NodeChip(state)
        Spacer(GlanceModifier.width(8.dp))
        batteryValue(state)?.let { battery ->
            StatRow(
                label = stringResource(Res.string.battery),
                value = battery,
                progress = state.batteryProgress,
                isSmall = true,
                modifier = GlanceModifier.defaultWeight().padding(end = 8.dp),
            )
        }
        StatRow(
            label = stringResource(Res.string.channel_utilization),
            value = MetricFormatter.percent(state.channelUtilization),
            progress = state.channelUtilizationProgress,
            isSmall = true,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            LabelText(stringResource(Res.string.nodes))
            ValueText(nodesValue(state))
        }
    }
}

/**
 * The node chip and battery bar, with the refresh button at the end when [showRefresh]. Two cells wide leaves no room
 * for all three in one row, so [stackBattery] puts the battery bar on its own row under the chip.
 */
@Composable
internal fun SummaryHeader(
    state: LocalStatsWidgetUiState,
    isSmall: Boolean,
    showRefresh: Boolean = false,
    stackBattery: Boolean = false,
) {
    val battery = batteryValue(state)
    val batteryRow: @Composable (GlanceModifier) -> Unit = { modifier ->
        if (battery != null) {
            StatRow(
                label = stringResource(Res.string.battery),
                value = battery,
                progress = state.batteryProgress,
                isSmall = isSmall,
                modifier = modifier,
            )
        }
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NodeChip(state)
            Spacer(GlanceModifier.width(8.dp))
            if (stackBattery || battery == null) {
                Spacer(GlanceModifier.defaultWeight())
            } else {
                batteryRow(GlanceModifier.defaultWeight())
            }
            if (showRefresh) {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.widget_ic_refresh),
                    contentDescription = stringResource(Res.string.refresh),
                    onClick = actionRunCallback<RefreshLocalStatsAction>(),
                    backgroundColor = null,
                )
            }
        }
        if (stackBattery) batteryRow(GlanceModifier.fillMaxWidth())
    }
}

private fun nodesValue(state: LocalStatsWidgetUiState) = "${state.onlineNodes}/${state.totalNodes}"

@Composable
private fun LabelText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
    )
}

@Composable
internal fun UtilizationRow(
    state: LocalStatsWidgetUiState,
    isSmall: Boolean,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    Row(modifier = modifier) {
        StatRow(
            label = stringResource(Res.string.channel_utilization),
            value = MetricFormatter.percent(state.channelUtilization),
            progress = state.channelUtilizationProgress,
            isSmall = isSmall,
            modifier = GlanceModifier.defaultWeight().padding(end = 4.dp),
        )
        StatRow(
            label = stringResource(Res.string.air_utilization),
            value = MetricFormatter.percent(state.airUtilization),
            progress = state.airUtilizationProgress,
            isSmall = isSmall,
            modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
        )
    }
}

@Composable
internal fun StatRow(
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
                maxLines = 1,
                style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (isSmall) 11.sp else 12.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            value?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (isSmall) 11.sp else 12.sp,
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

@Composable
internal fun StatusIcon(state: LocalStatsWidgetUiState, iconSize: Dp) {
    if (state.isConnecting) {
        CircularProgressIndicator(modifier = GlanceModifier.size(24.dp))
    } else {
        Image(
            provider = ImageProvider(R.drawable.widget_app_icon),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

@Composable
private fun NodeChip(state: LocalStatsWidgetUiState) {
    val name = state.nodeShortName ?: return
    val colors = state.nodeColors ?: return
    WidgetNodeChip(shortName = name, colors = colors)
}

@Composable
private fun batteryValue(state: LocalStatsWidgetUiState): String? = when {
    !state.hasBattery -> null
    state.batteryLevel > MAX_BATTERY_PERCENT -> stringResource(Res.string.powered)
    else -> MetricFormatter.percent(state.batteryLevel)
}

@Composable
private fun ValueText(value: String) {
    Text(
        text = value,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}
