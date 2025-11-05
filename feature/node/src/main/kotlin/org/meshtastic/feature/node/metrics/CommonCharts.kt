/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.node.metrics

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_MINUTE_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MAX_PERCENT_VALUE
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import java.text.DateFormat
import org.meshtastic.core.strings.R as Res

object CommonCharts {
    val DATE_TIME_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    val TIME_MINUTE_FORMAT: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    val DATE_TIME_MINUTE_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    const val MS_PER_SEC = 1000L
    const val MAX_PERCENT_VALUE = 100f
}

private const val LINE_ON = 10f
private const val LINE_OFF = 20f
private val TIME_FORMAT: DateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)
private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
private const val DATE_Y = 32f
private const val LINE_LIMIT = 4
private const val TEXT_PAINT_ALPHA = 192

data class LegendData(
    val nameRes: Int,
    val color: Color,
    val isLine: Boolean = false,
    val environmentMetric: Environment? = null,
)

@Composable
fun ChartHeader(amount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$amount ${stringResource(Res.string.logs)}",
            modifier = Modifier.wrapContentWidth(),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
    }
}

/**
 * Draws chart lines with respect to the Y-axis.
 *
 * @param lineColors A list of 5 [Color]s for the chart lines, 0 being the lowest line on the chart.
 */
@Composable
fun HorizontalLinesOverlay(modifier: Modifier, lineColors: List<Color>) {
    /* 100 is a good number to divide into quarters */
    val verticalSpacing = MAX_PERCENT_VALUE / LINE_LIMIT
    Canvas(modifier = modifier) {
        val lineStart = 0f
        val height = size.height
        val width = size.width
        /* Horizontal Lines */
        var lineY = 0f
        for (i in 0..LINE_LIMIT) {
            val ratio = lineY / MAX_PERCENT_VALUE
            val y = height - (ratio * height)
            drawLine(
                start = Offset(lineStart, y),
                end = Offset(width, y),
                color = lineColors[i],
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(LINE_ON, LINE_OFF), 0f),
            )
            lineY += verticalSpacing
        }
    }
}

/** Draws labels on the Y-axis with respect to the range. Defined by (`maxValue` - `minValue`). */
@Composable
fun YAxisLabels(modifier: Modifier, labelColor: Color, minValue: Float, maxValue: Float) {
    val range = maxValue - minValue
    val verticalSpacing = range / LINE_LIMIT
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val height = size.height

        /* Y Labels */
        val textPaint =
            Paint().apply {
                color = labelColor.toArgb()
                textAlign = Paint.Align.LEFT
                textSize = density.run { 12.dp.toPx() }
                typeface = setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                alpha = TEXT_PAINT_ALPHA
            }

        drawContext.canvas.nativeCanvas.apply {
            var label = minValue
            repeat(LINE_LIMIT + 1) {
                val ratio = (label - minValue) / range
                val y = height - (ratio * height)
                drawText("${label.toInt()}", 0f, y + 4.dp.toPx(), textPaint)
                label += verticalSpacing
            }
        }
    }
}

/** Draws the vertical lines to help the user relate the plotted data within a time frame. */
@Composable
fun TimeAxisOverlay(modifier: Modifier, oldest: Int, newest: Int, timeInterval: Long) {
    val range = newest - oldest
    val density = LocalDensity.current
    val lineColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val height = size.height
        val width = size.width

        /* Cut out the time remaining in order to place the lines on the dot. */
        val timeRemaining = oldest % timeInterval
        var current = oldest.toLong()
        current -= timeRemaining
        current += timeInterval

        val textPaint =
            Paint().apply {
                color = lineColor.toArgb()
                textAlign = Paint.Align.LEFT
                textSize = density.run { 12.dp.toPx() }
                typeface = setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                alpha = TEXT_PAINT_ALPHA
            }

        /* Vertical Lines with labels */
        drawContext.canvas.nativeCanvas.apply {
            while (current <= newest) {
                val ratio = (current - oldest).toFloat() / range
                val x = (ratio * width)
                drawLine(
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    color = lineColor,
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(LINE_ON, LINE_OFF), 0f),
                )

                /* Time */
                drawText(TIME_FORMAT.format(current * MS_PER_SEC), x, 0f, textPaint)
                /* Date */
                drawText(DATE_FORMAT.format(current * MS_PER_SEC), x, DATE_Y, textPaint)
                current += timeInterval
            }
        }
    }
}

/** Draws the `oldest` and `newest` times for the respective telemetry data. Expects time in seconds. */
@Composable
fun TimeLabels(oldest: Int, newest: Int) {
    Row {
        Text(
            text = DATE_TIME_MINUTE_FORMAT.format(oldest * MS_PER_SEC),
            modifier = Modifier.wrapContentWidth(),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = DATE_TIME_MINUTE_FORMAT.format(newest * MS_PER_SEC),
            modifier = Modifier.wrapContentWidth(),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = 12.sp,
        )
    }
}

/**
 * Creates the legend that identifies the colors used for the graph.
 *
 * @param legendData A list containing the `LegendData` to build the labels.
 * @param promptInfoDialog Executes when the user presses the info icon.
 */
@Composable
fun Legend(legendData: List<LegendData>, displayInfoIcon: Boolean = true, promptInfoDialog: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.weight(1f))
        legendData.forEachIndexed { index, data ->
            LegendLabel(text = stringResource(data.nameRes), color = data.color, isLine = data.isLine)

            if (index != legendData.lastIndex) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (displayInfoIcon) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Info,
                modifier = Modifier.clickable { promptInfoDialog() },
                contentDescription = stringResource(Res.string.info),
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Displays a dialog with information about the legend items.
 *
 * @param pairedRes A list of `Pair`s containing (term, definition).
 * @param onDismiss Executes when the user presses the close button.
 */
@Composable
fun LegendInfoDialog(pairedRes: List<Pair<Int, Int>>, onDismiss: () -> Unit) {
    AlertDialog(
        title = {
            Text(
                text = stringResource(Res.string.info),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column {
                for (pair in pairedRes) {
                    Text(
                        text = stringResource(pair.first),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        textDecoration = TextDecoration.Underline,
                    )
                    Text(text = stringResource(pair.second), style = TextStyle.Default)

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun LegendLabel(text: String, color: Color, isLine: Boolean = false) {
    Canvas(modifier = Modifier.size(4.dp)) {
        if (isLine) {
            drawLine(
                color = color,
                start = Offset(x = 0f, y = size.height / 2f),
                end = Offset(x = 16f, y = size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            drawCircle(color = color)
        }
    }
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
    )
}

@Preview
@Composable
private fun LegendPreview() {
    val data =
        listOf(
            LegendData(nameRes = Res.string.rssi, color = Color.Red),
            LegendData(nameRes = Res.string.snr, color = Color.Green),
        )
    Legend(legendData = data, promptInfoDialog = {})
}
