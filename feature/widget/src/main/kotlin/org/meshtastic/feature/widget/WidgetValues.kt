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
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.hops_away
import org.meshtastic.core.resources.node_sort_last_heard
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.traceroute_direct
import org.meshtastic.core.resources.unknown

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

/** Every reading the tracked node has, in the order they drop as the widget shrinks. */
@Composable
internal fun readings(state: NodeTrackerWidgetState.Tracking): List<Pair<String, String>> = buildList {
    add(stringResource(Res.string.node_sort_last_heard) to lastHeardValue(state))
    hopsValue(state)?.let { add(stringResource(Res.string.hops_away) to it) }
    state.distance?.let { add(stringResource(Res.string.distance) to it) }
    state.snr?.let { add(stringResource(Res.string.snr) to MetricFormatter.snr(it)) }
    state.rssi?.let { add(stringResource(Res.string.rssi) to MetricFormatter.rssi(it)) }
}
