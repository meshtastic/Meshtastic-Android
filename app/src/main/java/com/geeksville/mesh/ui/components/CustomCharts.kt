@file:Suppress("TooManyFunctions")

package com.geeksville.mesh.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.ui.BatteryInfo
import com.geeksville.mesh.ui.components.ChartConstants.DEVICE_METRICS_COLORS
import com.geeksville.mesh.ui.components.ChartConstants.ENVIRONMENT_METRICS_COLORS
import com.geeksville.mesh.ui.components.ChartConstants.LEFT_CHART_SPACING
import com.geeksville.mesh.ui.components.ChartConstants.LINE_OFF
import com.geeksville.mesh.ui.components.ChartConstants.LINE_ON
import com.geeksville.mesh.ui.components.ChartConstants.TIME_FORMAT
import com.geeksville.mesh.ui.components.ChartConstants.MAX_PERCENT_VALUE
import com.geeksville.mesh.ui.components.ChartConstants.LINE_LIMIT
import com.geeksville.mesh.ui.components.ChartConstants.MS_PER_SEC
import com.geeksville.mesh.ui.components.ChartConstants.TEXT_PAINT_ALPHA
import com.geeksville.mesh.ui.theme.Orange
import java.text.DateFormat


private object ChartConstants {
    val DEVICE_METRICS_COLORS = listOf(Color.Green, Color.Magenta, Color.Cyan)
    val ENVIRONMENT_METRICS_COLORS = listOf(Color.Red, Color.Blue)
    val TIME_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    const val MAX_PERCENT_VALUE = 100f
    const val LINE_LIMIT = 4
    const val TEXT_PAINT_ALPHA = 192
    const val LINE_ON = 10f
    const val LINE_OFF = 20f
    const val LEFT_CHART_SPACING = 8f
    const val MS_PER_SEC = 1000.0f
}

@Composable
fun DeviceMetricsScreen(telemetries: List<Telemetry>) {
    Column {
        DeviceMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            telemetries
        )
        /* Device Metric Cards */
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(telemetries.reversed()) { telemetry -> DeviceMetricsCard(telemetry) }
        }
    }
}

@Composable
fun EnvironmentMetricsScreen(telemetries: List<Telemetry>) {
    Column {
        EnvironmentMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            telemetries = telemetries
        )

        /* Environment Metric Cards */
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(telemetries.reversed()) { telemetry -> EnvironmentMetricsCard(telemetry)}
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun DeviceMetricsChart(modifier: Modifier = Modifier, telemetries: List<Telemetry>) {

    ChartHeader(amount = telemetries.size, title = stringResource(R.string.device_metrics))
    if (telemetries.isEmpty())
        return

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface
    val spacing = LEFT_CHART_SPACING

    Box(contentAlignment = Alignment.TopStart) {

        ChartOverlay(modifier, graphColor, minValue = 0f, maxValue = 100f)

        /* Plot Battery Line, ChUtil, and AirUtilTx */
        Canvas(modifier = modifier) {

            val height = size.height
            val width = size.width - 28.dp.toPx()
            val spacePerEntry = (width - spacing) / telemetries.size
            val dataPointRadius = 2.dp.toPx()
            var lastX: Float
            val strokePath = Path().apply {
                for (i in telemetries.indices) {
                    val telemetry = telemetries[i]
                    val nextTelemetry = telemetries.getOrNull(i + 1) ?: telemetries.last()
                    val leftRatio = telemetry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE
                    val rightRatio = nextTelemetry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE

                    val x1 = spacing + i * spacePerEntry
                    val y1 = height - spacing - (leftRatio * height)

                    /* Channel Utilization */
                    val chUtilRatio = telemetry.deviceMetrics.channelUtilization / MAX_PERCENT_VALUE
                    val yChUtil = height - spacing - (chUtilRatio * height)
                    drawCircle(
                        color = DEVICE_METRICS_COLORS[1],
                        radius = dataPointRadius,
                        center = Offset(x1, yChUtil)
                    )

                    /* Air Utilization Transmit  */
                    val airUtilRatio = telemetry.deviceMetrics.airUtilTx / MAX_PERCENT_VALUE
                    val yAirUtil = height - spacing - (airUtilRatio * height)
                    drawCircle(
                        color = DEVICE_METRICS_COLORS[2],
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
                color = DEVICE_METRICS_COLORS[0],
                style = Stroke(
                    width = dataPointRadius,
                    cap = StrokeCap.Round
                )
            )
        }

        TimeLabels(
            modifier = modifier,
            graphColor = graphColor,
            oldest = telemetries.first().time * MS_PER_SEC,
            newest = telemetries.last().time * MS_PER_SEC
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    DeviceLegend()

    Spacer(modifier = Modifier.height(16.dp))
}

@Suppress("LongMethod")
@Composable
private fun EnvironmentMetricsChart(modifier: Modifier = Modifier, telemetries: List<Telemetry>) {

    ChartHeader(amount = telemetries.size, title = stringResource(R.string.environment_metrics))
    if (telemetries.isEmpty())
        return

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface
    val transparentTemperatureColor = remember { ENVIRONMENT_METRICS_COLORS[0].copy(alpha = 0.5f) }
    val transparentHumidityColor = remember { ENVIRONMENT_METRICS_COLORS[1].copy(alpha = 0.5f) }
    val spacing = LEFT_CHART_SPACING

    /* Since both temperature and humidity are being plotted we need a combined min and max. */
    val (minTemp, maxTemp) = remember(key1 = telemetries) {
        Pair(
            telemetries.minBy { it.environmentMetrics.temperature },
            telemetries.maxBy { it.environmentMetrics.temperature }
        )
    }
    val (minHumidity, maxHumidity) = remember(key1 = telemetries) {
        Pair(
            telemetries.minBy { it.environmentMetrics.relativeHumidity },
            telemetries.maxBy { it.environmentMetrics.relativeHumidity }
        )
    }
    val min = minOf(minTemp.environmentMetrics.temperature, minHumidity.environmentMetrics.relativeHumidity)
    val max = maxOf(maxTemp.environmentMetrics.temperature, maxHumidity.environmentMetrics.relativeHumidity)
    val diff = max - min

    Box(contentAlignment = Alignment.TopStart) {

        ChartOverlay(modifier = modifier, graphColor = graphColor, minValue = min, maxValue = max)

        /* Plot Temperature and Relative Humidity */
        Canvas(modifier = modifier) {

            val height = size.height
            val width = size.width - 28.dp.toPx()
            val spacePerEntry = (width - spacing) / telemetries.size

            /* Temperature */
            var lastTempX = 0f
            val temperaturePath = Path().apply {
                for (i in telemetries.indices) {
                    val envMetrics = telemetries[i].environmentMetrics
                    val nextEnvMetrics =
                        (telemetries.getOrNull(i + 1) ?: telemetries.last()).environmentMetrics
                    val leftRatio = (envMetrics.temperature - min) / diff
                    val rightRatio = (nextEnvMetrics.temperature - min) / diff

                    val x1 = spacing + i * spacePerEntry
                    val y1 = height - spacing - (leftRatio * height)

                    val x2 = spacing + (i + 1) * spacePerEntry
                    val y2 = height - spacing - (rightRatio * height)
                    if (i == 0) {
                        moveTo(x1, y1)
                    }
                    lastTempX = (x1 + x2) / 2f
                    quadraticBezierTo(
                        x1, y1, lastTempX, (y1 + y2) / 2f
                    )
                }
            }

            val fillPath = android.graphics.Path(temperaturePath.asAndroidPath())
                .asComposePath()
                .apply {
                    lineTo(lastTempX, height - spacing)
                    lineTo(spacing, height - spacing)
                    close()
                }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        transparentTemperatureColor,
                        Color.Transparent
                    ),
                    endY = height - spacing
                ),
            )

            drawPath(
                path = temperaturePath,
                color = ENVIRONMENT_METRICS_COLORS[0],
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            /* Relative Humidity */
            var lastHumidityX = 0f
            val humidityPath = Path().apply {
                for (i in telemetries.indices) {
                    val envMetrics = telemetries[i].environmentMetrics
                    val nextEnvMetrics =
                        (telemetries.getOrNull(i + 1) ?: telemetries.last()).environmentMetrics
                    val leftRatio = (envMetrics.relativeHumidity - min) / diff
                    val rightRatio = (nextEnvMetrics.relativeHumidity - min) / diff

                    val x1 = spacing + i * spacePerEntry
                    val y1 = height - spacing - (leftRatio * height)

                    val x2 = spacing + (i + 1) * spacePerEntry
                    val y2 = height - spacing - (rightRatio * height)
                    if (i == 0) {
                        moveTo(x1, y1)
                    }
                    lastHumidityX = (x1 + x2) / 2f
                    quadraticBezierTo(
                        x1, y1, lastHumidityX, (y1 + y2) / 2f
                    )
                }
            }

            val fillHumidityPath = android.graphics.Path(humidityPath.asAndroidPath())
                .asComposePath()
                .apply {
                    lineTo(lastHumidityX, height - spacing)
                    lineTo(spacing, height - spacing)
                    close()
                }

            drawPath(
                path = fillHumidityPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        transparentHumidityColor,
                        Color.Transparent
                    ),
                    endY = height - spacing
                ),
            )

            drawPath(
                path = humidityPath,
                color = ENVIRONMENT_METRICS_COLORS[1],
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
        TimeLabels(
            modifier = modifier,
            graphColor = graphColor,
            oldest = telemetries.first().time * MS_PER_SEC,
            newest = telemetries.last().time * MS_PER_SEC
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    EnvironmentLegend()

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun DeviceMetricsCard(telemetry: Telemetry) {
    val deviceMetrics = telemetry.deviceMetrics
    val time = telemetry.time * MS_PER_SEC
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
                            text = TIME_FORMAT.format(time),
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            fontSize = MaterialTheme.typography.button.fontSize
                        )

                        BatteryInfo(
                            batteryLevel = deviceMetrics.batteryLevel,
                            voltage = deviceMetrics.voltage
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    /* Channel Utilization and Air Utilization Tx */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val text = "%s %.2f%%  %s %.2f%%".format(
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

@Suppress("LongMethod")
@Composable
private fun EnvironmentMetricsCard(telemetry: Telemetry) {
    val envMetrics = telemetry.environmentMetrics
    val time = telemetry.time * MS_PER_SEC
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
                    /* Time and Temperature */
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = TIME_FORMAT.format(time),
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            fontSize = MaterialTheme.typography.button.fontSize
                        )

                        Text(
                            text = "%s %.1f°C".format(
                                stringResource(id = R.string.temperature),
                                envMetrics.temperature
                            ),
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    /* Humidity and Barometric Pressure */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "%s %.2f%%".format(
                                stringResource(id = R.string.humidity),
                                envMetrics.relativeHumidity,
                            ),
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize
                        )
                        if (envMetrics.barometricPressure > 0) {
                            Text(
                                text = "%.2f hPa".format(envMetrics.barometricPressure),
                                color = MaterialTheme.colors.onSurface,
                                fontSize = MaterialTheme.typography.button.fontSize
                            )
                        }
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
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = MaterialTheme.typography.button.fontSize
        )
    }
}

/**
 * Draws chart lines and labels with respect to the Y-axis range; defined by (`maxValue` - `minValue`).
 */
@Composable
private fun ChartOverlay(
    modifier: Modifier,
    graphColor: Color,
    minValue: Float,
    maxValue: Float
) {
    val range = maxValue - minValue
    val verticalSpacing = range / LINE_LIMIT
    val density = LocalDensity.current
    Canvas(modifier = modifier) {

        val height = size.height
        val width = size.width - 28.dp.toPx()

        /* Horizontal Lines */
        var lineY = minValue
        for (i in 0..LINE_LIMIT) {
            val ratio = (lineY - minValue) / range
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
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(LINE_ON, LINE_OFF), 0f)
            )
            lineY += verticalSpacing
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
private fun TimeLabels(
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
private fun DeviceLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        LegendLabel(text = stringResource(R.string.battery), color = DEVICE_METRICS_COLORS[0], isLine = true)

        Spacer(modifier = Modifier.width(4.dp))

        LegendLabel(text = stringResource(R.string.channel_utilization), color = DEVICE_METRICS_COLORS[1])

        Spacer(modifier = Modifier.width(4.dp))

        LegendLabel(text = stringResource(R.string.air_utilization), color = DEVICE_METRICS_COLORS[2])

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EnvironmentLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        LegendLabel(text = stringResource(R.string.temperature), color = ENVIRONMENT_METRICS_COLORS[0], isLine = true)

        Spacer(modifier = Modifier.width(4.dp))

        LegendLabel(text = stringResource(R.string.humidity), color = ENVIRONMENT_METRICS_COLORS[1], isLine = true)

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
