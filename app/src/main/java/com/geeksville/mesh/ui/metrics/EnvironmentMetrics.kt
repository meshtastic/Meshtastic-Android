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

package com.geeksville.mesh.ui.metrics

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Environment
import com.geeksville.mesh.model.EnvironmentGraphingData
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.TimeFrame
import com.geeksville.mesh.ui.common.components.IaqDisplayMode
import com.geeksville.mesh.ui.common.components.IndoorAirQuality
import com.geeksville.mesh.ui.common.components.OptionLabel
import com.geeksville.mesh.ui.common.components.SlidingSelector
import com.geeksville.mesh.ui.common.theme.Pink
import com.geeksville.mesh.ui.common.theme.Purple
import com.geeksville.mesh.ui.metrics.CommonCharts.DATE_TIME_FORMAT
import com.geeksville.mesh.ui.metrics.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.util.GraphUtil.createPath
import com.geeksville.mesh.util.GraphUtil.drawPathWithGradient
import com.geeksville.mesh.util.UnitConversions.celsiusToFahrenheit

@Suppress("MagicNumber")
private enum class Environment(val color: Color) {
    TEMPERATURE(Color.Red),
    RELATIVE_HUMIDITY(Color.Blue),
    SOIL_TEMPERATURE(Pink),
    SOIL_MOISTURE(Purple),
    BAROMETRIC_PRESSURE(Color.Green),
    GAS_RESISTANCE(Color.Yellow),
    IAQ(Color.Magenta)
}

private const val CHART_WEIGHT = 1f
private const val Y_AXIS_WEIGHT = 0.1f
// EnvironmentMetrics can have 1 or 2 Y-axis labels depending on whether barometric pressure is plotted
// We'll calculate this dynamically in the chart function

private val LEGEND_DATA_1 = listOf(
    LegendData(
        nameRes = R.string.temperature,
        color = Environment.TEMPERATURE.color,
        isLine = true
    ),
    LegendData(
        nameRes = R.string.humidity,
        color = Environment.HUMIDITY.color,
        isLine = true
    ),
)
private val LEGEND_DATA_2 = listOf(
    LegendData(
        nameRes = R.string.iaq,
        color = Environment.IAQ.color,
        isLine = true
    ),
    LegendData(
        nameRes = R.string.baro_pressure,
        color = Environment.BAROMETRIC_PRESSURE.color,
        isLine = true
    )
)
private val LEGEND_DATA_3 = listOf(
    LegendData(
        nameRes = R.string.soil_temperature,
        color = Environment.SOIL_TEMPERATURE.color,
        isLine = true
    ),
    LegendData(
        nameRes = R.string.soil_moisture,
        color = Environment.SOIL_MOISTURE.color,
        isLine = true
    ),
)

@Composable
fun EnvironmentMetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val graphData = environmentState.environmentMetricsFiltered(selectedTimeFrame, state.isFahrenheit)
    val data = graphData.metrics

    val processedTelemetries: List<Telemetry> = if (state.isFahrenheit) {
        data.map { telemetry ->
            val temperatureFahrenheit =
                celsiusToFahrenheit(telemetry.environmentMetrics.temperature)
            val soilTemperatureFahrenheit =
                celsiusToFahrenheit(telemetry.environmentMetrics.soilTemperature)
            telemetry.copy {
                environmentMetrics = telemetry.environmentMetrics.copy {
                    temperature = temperatureFahrenheit }
                environmentMetrics = telemetry.environmentMetrics.copy {
                    soilTemperature = soilTemperatureFahrenheit }
            }
        }
    } else {
        data
    }

    var displayInfoDialog by remember { mutableStateOf(false) }
    Column {
        if (displayInfoDialog) {
            LegendInfoDialog(
                pairedRes = listOf(
                    Pair(R.string.iaq, R.string.iaq_definition)
                ),
                onDismiss = { displayInfoDialog = false }
            )
        }

        EnvironmentMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            telemetries = processedTelemetries.reversed(),
            graphData = graphData,
            selectedTimeFrame,
            promptInfoDialog = { displayInfoDialog = true }
        )

        SlidingSelector(
            TimeFrame.entries.toList(),
            selectedTimeFrame,
            onOptionSelected = { viewModel.setTimeFrame(it) }
        ) {
            OptionLabel(stringResource(it.strRes))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(processedTelemetries) { telemetry ->
                EnvironmentMetricsCard(telemetry, state.isFahrenheit)
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Suppress("LongMethod")
@Composable
private fun EnvironmentMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    graphData: EnvironmentGraphingData,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }

    val (oldest, newest) = graphData.times
    val timeDiff = newest - oldest

    val scrollState = rememberScrollState()
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val dp by remember(key1 = selectedTime) {
        mutableStateOf(selectedTime.dp(screenWidth, time = timeDiff.toLong()))
    }

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

    TimeLabels(
        oldest = visibleTimeRange.first,
        newest = visibleTimeRange.second
    )

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colorScheme.onSurface

    val (rightMin, rightMax) = graphData.rightMinMax
    val (pressureMin, pressureMax) = graphData.leftMinMax
    var min = rightMin
    var diff = rightMax - rightMin

    Row {
        if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) {
            YAxisLabels(
                modifier = modifier.weight(weight = Y_AXIS_WEIGHT),
                Environment.BAROMETRIC_PRESSURE.color,
                minValue = pressureMin,
                maxValue = pressureMax
            )
        }
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier
                .horizontalScroll(state = scrollState, reverseScrolling = true)
                .weight(weight = 1f)
        ) {

            HorizontalLinesOverlay(
                modifier.width(dp),
                lineColors = List(size = 5) { graphColor }
            )

            TimeAxisOverlay(
                modifier = modifier.width(dp),
                oldest = oldest,
                newest = newest,
                selectedTime.lineInterval()
            )

            Canvas(modifier = modifier.width(dp)) {
                val height = size.height
                val width = size.width

                var index: Int
                var first: Int
                for (metric in Environment.entries) {

                    if (!shouldPlot[metric.ordinal]) {
                        continue
                    }
                    if (metric == Environment.BAROMETRIC_PRESSURE) {
                        diff = pressureMax - pressureMin
                        min = pressureMin
                    }
                    index = 0
                    while (index < telemetries.size) {
                        first = index
                        val path = Path()
                        index = createPath(
                            telemetries = telemetries,
                            index = index,
                            path = path,
                            oldestTime = oldest,
                            timeRange = timeDiff,
                            width = width,
                            timeThreshold = selectedTime.timeThreshold()
                        ) { i ->
                            val telemetry = telemetries.getOrNull(i) ?: telemetries.last()
                            val ratio = (metric.getValue(telemetry) - min) / diff
                            val y = height - (ratio * height)
                            return@createPath y
                        }
                        drawPathWithGradient(
                            path = path,
                            color = metric.color,
                            height = height,
                            x1 = ((telemetries[index - 1].time - oldest).toFloat() / timeDiff) * width,
                            x2 = ((telemetries[first].time - oldest).toFloat() / timeDiff) * width
                        )
                    }
                }
            }
        }
        YAxisLabels(
            modifier = modifier.weight(weight = Y_AXIS_WEIGHT),
            graphColor,
            minValue = rightMin,
            maxValue = rightMax
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Legend(LEGEND_DATA_1, displayInfoIcon = false)
    Legend(LEGEND_DATA_3, displayInfoIcon = false)
    Legend(LEGEND_DATA_2, promptInfoDialog = promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

@Suppress("LongMethod", "MagicNumber")
@Composable
private fun EnvironmentMetricsCard(telemetry: Telemetry, environmentDisplayFahrenheit: Boolean) {
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
                            text = DATE_TIME_FORMAT.format(time),
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            fontSize = MaterialTheme.typography.labelLarge.fontSize
                        )
                        val textFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
                        Text(
                            text = textFormat.format(
                                stringResource(id = R.string.temperature),
                                envMetrics.temperature
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize
                        )
                        if (envMetrics.barometricPressure > 0) {
                            Text(
                                text = "%.2f hPa".format(envMetrics.barometricPressure),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.labelLarge.fontSize
                            )
                        }
                    }

                    /* Soil Moisture and Soil Temperature */
                    val soilMoistureRange = 0..100
                    if (telemetry.environmentMetrics.hasSoilTemperature() ||
                        telemetry.environmentMetrics.soilMoisture in soilMoistureRange) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val soilTemperatureTextFormat =
                                if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
                            val soilMoistureTextFormat = "%s %d%%"
                            Text(
                                text = soilMoistureTextFormat.format(
                                    stringResource(R.string.soil_moisture),
                                    envMetrics.soilMoisture
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.labelLarge.fontSize
                            )
                            Text(
                                text = soilTemperatureTextFormat.format(
                                    stringResource(R.string.soil_temperature),
                                    envMetrics.soilTemperature
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.labelLarge.fontSize
                            )
                        }
                    }

                    if (telemetry.environmentMetrics.hasIaq()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        /* Air Quality */
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,

                        ) {
                            Text(
                                text = stringResource(R.string.iaq),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.labelLarge.fontSize
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IndoorAirQuality(
                                iaq = telemetry.environmentMetrics.iaq,
                                displayMode = IaqDisplayMode.Dot
                            )
                        }
                    }
                }
            }
        }
    }
}
