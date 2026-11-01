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
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.hops_away
import org.meshtastic.core.resources.node_sort_last_heard

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
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            batteryValue(state)?.let { ValueText(it, state.isStale, batteryDescription(it)) }
        }
        Spacer(GlanceModifier.height(2.dp))
        NameAndLastHeard(state, lastHeardLabel = wide)
    }
}

/** Header, identity, then the name and last heard sharing one line so a portrait 2x1 cell holds it. */
@Composable
private fun TrackerSquare(state: NodeTrackerWidgetState.Tracking, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state)
        Spacer(GlanceModifier.height(2.dp))
        NameAndLastHeard(state, lastHeardLabel = false, showHops = wide)
    }
}

@Composable
private fun TrackerTall(state: NodeTrackerWidgetState.Tracking, extra: Boolean) {
    val rows = readings(state).take(if (extra) EXTRA_TALL_ROWS else TALL_ROWS)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Identity(state)
        Spacer(GlanceModifier.height(2.dp))
        LongName(state, maxLines = if (extra || rows.size < TALL_ROWS) 2 else 1)
        Spacer(GlanceModifier.height(4.dp))
        rows.forEach { (label, value) -> StatRow(label, value, state.isStale) }
    }
}

@Composable
private fun NameAndLastHeard(
    state: NodeTrackerWidgetState.Tracking,
    lastHeardLabel: Boolean,
    showHops: Boolean = false,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LongName(state, modifier = GlanceModifier.defaultWeight())
        if (showHops) {
            hopsValue(state)?.let {
                Spacer(GlanceModifier.width(8.dp))
                PairText(stringResource(Res.string.hops_away), it, state.isStale)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        PairText(
            if (lastHeardLabel) stringResource(Res.string.node_sort_last_heard) else null,
            lastHeardValue(state),
            state.isStale,
        )
    }
}

@Composable
private fun Identity(state: NodeTrackerWidgetState.Tracking) {
    val battery = batteryValue(state)
    IdentityRow(
        chip = { WidgetNodeChip(shortName = state.shortName, colors = state.colors) },
        hero = battery,
        heroIsWord = (state.batteryLevel ?: 0) > MAX_BATTERY_PERCENT,
        heroDescription = battery?.let { batteryDescription(it) },
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

private fun GlanceModifier.staleTag(state: NodeTrackerWidgetState.Tracking): GlanceModifier =
    if (state.isStale) semantics { testTag = NodeTrackerWidget.STALE_TEST_TAG } else this
