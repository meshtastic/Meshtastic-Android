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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.baro_pressure
import org.meshtastic.core.strings.humidity
import org.meshtastic.core.strings.iaq
import org.meshtastic.core.strings.lux
import org.meshtastic.core.strings.soil_moisture
import org.meshtastic.core.strings.soil_temperature
import org.meshtastic.core.strings.temperature
import org.meshtastic.core.strings.uv_lux
import org.meshtastic.feature.node.metrics.GraphUtil.createPath
import org.meshtastic.feature.node.metrics.GraphUtil.drawPathWithGradient
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.TelemetryProtos.Telemetry

private const val CHART_WEIGHT = 1f
private const val Y_AXIS_WEIGHT = 0.1f

@Suppress("MagicNumber")
private val LEGEND_DATA_1 =
    listOf(
        LegendData(
            nameRes = Res.string.temperature,
            color = Environment.TEMPERATURE.color,
            isLine = true,
            environmentMetric = Environment.TEMPERATURE,
        ),
        LegendData(
            nameRes = Res.string.humidity,
            color = Environment.HUMIDITY.color,
            isLine = true,
            environmentMetric = Environment.HUMIDITY,
        ),
    )
private val LEGEND_DATA_2 =
    listOf(
        LegendData(
            nameRes = Res.string.iaq,
            color = Environment.IAQ.color,
            isLine = true,
            environmentMetric = Environment.IAQ,
        ),
        LegendData(
            nameRes = Res.string.baro_pressure,
            color = Environment.BAROMETRIC_PRESSURE.color,
            isLine = true,
            environmentMetric = Environment.BAROMETRIC_PRESSURE,
        ),
        LegendData(
            nameRes = Res.string.lux,
            color = Environment.LUX.color,
            isLine = true,
            environmentMetric = Environment.LUX,
        ),
        LegendData(
            nameRes = Res.string.uv_lux,
            color = Environment.UV_LUX.color,
            isLine = true,
            environmentMetric = Environment.UV_LUX,
        ),
    )

private val LEGEND_DATA_3 =
    listOf(
        LegendData(
            nameRes = Res.string.soil_temperature,
            color = Environment.SOIL_TEMPERATURE.color,
            isLine = true,
            environmentMetric = Environment.SOIL_TEMPERATURE,
        ),
        LegendData(
            nameRes = Res.string.soil_moisture,
            color = Environment.SOIL_MOISTURE.color,
            isLine = true,
            environmentMetric = Environment.SOIL_MOISTURE,
        ),
    )

@Composable
fun EnvironmentMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    graphData: EnvironmentGraphingData,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit,
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }

    val (oldest, newest) = graphData.times
    val timeDiff = newest - oldest

    val scrollState = rememberScrollState()
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val dp by remember(key1 = selectedTime) { mutableStateOf(selectedTime.dp(screenWidth, time = timeDiff.toLong())) }

    val shouldPlot = graphData.shouldPlot

    // Calculate visible time range based on scroll position and chart width
    val visibleTimeRange = run {
        val totalWidthPx = with(LocalDensity.current) { dp.toPx() }
        val scrollPx = scrollState.value.toFloat()
        // Calculate chart width ratio dynamically based on whether barometric pressure is plotted
        val yAxisCount = if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) 2 else 1
        val chartWidthRatio = CHART_WEIGHT / (CHART_WEIGHT + (Y_AXIS_WEIGHT * yAxisCount))
        val visibleWidthPx = screenWidth * chartWidthRatio
        val leftRatio = (scrollPx / totalWidthPx).coerceIn(0f, 1f)
        val rightRatio = ((scrollPx + visibleWidthPx) / totalWidthPx).coerceIn(0f, 1f)
        // With reverseScrolling = true, scrolling right shows older data (left side of chart)
        val visibleOldest = oldest + (timeDiff * (1f - rightRatio)).toInt()
        val visibleNewest = oldest + (timeDiff * (1f - leftRatio)).toInt()
        visibleOldest to visibleNewest
    }

    TimeLabels(oldest = visibleTimeRange.first, newest = visibleTimeRange.second)

    Row(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        BarometricPressureYAxisLabel(
            modifier = Modifier.weight(Y_AXIS_WEIGHT).fillMaxHeight(),
            shouldPlotBarometricPressure = shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal],
            minValue = graphData.leftMinMax.first,
            maxValue = graphData.leftMinMax.second,
        )
        ChartContent(
            modifier = Modifier.weight(CHART_WEIGHT).fillMaxHeight(),
            scrollState = scrollState,
            dp = dp,
            oldest = oldest,
            newest = newest,
            selectedTime = selectedTime,
            telemetries = telemetries,
            graphData = graphData,
            rightMin = graphData.rightMinMax.first,
            rightMax = graphData.rightMinMax.second,
            timeDiff = timeDiff,
        )
        YAxisLabels(
            modifier = Modifier.weight(Y_AXIS_WEIGHT).fillMaxHeight(),
            MaterialTheme.colorScheme.onSurface,
            minValue = graphData.rightMinMax.first,
            maxValue = graphData.rightMinMax.second,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    MetricLegends(graphData = graphData, promptInfoDialog = promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

@Suppress("detekt:LongMethod")
@Composable
private fun MetricPlottingCanvas(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    graphData: EnvironmentGraphingData,
    selectedTime: TimeFrame,
    oldest: Int,
    timeDiff: Int,
    rightMin: Float,
    rightMax: Float,
) {
    val (pressureMin, pressureMax) = graphData.leftMinMax
    val shouldPlot = graphData.shouldPlot
    val graphColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val height = size.height
        val width = size.width

        var min: Float
        var diff: Float
        var index: Int
        var first: Int
        for (metric in Environment.entries) {
            if (!shouldPlot[metric.ordinal]) {
                continue
            }
            if (metric == Environment.BAROMETRIC_PRESSURE) {
                diff = pressureMax - pressureMin
                min = pressureMin
            } else { // Reset for other metrics to use rightMin/rightMax
                min = rightMin
                diff = rightMax - rightMin
            }
            index = 0
            while (index < telemetries.size) {
                first = index
                val path = Path()
                index =
                    createPath(
                        telemetries = telemetries,
                        index = index,
                        path = path,
                        oldestTime = oldest,
                        timeRange = timeDiff,
                        width = width,
                        timeThreshold = selectedTime.timeThreshold(),
                    ) { i ->
                        val telemetry = telemetries.getOrNull(i) ?: telemetries.last()
                        val rawValue = metric.getValue(telemetry) // This is Float?

                        // Default to 0f if the actual value is null or NaN. This is a reasonable default for
                        // lux.
                        val pointValue =
                            if (rawValue != null && !rawValue.isNaN()) {
                                rawValue
                            } else {
                                0f
                            }

                        // Use 'min' and 'diff' from the outer scope, which are specific to the current metric's
                        // scale group.
                        val currentMin = min
                        // Avoid division by zero if all values in the current y-axis range are the same.
                        val currentDiff = if (diff == 0f) 1f else diff

                        val ratio = (pointValue - currentMin) / currentDiff
                        var y = height - (ratio * height)

                        // Final check to ensure y is a valid, plottable coordinate.
                        if (y.isNaN() || y.isInfinite()) {
                            y = height // Default to the bottom of the chart if calculation still results in an
                            // invalid number.
                        } else {
                            y = y.coerceIn(0f, height) // Clamp to chart bounds to be safe.
                        }
                        return@createPath y
                    }
                drawPathWithGradient(
                    path = path,
                    color = metric.color,
                    height = height,
                    x1 = ((telemetries[index - 1].time - oldest).toFloat() / timeDiff) * width,
                    x2 = ((telemetries[first].time - oldest).toFloat() / timeDiff) * width,
                )
            }
        }
    }
}

@Composable
private fun BarometricPressureYAxisLabel(
    modifier: Modifier,
    shouldPlotBarometricPressure: Boolean,
    minValue: Float,
    maxValue: Float,
) {
    if (shouldPlotBarometricPressure) {
        YAxisLabels(
            modifier = modifier,
            Environment.BAROMETRIC_PRESSURE.color,
            minValue = minValue,
            maxValue = maxValue,
        )
    }
}

@Composable
private fun ChartContent(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    dp: Dp,
    oldest: Int,
    newest: Int,
    selectedTime: TimeFrame,
    telemetries: List<Telemetry>,
    graphData: EnvironmentGraphingData,
    rightMin: Float,
    rightMax: Float,
    timeDiff: Int,
) {
    val graphColor = MaterialTheme.colorScheme.onSurface

    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier.horizontalScroll(state = scrollState, reverseScrolling = true),
    ) {
        HorizontalLinesOverlay(modifier.width(dp), lineColors = List(size = 5) { graphColor })

        TimeAxisOverlay(modifier = modifier.width(dp), oldest = oldest, newest = newest, selectedTime.lineInterval())

        MetricPlottingCanvas(
            modifier = modifier.width(dp),
            telemetries = telemetries,
            graphData = graphData,
            selectedTime = selectedTime,
            oldest = oldest,
            timeDiff = timeDiff,
            rightMin = rightMin,
            rightMax = rightMax,
        )
    }
}

@Composable
private fun MetricLegends(graphData: EnvironmentGraphingData, promptInfoDialog: () -> Unit) {
    Legend(LEGEND_DATA_1.filter { graphData.shouldPlot[it.environmentMetric?.ordinal ?: 0] }, displayInfoIcon = false)
    Legend(LEGEND_DATA_3.filter { graphData.shouldPlot[it.environmentMetric?.ordinal ?: 0] }, displayInfoIcon = false)
    Legend(
        LEGEND_DATA_2.filter { graphData.shouldPlot[it.environmentMetric?.ordinal ?: 0] },
        promptInfoDialog = promptInfoDialog,
    )
}

// private const val LINE_ON = 10f
// private const val LINE_OFF = 20f
// private val TIME_FORMAT: DateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)
// private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
// private const val DATE_Y = 32f
// private const val LINE_LIMIT = 4
// private const val TEXT_PAINT_ALPHA = 192
