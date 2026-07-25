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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.feature.node.model.DrawableMetricInfo
import org.meshtastic.feature.node.model.MetricInfo
import org.meshtastic.feature.node.model.VectorMetricInfo

/** Cards that belong together — one physical sensor, or a reading and the value derived from it. */
internal typealias MetricGroup = List<MetricInfo>

/** Groups a single reading that has nothing to pair with, so it renders as a one-card column. */
internal fun MetricInfo.asGroup(): MetricGroup = listOf(this)

/** Height, in cards, that a column is packed to when a run of unrelated single metrics is stacked. */
private const val PACKED_COLUMN_HEIGHT = 2

/**
 * Packs runs of consecutive single-metric groups into taller columns, preserving order.
 *
 * A [FlowRow] row is as tall as its tallest child, so mixing one-card columns with grouped two-card columns strands
 * empty space beneath every single card. Stacking consecutive singles keeps rows an even height and reclaims most of
 * that space; a single sandwiched between two multi-card groups still gets a column of its own.
 */
private fun List<MetricGroup>.packed(): List<MetricGroup> = buildList {
    val singles = mutableListOf<MetricInfo>()
    fun flushSingles() {
        singles.chunked(PACKED_COLUMN_HEIGHT).forEach { add(it) }
        singles.clear()
    }
    this@packed.filter { it.isNotEmpty() }
        .forEach { group ->
            if (group.size == 1) {
                singles += group
            } else {
                flushSingles()
                add(group)
            }
        }
    flushSingles()
}

/**
 * Lays metric cards out as a wrapping row of vertical columns, one column per [MetricGroup].
 *
 * Related readings (a channel's voltage and current, a temperature and its dew point) stay stacked together and share a
 * column width; unrelated single readings are packed into columns of [PACKED_COLUMN_HEIGHT] so the grid keeps an even
 * height. Both together pack the cards far more tightly than a flat row of independent cards — see issue #4507.
 *
 * [valueColor] overrides a card's value text color, for metrics that are color-coded by severity; returning null keeps
 * the [InfoCard] default.
 */
@Composable
internal fun MetricCardFlow(
    groups: List<MetricGroup>,
    modifier: Modifier = Modifier,
    valueColor: (MetricInfo) -> Color? = { null },
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.packed().forEach { group ->
            // IntrinsicSize.Max + fillMaxWidth keeps every card in a column the same width as the column's widest.
            Column(modifier = Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { metric -> MetricCard(metric = metric, valueColor = valueColor(metric)) }
            }
        }
    }
}

@Composable
private fun MetricCard(metric: MetricInfo, valueColor: Color?) {
    val cardModifier = Modifier.fillMaxWidth()
    val label = stringResource(metric.label)
    val resolvedValueColor = valueColor ?: MaterialTheme.colorScheme.onSurface
    when (metric) {
        is VectorMetricInfo ->
            InfoCard(
                icon = metric.icon,
                text = label,
                value = metric.value,
                rotateIcon = metric.rotateIcon,
                modifier = cardModifier,
                valueColor = resolvedValueColor,
            )

        is DrawableMetricInfo ->
            DrawableInfoCard(
                iconRes = metric.icon,
                text = label,
                value = metric.value,
                rotateIcon = metric.rotateIcon,
                modifier = cardModifier,
                valueColor = resolvedValueColor,
            )
    }
}
