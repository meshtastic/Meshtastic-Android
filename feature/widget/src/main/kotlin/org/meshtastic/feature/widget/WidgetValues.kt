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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.glance.GlanceTheme
import androidx.glance.unit.ColorProvider
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.formatByteSize
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.formatDuration
import org.meshtastic.core.resources.free_memory
import org.meshtastic.core.resources.hops_away
import org.meshtastic.core.resources.node_sort_last_heard
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.noise_floor
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.traceroute_direct
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.resources.widget_packets_bad
import org.meshtastic.core.resources.widget_packets_dropped
import org.meshtastic.core.resources.widget_packets_received
import org.meshtastic.core.resources.widget_packets_sent
import org.meshtastic.core.resources.widget_relays
import org.meshtastic.core.resources.widget_relays_canceled
import kotlin.time.DurationUnit

/** Firmware reports 101 for a node on external power. */
internal const val MAX_BATTERY_PERCENT = 100

/** What a screen reader announces for a battery value shown without its label. */
@Composable internal fun batteryDescription(value: String): String = "${stringResource(Res.string.battery)} $value"

@Composable
@ReadOnlyComposable
internal fun valueColor(isStale: Boolean): ColorProvider =
    if (isStale) GlanceTheme.colors.outline else GlanceTheme.colors.onSurface

@Composable
internal fun batteryValue(state: NodeTrackerWidgetState.Tracking): String? {
    val level = state.batteryLevel ?: return null
    return if (level > MAX_BATTERY_PERCENT) stringResource(Res.string.powered) else MetricFormatter.percent(level)
}

@Composable
internal fun hopsValue(state: NodeTrackerWidgetState.Tracking): String? = state.hopsAway?.let { hops ->
    if (hops == 0) stringResource(Res.string.traceroute_direct) else hops.toString()
}

// An absolute time, because a relative "5 min ago" would freeze between widget updates and go on reading fresh.
@Composable
internal fun lastHeardValue(state: NodeTrackerWidgetState.Tracking): String = if (state.lastHeard > 0) {
    DateFormatter.formatShortDate(state.lastHeard.toLong() * TimeConstants.MS_PER_SEC)
} else {
    stringResource(Res.string.unknown)
}

internal fun nodesValue(state: LocalStatsWidgetUiState) = "${state.onlineNodes}/${state.totalNodes}"

/** Every reading the Local Stats tall layouts draw from, most glanced-at first; the counters wait for `LocalStats`. */
@Composable
internal fun readings(state: LocalStatsWidgetUiState, nodesInGrid: Boolean): List<Reading> = buildList {
    if (nodesInGrid) add(Reading(stringResource(Res.string.nodes), nodesValue(state)))
    add(Reading(stringResource(Res.string.uptime), formatDuration(state.uptimeSecs, DurationUnit.MINUTES)))
    if (!state.hasStats) return@buildList
    add(Reading(stringResource(Res.string.widget_packets_sent), state.numPacketsTx.toString()))
    add(Reading(stringResource(Res.string.widget_packets_received), state.numPacketsRx.toString()))
    state.noiseFloor?.let { add(Reading(stringResource(Res.string.noise_floor), MetricFormatter.rssi(it))) }
    add(Reading(stringResource(Res.string.free_memory), formatByteSize(state.heapFreeBytes.toLong())))
    add(Reading(stringResource(Res.string.widget_relays), state.numTxRelay.toString()))
    add(Reading(stringResource(Res.string.widget_packets_bad), state.numPacketsRxBad.toString()))
    add(Reading(stringResource(Res.string.widget_packets_dropped), state.numTxDropped.toString()))
    add(Reading(stringResource(Res.string.widget_relays_canceled), state.numTxRelayCanceled.toString()))
}

/** Every reading the tracked node has, in the order they drop as the widget shrinks. */
@Composable
internal fun readings(state: NodeTrackerWidgetState.Tracking): List<Reading> = buildList {
    add(Reading(stringResource(Res.string.node_sort_last_heard), lastHeardValue(state)))
    hopsValue(state)?.let { add(Reading(stringResource(Res.string.hops_away), it)) }
    state.distance?.let { add(Reading(stringResource(Res.string.distance), it)) }
    state.snr?.let { add(Reading(stringResource(Res.string.snr), MetricFormatter.snr(it))) }
    state.rssi?.let { add(Reading(stringResource(Res.string.rssi), MetricFormatter.rssi(it))) }
}
