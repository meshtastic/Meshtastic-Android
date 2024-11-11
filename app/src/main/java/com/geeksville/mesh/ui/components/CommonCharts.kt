package com.geeksville.mesh.ui.components

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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.components.CommonCharts.LINE_LIMIT
import com.geeksville.mesh.ui.components.CommonCharts.TEXT_PAINT_ALPHA
import com.geeksville.mesh.ui.components.CommonCharts.TIME_FORMAT
import com.geeksville.mesh.ui.components.CommonCharts.LEFT_LABEL_SPACING
import java.text.DateFormat

object CommonCharts {
    val TIME_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    const val X_AXIS_SPACING = 8f
    const val LEFT_LABEL_SPACING = 36
    const val MS_PER_SEC = 1000.0f
    const val LINE_LIMIT = 4
    const val TEXT_PAINT_ALPHA = 192
}

private const val LINE_ON = 10f
private const val LINE_OFF = 20f

data class LegendData(val nameRes: Int, val color: Color, val isLine: Boolean = false)

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
 *
 * @param labelColor The color to be used for the Y labels.
 * @param lineColors A list of 5 `Color`s for the chart lines, 0 being the lowest line on the chart.
 * @param leaveSpace When true the lines will leave space for Y labels on the left side of the graph.
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
    oldest: Float,
    newest: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = TIME_FORMAT.format(oldest),
            modifier = Modifier.wrapContentWidth(),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = TIME_FORMAT.format(newest),
            modifier = Modifier.wrapContentWidth(),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = 12.sp
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
fun Legend(legendData: List<LegendData>, promptInfoDialog: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        legendData.forEachIndexed { index, data ->
            LegendLabel(
                text = stringResource(data.nameRes),
                color = data.color,
                isLine = data.isLine
            )

            if (index != legendData.lastIndex) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Default.Info,
            modifier = Modifier.clickable { promptInfoDialog() },
            contentDescription = stringResource(R.string.info)
        )

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
                text = stringResource(R.string.info),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                for (pair in pairedRes) {
                    Text(
                        text = stringResource(pair.first),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        textDecoration = TextDecoration.Underline
                    )
                    Text(
                        text = stringResource(pair.second),
                        style = TextStyle.Default,
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background
    )
}

@Composable
private fun LegendLabel(text: String, color: Color, isLine: Boolean = false) {
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

@Preview
@Composable
private fun LegendPreview() {
    val data = listOf(
        LegendData(nameRes = R.string.rssi, color = Color.Red),
        LegendData(nameRes = R.string.snr, color = Color.Green)
    )
    Legend(legendData = data, promptInfoDialog = {})
}
