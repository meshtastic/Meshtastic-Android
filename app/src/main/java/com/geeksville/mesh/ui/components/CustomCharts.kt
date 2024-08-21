package com.geeksville.mesh.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DataEntry
import com.geeksville.mesh.ui.BatteryInfo
import com.geeksville.mesh.ui.components.ChartConstants.COLORS
import com.geeksville.mesh.ui.components.ChartConstants.TIME_FORMAT
import com.geeksville.mesh.ui.components.ChartConstants.MAX_PERCENT_VALUE
import com.geeksville.mesh.ui.components.ChartConstants.PERCENT_LINE_LIMIT
import com.geeksville.mesh.ui.components.ChartConstants.PERCENT_VERTICAL_SPACING
import com.geeksville.mesh.ui.components.ChartConstants.TEXT_PAINT_ALPHA
import com.geeksville.mesh.ui.theme.Orange
import java.text.DateFormat


private object ChartConstants {
    val COLORS = listOf(Color.Green, Color.Magenta, Color.Cyan)
    val TIME_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    const val MAX_PERCENT_VALUE = 100f
    const val PERCENT_VERTICAL_SPACING = 25f
    const val PERCENT_LINE_LIMIT = 4
    const val TEXT_PAINT_ALPHA = 192
}

@Suppress("LongMethod")
@Composable
fun DeviceMetricsChart(modifier: Modifier = Modifier, data: List<DataEntry>) {

    if (data.isEmpty())
        return

    ChartHeader(amount = data.size, title = stringResource(R.string.device_metrics))

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface
    val spacing = 0f

    val (oldestMetrics, newestMetrics) = remember(key1 = data) {
        Pair(
            data.minBy { it.time },
            data.maxBy { it.time }
        )
    }

    Box(contentAlignment = Alignment.TopStart) {

        PercentageChartLayer(modifier = modifier, graphColor = graphColor)

        /* Plot Battery Line, ChUtil, and AirUtilTx */
        Canvas(modifier = modifier) {

            val height = size.height
            val width = size.width - 28.dp.toPx()

            val textPaint = Paint().apply {
                color = graphColor.toArgb()
                textAlign = Paint.Align.LEFT
                textSize = density.run { 12.dp.toPx() }
                typeface = setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                alpha = TEXT_PAINT_ALPHA
            }

            val spacePerEntry = (width - spacing) / data.size
            val dataPointRadius = 2.dp.toPx()
            var lastX: Float
            val strokePath = Path().apply {
                for (i in data.indices) {
                    val dataEntry = data[i]
                    val nextDataEntry = data.getOrNull(i + 1) ?: data.last()
                    val leftRatio = dataEntry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE
                    val rightRatio = nextDataEntry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE

                    val x1 = spacing + i * spacePerEntry
                    val y1 = height - spacing - (leftRatio * height)

                    /* Channel Utilization */
                    val chUtilRatio = dataEntry.deviceMetrics.channelUtilization / MAX_PERCENT_VALUE
                    val yChUtil = height - spacing - (chUtilRatio * height)
                    drawCircle(
                        color = COLORS[1],
                        radius = dataPointRadius,
                        center = Offset(x1, yChUtil)
                    )

                    /* Air Utilization Transmit  */
                    val airUtilRatio = dataEntry.deviceMetrics.airUtilTx / MAX_PERCENT_VALUE
                    val yAirUtil = height - spacing - (airUtilRatio * height)
                    drawCircle(
                        color = COLORS[2],
                        radius = dataPointRadius,
                        center = Offset(x1, yAirUtil)
                    )

                    val x2 = spacing + (i + 1) * spacePerEntry
                    val y2 = height - spacing - (rightRatio * height)
                    if (i == 0)
                        moveTo(x1, y1)

                    lastX = (x1 + x2) / 2f

                    quadraticBezierTo(x1, y1, lastX, (y1 + y2) / 2f)
                }
            }

            /* Battery Line */
            drawPath(
                path = strokePath,
                color = COLORS[0],
                style = Stroke(
                    width = dataPointRadius,
                    cap = StrokeCap.Round
                )
            )

            /* X - Labels: Time */
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    TIME_FORMAT.format(oldestMetrics.time),
                    8.dp.toPx(),
                    12.dp.toPx(),
                    textPaint
                )
                drawText(
                    TIME_FORMAT.format(newestMetrics.time),
                    width - 116.dp.toPx(),
                    12.dp.toPx(),
                    textPaint
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    ChartLegend() // TODO function will adapt for the specific chart

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun DeviceMetricsCard(dataEntry: DataEntry) {
    val deviceMetrics = dataEntry.deviceMetrics
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    /* Time, Battery, and Voltage */
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = TIME_FORMAT.format(dataEntry.time),
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            fontSize = MaterialTheme.typography.button.fontSize
                        )

                        BatteryInfo(
                            batteryLevel = deviceMetrics.batteryLevel,
                            voltage = deviceMetrics.voltage
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    /* Channel Utilization and Air Utilization Tx*/
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val text = "%s %.2f%% %s %.2f%%".format(
                            stringResource(R.string.channel_utilization),
                            deviceMetrics.channelUtilization,
                            stringResource(R.string.air_utilization),
                            deviceMetrics.airUtilTx
                        )
                        Text(
                            text = text,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartHeader(amount: Int, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$amount $title",
            modifier = Modifier.wrapContentWidth(),
            color = MaterialTheme.colors.onSurface,
            style = MaterialTheme.typography.body1
        )
    }
}

@Composable
private fun PercentageChartLayer(modifier: Modifier, graphColor: Color) {
    val density = LocalDensity.current
    Canvas(modifier = modifier) {

        val height = size.height
        val width = size.width - 28.dp.toPx()

        /* Horizontal Lines */
        var lineY = 0f
        for (i in 0..PERCENT_LINE_LIMIT) {
            val ratio = lineY / MAX_PERCENT_VALUE
            val y = height - (ratio * height)
            val color: Color = when (i) {
                1 -> Color.Red
                2 -> Orange
                else -> graphColor
            }
            drawLine(
                start = Offset(0f, y),
                end = Offset(width, y),
                color = color,
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 20f), 0f)
            )
            lineY += PERCENT_VERTICAL_SPACING
        }

        /* Y Labels */

        val textPaint = Paint().apply {
            color = graphColor.toArgb()
            textAlign = Paint.Align.LEFT
            textSize = density.run { 12.dp.toPx() }
            typeface = setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            alpha = TEXT_PAINT_ALPHA
        }
        drawContext.canvas.nativeCanvas.apply {
            var currentLabel = 0f
            for (i in 0..PERCENT_LINE_LIMIT) {
                val ratio = currentLabel / MAX_PERCENT_VALUE
                val y = height - (ratio * height)
                drawText(
                    "${currentLabel.toInt()}",
                    width + 4.dp.toPx(),
                    y + 4.dp.toPx(),
                    textPaint
                )
                currentLabel += PERCENT_VERTICAL_SPACING
            }
        }

    }
}

@Composable
private fun ChartLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {

        Spacer(modifier = Modifier.weight(1f))

        LegendLabel(text = stringResource(R.string.battery), color = COLORS[0], isLine = true)

        Spacer(modifier = Modifier.width(4.dp))

        LegendLabel(text = stringResource(R.string.channel_utilization), color = COLORS[1])

        Spacer(modifier = Modifier.width(4.dp))

        LegendLabel(text = stringResource(R.string.air_utilization), color = COLORS[2])

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LegendLabel(text: String, color: Color, isLine: Boolean = false) {
    Canvas(
        modifier = Modifier.size(4.dp)
    ) {
        if (isLine) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(16f, size.height / 2f),
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
