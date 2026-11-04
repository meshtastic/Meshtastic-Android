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
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_utilization
import org.meshtastic.core.resources.channel_utilization
import org.meshtastic.core.resources.local_stats_updated_at
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.updated

/** Between the two stacked bars at narrow widths, counted in the line budget. */
private val BAR_SPACING = 2.dp

/** One row: the mark, node and battery over channel utilization, with nodes online when wide. */
@Composable
internal fun StatsRow(state: LocalStatsWidgetUiState, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetMark(16.dp)
            Spacer(GlanceModifier.width(6.dp))
            NodeChip(state)
            Spacer(GlanceModifier.defaultWeight())
            batteryValue(state)?.let { ValueText(it, contentDescription = batteryDescription(it)) }
        }
        Spacer(GlanceModifier.height(2.dp))
        val chUtil = MetricFormatter.percent(state.channelUtilization)
        if (wide) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PairText(stringResource(Res.string.channel_utilization), chUtil)
                Spacer(GlanceModifier.defaultWeight())
                PairText(stringResource(Res.string.nodes), nodesValue(state))
            }
        } else {
            // StatRow, so a long translated label gives way before the value does.
            StatRow(stringResource(Res.string.channel_utilization), chUtil)
        }
    }
}

/** Under the compact header: identity, then channel utilization, or both bars side by side when wide. */
@Composable
internal fun StatsSquare(state: LocalStatsWidgetUiState, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state, nodesInRow = wide)
        Spacer(GlanceModifier.height(2.dp))
        if (wide) BarPair(state) else ChUtilBar(state)
    }
}

/**
 * Under a header [headerHeight] tall: identity, utilization, then as many readings as the height holds, in [columns],
 * most glanced-at first. Height left over spreads the lines when the readings run out, and sits under them otherwise.
 */
@Composable
internal fun StatsTall(state: LocalStatsWidgetUiState, wide: Boolean, columns: Int, extra: Boolean, headerHeight: Dp) {
    val heights = WidgetSizes.Heights
    // Under the header, the identity row and the bars, plus over the footnote.
    val gaps = heights.gap + heights.gap + heights.gap + (if (extra) heights.gap else 0.dp)
    val lines =
        WidgetSizes.lines(
            fixed = headerHeight + gaps + heights.bottom + (if (wide) 0.dp else BAR_SPACING),
            text = heights.identity + heights.bar * (if (wide) 1 else 2) + (if (extra) heights.footnote else 0.dp),
        )
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state, nodesInRow = wide)
        Spacer(GlanceModifier.height(4.dp))
        if (wide) {
            BarPair(state)
        } else {
            ChUtilBar(state)
            Spacer(GlanceModifier.height(BAR_SPACING))
            AirUtilBar(state)
        }
        Spacer(GlanceModifier.height(4.dp))
        StatGrid(
            readings(state, nodesInGrid = !wide).take(lines * columns),
            columns,
            lines,
            GlanceModifier.defaultWeight(),
        )
        if (extra) Footnote(updatedText(state))
    }
}

@Composable
private fun Identity(state: LocalStatsWidgetUiState, nodesInRow: Boolean) {
    val battery = batteryValue(state)
    IdentityRow(
        chip = { NodeChip(state) },
        hero = battery,
        heroIsWord = state.batteryLevel > MAX_BATTERY_PERCENT,
        heroDescription = battery?.let { batteryDescription(it) },
        trailing = if (nodesInRow) ({ PairText(stringResource(Res.string.nodes), nodesValue(state)) }) else null,
    )
}

@Composable
private fun BarPair(state: LocalStatsWidgetUiState) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        ChUtilBar(state, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(8.dp))
        AirUtilBar(state, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun ChUtilBar(state: LocalStatsWidgetUiState, modifier: GlanceModifier = GlanceModifier) {
    BarRow(
        label = stringResource(Res.string.channel_utilization),
        value = MetricFormatter.percent(state.channelUtilization),
        progress = state.channelUtilizationProgress,
        modifier = modifier,
    )
}

@Composable
private fun AirUtilBar(state: LocalStatsWidgetUiState, modifier: GlanceModifier = GlanceModifier) {
    BarRow(
        label = stringResource(Res.string.air_utilization),
        value = MetricFormatter.percent(state.airUtilization),
        progress = state.airUtilizationProgress,
        modifier = modifier,
    )
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
private fun updatedText(state: LocalStatsWidgetUiState): String {
    val label = stringResource(Res.string.updated)
    val time = stringResource(Res.string.local_stats_updated_at, DateFormatter.formatShortDate(state.updateTimeMillis))
    return if (label.isNotEmpty()) "$label $time" else time
}
