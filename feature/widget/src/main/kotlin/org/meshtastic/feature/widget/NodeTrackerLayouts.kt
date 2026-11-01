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
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.hops_away
import org.meshtastic.core.resources.node_sort_last_heard
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.traceroute_direct
import org.meshtastic.core.resources.unknown

private const val MAX_BATTERY_PERCENT = 100
private const val TALL_ROWS = 4
private const val EXTRA_TALL_ROWS = 5

/** The tracker body for the chosen [WidgetSizes] breakpoint, under the header the widget draws above it. */
@Composable
internal fun TrackerContent(state: NodeTrackerWidgetState.Tracking) {
    val size = LocalSize.current
    val wide = size.width >= WidgetSizes.WIDE.width
    Column(modifier = GlanceModifier.fillMaxSize().staleTag(state)) {
        when {
            size.height < WidgetSizes.SQUARE.height -> TrackerRow(state, wide)
            size.height < WidgetSizes.TALL.height -> TrackerSquare(state, wide)
            else -> TrackerTall(state, extra = size.height >= WidgetSizes.EXTRA_TALL.height)
        }
    }
}

@Composable
private fun TrackerRow(state: NodeTrackerWidgetState.Tracking, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            WidgetMark(16.dp)
            Spacer(GlanceModifier.width(6.dp))
            WidgetNodeChip(shortName = state.shortName, colors = state.colors)
            if (wide) {
                hopsValue(state)?.let {
                    Spacer(GlanceModifier.width(8.dp))
                    PairText(stringResource(Res.string.hops_away), it, state.isStale)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            batteryValue(state)?.let { ValueText(it, state.isStale) }
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LongName(state, modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            PairText(
                if (wide) stringResource(Res.string.node_sort_last_heard) else null,
                lastHeardValue(state),
                state.isStale,
            )
        }
    }
}

@Composable
private fun TrackerSquare(state: NodeTrackerWidgetState.Tracking, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state)
        Spacer(GlanceModifier.height(2.dp))
        LongName(state)
        Spacer(GlanceModifier.height(2.dp))
        if (wide) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PairText(stringResource(Res.string.node_sort_last_heard), lastHeardValue(state), state.isStale)
                Spacer(GlanceModifier.defaultWeight())
                hopsValue(state)?.let { PairText(stringResource(Res.string.hops_away), it, state.isStale) }
            }
        } else {
            StatRow(stringResource(Res.string.node_sort_last_heard), lastHeardValue(state), state.isStale)
        }
    }
}

@Composable
private fun TrackerTall(state: NodeTrackerWidgetState.Tracking, extra: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state)
        Spacer(GlanceModifier.height(2.dp))
        LongName(state, maxLines = if (extra) 2 else 1)
        Spacer(GlanceModifier.height(4.dp))
        readings(state).take(if (extra) EXTRA_TALL_ROWS else TALL_ROWS).forEach { (label, value) ->
            StatRow(label, value, state.isStale)
        }
    }
}

/** Every reading the node has, in the order they drop as the widget shrinks. */
@Composable
private fun readings(state: NodeTrackerWidgetState.Tracking): List<Pair<String, String>> = buildList {
    add(stringResource(Res.string.node_sort_last_heard) to lastHeardValue(state))
    hopsValue(state)?.let { add(stringResource(Res.string.hops_away) to it) }
    state.distance?.let { add(stringResource(Res.string.distance) to it) }
    state.snr?.let { add(stringResource(Res.string.snr) to MetricFormatter.snr(it)) }
    state.rssi?.let { add(stringResource(Res.string.rssi) to MetricFormatter.rssi(it)) }
}

@Composable
private fun Identity(state: NodeTrackerWidgetState.Tracking) {
    IdentityRow(
        chip = { WidgetNodeChip(shortName = state.shortName, colors = state.colors) },
        hero = batteryValue(state),
        heroIsWord = (state.batteryLevel ?: 0) > MAX_BATTERY_PERCENT,
        isStale = state.isStale,
    )
}

@Composable
private fun LongName(
    state: NodeTrackerWidgetState.Tracking,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1,
) {
    Text(
        text = state.longName,
        maxLines = maxLines,
        style =
        TextStyle(
            color = if (state.isStale) GlanceTheme.colors.outline else GlanceTheme.colors.onSurfaceVariant,
            fontSize = WidgetSizes.Type.label,
            fontWeight = FontWeight.Medium,
        ),
        modifier = modifier,
    )
}

@Composable
private fun batteryValue(state: NodeTrackerWidgetState.Tracking): String? {
    val level = state.batteryLevel ?: return null
    return if (level > MAX_BATTERY_PERCENT) stringResource(Res.string.powered) else MetricFormatter.percent(level)
}

@Composable
private fun hopsValue(state: NodeTrackerWidgetState.Tracking): String? = state.hopsAway?.let { hops ->
    if (hops == 0) stringResource(Res.string.traceroute_direct) else hops.toString()
}

// An absolute time, because a relative "5 min ago" would freeze between widget updates and go on reading fresh.
@Composable
private fun lastHeardValue(state: NodeTrackerWidgetState.Tracking): String = if (state.lastHeard > 0) {
    DateFormatter.formatShortDate(state.lastHeard.toLong() * TimeConstants.MS_PER_SEC)
} else {
    stringResource(Res.string.unknown)
}

private fun GlanceModifier.staleTag(state: NodeTrackerWidgetState.Tracking): GlanceModifier =
    if (state.isStale) semantics { testTag = NodeTrackerWidget.STALE_TEST_TAG } else this
