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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.info
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.snr
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.time.Duration.Companion.days

object CommonCharts {
    const val MAX_PERCENT_VALUE = 100f
    const val SCROLL_BIAS = 0.5f

    /**
     * A dynamic [CartesianValueFormatter] that adjusts the time format based on the total data span
     * ([CartesianRanges.xLength]).
     *
     * Since chart data is already filtered by [TimeFrame], `xLength` approximates the visible window. Vico's formatter
     * receives [CartesianMeasuringContext] during measurement passes — **not** [CartesianDrawingContext] — so
     * `context.zoom` is unavailable and we intentionally avoid it.
     *
     * | Data span | Format                 | Example          |
     * |-----------|------------------------|------------------|
     * | ≤ 1 hour  | Time with seconds      | 3:45:12 PM       |
     * | ≤ 2 days  | Time only              | 3:45 PM          |
     * | ≤ 14 days | Date + time (two-line) | 4/9/26 ↵ 3:45 PM |
     * | > 14 days | Date only              | 4/9/26           |
     */
    val dynamicTimeFormatter = CartesianValueFormatter { context, value, _ ->
        val timestampMillis = (value * MS_PER_SEC.toDouble()).toLong()
        val dataSpanSeconds = context.ranges.xLength

        when {
            dataSpanSeconds <= TimeConstants.ONE_HOUR.inWholeSeconds ->
                DateFormatter.formatTimeWithSeconds(timestampMillis)

            dataSpanSeconds <= 2.days.inWholeSeconds -> DateFormatter.formatTime(timestampMillis)

            dataSpanSeconds <= 14.days.inWholeSeconds -> {
                val dateStr = DateFormatter.formatDate(timestampMillis)
                val timeStr = DateFormatter.formatTime(timestampMillis)
                "$dateStr\n$timeStr"
            }

            else -> DateFormatter.formatDate(timestampMillis)
        }
    }

    /**
     * Shared bottom time axis used by all metric chart screens.
     *
     * Uses `spacing = 1` with `addExtremeLabelPadding = true` so Vico's built-in auto-thinning controls label density —
     * it measures label widths and automatically skips labels when they would overlap, adapting to both zoom level and
     * screen width.
     */
    @Composable
    fun rememberBottomTimeAxis(): HorizontalAxis<Axis.Position.Horizontal.Bottom> = HorizontalAxis.rememberBottom(
        label = ChartStyling.rememberAxisLabel(),
        valueFormatter = dynamicTimeFormatter,
        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { 1 }, addExtremeLabelPadding = true),
        labelRotationDegrees = LABEL_ROTATION_DEGREES,
    )

    private const val LABEL_ROTATION_DEGREES = 45f
}

data class LegendData(
    val nameRes: StringResource,
    val color: Color,
    val isLine: Boolean = false,
    val metricKey: Any? = null,
    /** When non-null, overrides the resolved [nameRes] string in the legend label. */
    val labelOverride: String? = null,
)

data class InfoDialogData(val titleRes: StringResource, val definitionRes: StringResource, val color: Color)

/**
 * Creates the legend that identifies the colors used for the graph.
 *
 * When [onToggle] is provided, each item renders as a Material 3 [FilterChip] so users can tap to show/hide chart
 * series. This provides proper M3 affordance (selected state styling, ripple, accessibility semantics). When [onToggle]
 * is null, a compact read-only legend is shown instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(
    legendData: List<LegendData>,
    modifier: Modifier = Modifier,
    hiddenSet: Set<Int> = emptySet(),
    onToggle: ((Int) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        legendData.forEachIndexed { index, data ->
            val isVisible = index !in hiddenSet
            val label = data.labelOverride ?: stringResource(data.nameRes)
            if (onToggle != null) {
                FilterChip(
                    selected = isVisible,
                    onClick = { onToggle(index) },
                    label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { LegendIndicator(color = data.color, isLine = data.isLine) },
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    LegendIndicator(color = data.color, isLine = data.isLine)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    )
                }
            }
        }
    }
}

/** Displays a dialog with information about the legend items. */
@Composable
fun LegendInfoDialog(infoData: List<InfoDialogData>, onDismiss: () -> Unit) {
    AlertDialog(
        icon = { Icon(imageVector = MeshtasticIcons.Info, contentDescription = null) },
        title = {
            Text(
                text = stringResource(Res.string.info),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (item in infoData) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MetricIndicator(item.color)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(item.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = item.color,
                            )
                        }
                        Text(
                            text = stringResource(item.definitionRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.close), fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

/** Draws a small colored line segment or circle to identify a chart series. */
@Composable
fun LegendIndicator(color: Color, isLine: Boolean = false) {
    Canvas(modifier = Modifier.size(height = 4.dp, width = if (isLine) 16.dp else 4.dp)) {
        if (isLine) {
            drawLine(
                color = color,
                start = Offset(x = 0f, y = size.height / 2f),
                end = Offset(x = size.width, y = size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            drawCircle(color = color)
        }
    }
}

@Composable
fun MetricIndicator(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(8.dp).clip(CircleShape).background(color))
}

@PreviewLightDark
@Suppress("unused") // Compose preview
@Composable
private fun LegendPreview() {
    val data =
        listOf(
            LegendData(nameRes = Res.string.rssi, color = Color.Red, isLine = true),
            LegendData(nameRes = Res.string.snr, color = Color.Green, isLine = true),
        )
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Read-only legend
            Legend(legendData = data)
            // Toggleable legend
            Legend(legendData = data, hiddenSet = setOf(1), onToggle = {})
        }
    }
}
