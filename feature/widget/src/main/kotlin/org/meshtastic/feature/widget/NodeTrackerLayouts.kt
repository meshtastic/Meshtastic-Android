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
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.hops_away
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.traceroute_direct

/** Picks the tracker layout for the chosen [WidgetSizes] breakpoint. */
@Composable
internal fun TrackerContent(state: NodeTrackerWidgetState.Tracking) {
    val size = LocalSize.current
    val wide = size.width >= WidgetSizes.WIDE.width
    val tall = size.height >= WidgetSizes.TALL.height
    when {
        size.height < WidgetSizes.SQUARE.height -> if (wide) WideRow(state) else CompactRow(state)
        wide -> Wide(state, large = tall)
        tall -> Tall(state)
        else -> Small(state)
    }
}

@Composable
private fun CompactRow(state: NodeTrackerWidgetState.Tracking) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(vertical = 4.dp).staleTag(state),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetNodeChip(shortName = state.shortName, colors = state.colors, dimmed = state.isStale)
            Spacer(GlanceModifier.defaultWeight())
            batteryValue(state)?.let { ValueText(it, state.isStale) }
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.longName,
                maxLines = 1,
                style = TextStyle(color = valueColor(state.isStale), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            ValueText(lastHeardValue(state), state.isStale)
        }
    }
}

@Composable
private fun WideRow(state: NodeTrackerWidgetState.Tracking) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(vertical = 4.dp).staleTag(state),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Identity(state, modifier = GlanceModifier.defaultWeight(), nameGap = 0.dp)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            BatteryLine(state)
            LastHeardLine(state)
        }
    }
}

@Composable
private fun Small(state: NodeTrackerWidgetState.Tracking) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp).staleTag(state)) {
        Identity(state)
        Spacer(GlanceModifier.defaultWeight())
        BatteryLine(state)
        LastHeardLine(state)
    }
}

@Composable
private fun Tall(state: NodeTrackerWidgetState.Tracking) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp).staleTag(state)) {
        Identity(state)
        Spacer(GlanceModifier.height(8.dp))
        LastHeardLine(state, large = true)
        BatteryLine(state, large = true)
        SignalLines(state, large = true)
    }
}

/** Two columns; at two rows tall the readings grow rather than leaving the lower half empty. */
@Composable
private fun Wide(state: NodeTrackerWidgetState.Tracking, large: Boolean) {
    Row(modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp).staleTag(state)) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Identity(state)
            Spacer(GlanceModifier.height(if (large) 8.dp else 4.dp))
            LastHeardLine(state, large)
            BatteryLine(state, large)
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            SignalLines(state, large)
        }
    }
}

@Composable
private fun SignalLines(state: NodeTrackerWidgetState.Tracking, large: Boolean = false) {
    state.snr?.let { StatLine(stringResource(Res.string.snr), MetricFormatter.snr(it), state.isStale, large) }
    state.rssi?.let { StatLine(stringResource(Res.string.rssi), MetricFormatter.rssi(it), state.isStale, large) }
    state.hopsAway?.let { hops ->
        val value = if (hops == 0) stringResource(Res.string.traceroute_direct) else hops.toString()
        StatLine(stringResource(Res.string.hops_away), value, state.isStale, large)
    }
    state.distance?.let { StatLine(stringResource(Res.string.distance), it, state.isStale, large) }
}

@Composable
private fun ValueText(value: String, isStale: Boolean) {
    Text(
        text = value,
        maxLines = 1,
        style = TextStyle(color = valueColor(isStale), fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}
