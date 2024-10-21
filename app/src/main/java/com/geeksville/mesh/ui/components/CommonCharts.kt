package com.geeksville.mesh.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.components.CommonCharts.LINE_LIMIT
import com.geeksville.mesh.ui.components.CommonCharts.TEXT_PAINT_ALPHA
import com.geeksville.mesh.ui.components.CommonCharts.TIME_FORMAT
import com.geeksville.mesh.ui.components.CommonCharts.LEFT_LABEL_SPACING
import java.text.DateFormat


object CommonCharts {
    val TIME_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    const val LEFT_CHART_SPACING = 8f
    const val LEFT_LABEL_SPACING = 36
    const val MS_PER_SEC = 1000.0f
    const val LINE_LIMIT = 4
    const val TEXT_PAINT_ALPHA = 192
}

private const val LINE_ON = 10f
private const val LINE_OFF = 20f


@Composable
fun ChartHeader(amount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$amount ${stringResource(R.string.logs)}",
            modifier = Modifier.wrapContentWidth(),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = MaterialTheme.typography.button.fontSize
        )
    }
}

/**
 * Draws chart lines and labels with respect to the Y-axis range; defined by (`maxValue` - `minValue`).
 * Assumes `lineColors` is a list of 5 `Color`s with index 0 being the lowest line on the chart.
 * `leaveSpace` when true the lines will leave space for Y labels on the left side of the graph.
 */
@Composable
fun ChartOverlay(
    modifier: Modifier,
    labelColor: Color,
    lineColors: List<Color>,
    minValue: Float,
    maxValue: Float,
    leaveSpace: Boolean = false
) {
    val range = maxValue - minValue
    val verticalSpacing = range / LINE_LIMIT
    val density = LocalDensity.current
    Canvas(modifier = modifier) {

        val lineStart = if (leaveSpace) LEFT_LABEL_SPACING.dp.toPx() else 0f
        val height = size.height
        val width = size.width - 28.dp.toPx()

        /* Horizontal Lines */
        var lineY = minValue
        for (i in 0..LINE_LIMIT) {
            val ratio = (lineY - minValue) / range
            val y = height - (ratio * height)
            drawLine(
                start = Offset(lineStart, y),
                end = Offset(width, y),
                color = lineColors[i],
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(LINE_ON, LINE_OFF), 0f)
            )
            lineY += verticalSpacing
        }

        /* Y Labels */

        val textPaint = Paint().apply {
            color = labelColor.toArgb()
            textAlign = Paint.Align.LEFT
            textSize = density.run { 12.dp.toPx() }
            typeface = setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            alpha = TEXT_PAINT_ALPHA
        }
        drawContext.canvas.nativeCanvas.apply {
            var label = minValue
            for (i in 0..LINE_LIMIT) {
                val ratio = (label - minValue) / range
                val y = height - (ratio * height)
                drawText(
                    "${label.toInt()}",
                    width + 4.dp.toPx(),
                    y + 4.dp.toPx(),
                    textPaint
                )
                label += verticalSpacing
            }
        }
    }
}

/**
 * Draws the `oldest` and `newest` times for the respective telemetry data.
 * Expects time in milliseconds
 */
@Composable
fun TimeLabels(
    modifier: Modifier,
    graphColor: Color,
    oldest: Float,
    newest: Float
) {
    val density = LocalDensity.current
    Canvas(modifier = modifier) {

        val textPaint = Paint().apply {
            color = graphColor.toArgb()
            textAlign = Paint.Align.LEFT
            textSize = density.run { 12.dp.toPx() }
            typeface = setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            alpha = TEXT_PAINT_ALPHA
        }

        drawContext.canvas.nativeCanvas.apply {
            drawText(
                TIME_FORMAT.format(oldest),
                8.dp.toPx(),
                12.dp.toPx(),
                textPaint
            )
            drawText(
                TIME_FORMAT.format(newest),
                size.width - 140.dp.toPx(),
                12.dp.toPx(),
                textPaint
            )
        }
    }
}

@Composable
fun LegendLabel(text: String, color: Color, isLine: Boolean = false) {
    Canvas(
        modifier = Modifier.size(4.dp)
    ) {
        if (isLine) {
            drawLine(
                color = color,
                start = Offset(x = 0f, y = size.height / 2f),
                end = Offset(x = 16f, y = size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            drawCircle(
                color = color
            )
        }
    }
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = text,
        color = MaterialTheme.colors.onSurface,
        fontSize = MaterialTheme.typography.button.fontSize,
    )
}
