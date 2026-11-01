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
import org.meshtastic.core.resources.formatDuration
import org.meshtastic.core.resources.free_memory
import org.meshtastic.core.resources.local_stats_updated_at
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.noise_floor
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.updated
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.resources.widget_bad_dropped
import org.meshtastic.core.resources.widget_pair_value
import org.meshtastic.core.resources.widget_relays_canceled
import org.meshtastic.core.resources.widget_traffic
import kotlin.time.DurationUnit

private const val MAX_BATTERY_PERCENT = 100

/** One row: the mark, node and battery over channel utilization, with nodes online when wide. */
@Composable
internal fun StatsRow(state: LocalStatsWidgetUiState, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            WidgetMark(16.dp)
            Spacer(GlanceModifier.width(6.dp))
            NodeChip(state)
            Spacer(GlanceModifier.defaultWeight())
            batteryValue(state)?.let { ValueText(it) }
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PairText(stringResource(Res.string.channel_utilization), MetricFormatter.percent(state.channelUtilization))
            if (wide) {
                Spacer(GlanceModifier.defaultWeight())
                PairText(stringResource(Res.string.nodes), nodesValue(state))
            }
        }
    }
}

/** Under the compact header: identity, then channel utilization, or both bars side by side when wide. */
@Composable
internal fun StatsSquare(state: LocalStatsWidgetUiState, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state, nodesInRow = wide)
        Spacer(GlanceModifier.height(4.dp))
        if (wide) BarPair(state) else ChUtilBar(state)
    }
}

/** Under the full header: identity, utilization, then the readings that fit, most glanced-at first. */
@Composable
@Suppress("LongMethod")
internal fun StatsTall(state: LocalStatsWidgetUiState, wide: Boolean, extra: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state, nodesInRow = wide)
        Spacer(GlanceModifier.height(4.dp))
        if (wide) {
            BarPair(state)
        } else {
            ChUtilBar(state)
            Spacer(GlanceModifier.height(2.dp))
            AirUtilBar(state)
        }
        Spacer(GlanceModifier.height(4.dp))
        if (!wide) StatRow(stringResource(Res.string.nodes), nodesValue(state))
        StatRow(stringResource(Res.string.uptime), formatDuration(state.uptimeSecs, DurationUnit.MINUTES))
        if (state.hasStats && (wide || extra)) {
            StatRow(
                stringResource(Res.string.widget_traffic),
                stringResource(Res.string.widget_pair_value, state.numPacketsTx, state.numPacketsRx),
            )
            state.noiseFloor?.let { StatRow(stringResource(Res.string.noise_floor), MetricFormatter.rssi(it)) }
        }
        if (!extra) return@Column
        if (state.hasStats) {
            StatRow(
                stringResource(Res.string.widget_relays_canceled),
                stringResource(Res.string.widget_pair_value, state.numTxRelay, state.numTxRelayCanceled),
            )
            if (wide) {
                StatRow(
                    stringResource(Res.string.widget_bad_dropped),
                    stringResource(Res.string.widget_pair_value, state.numPacketsRxBad, state.numTxDropped),
                )
                StatRow(
                    stringResource(Res.string.free_memory),
                    stringResource(Res.string.widget_pair_value, state.heapFreeBytes, state.heapTotalBytes),
                )
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Footnote(updatedText(state))
    }
}

@Composable
private fun Identity(state: LocalStatsWidgetUiState, nodesInRow: Boolean) {
    IdentityRow(
        chip = { NodeChip(state) },
        hero = batteryValue(state),
        heroIsWord = state.batteryLevel > MAX_BATTERY_PERCENT,
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

private fun nodesValue(state: LocalStatsWidgetUiState) = "${state.onlineNodes}/${state.totalNodes}"

@Composable
private fun updatedText(state: LocalStatsWidgetUiState): String {
    val label = stringResource(Res.string.updated)
    val time = stringResource(Res.string.local_stats_updated_at, DateFormatter.formatShortDate(state.updateTimeMillis))
    return if (label.isNotEmpty()) "$label $time" else time
}
