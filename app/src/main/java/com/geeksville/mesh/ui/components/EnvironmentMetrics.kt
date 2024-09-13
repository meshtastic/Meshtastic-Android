package com.geeksville.mesh.ui.components

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.ui.components.ChartConstants.ENVIRONMENT_METRICS_COLORS
import com.geeksville.mesh.ui.components.ChartConstants.LEFT_CHART_SPACING
import com.geeksville.mesh.ui.components.ChartConstants.MS_PER_SEC
import com.geeksville.mesh.ui.components.ChartConstants.TIME_FORMAT


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
private fun EnvironmentMetricsChart(modifier: Modifier = Modifier, telemetries: List<Telemetry>) {

    ChartHeader(amount = telemetries.size)
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
