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

package com.geeksville.mesh.ui.components

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
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.TimeFrame
import com.geeksville.mesh.ui.components.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.ui.components.CommonCharts.DATE_TIME_FORMAT
import com.geeksville.mesh.ui.components.CommonCharts.INFANTRY_BLUE
import com.geeksville.mesh.util.GraphUtil.createPath
import com.geeksville.mesh.util.GraphUtil.drawPathWithGradient

private enum class Environment(val color: Color) {
    TEMPERATURE(Color.Red),
    HUMIDITY(INFANTRY_BLUE),
    IAQ(Color.Green)
}
private val LEGEND_DATA = listOf(
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
    LegendData(
        nameRes = R.string.iaq,
        color = Environment.IAQ.color,
        isLine = true
    ),
)

@Composable
fun EnvironmentMetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val data = state.environmentMetricsFiltered(selectedTimeFrame)

    /* Convert Celsius to Fahrenheit */
    @Suppress("MagicNumber")
    fun celsiusToFahrenheit(celsius: Float): Float {
        return (celsius * 1.8F) + 32
    }

    val processedTelemetries: List<Telemetry> = if (state.isFahrenheit) {
        data.map { telemetry ->
            val temperatureFahrenheit =
                celsiusToFahrenheit(telemetry.environmentMetrics.temperature)
            telemetry.copy {
                environmentMetrics =
                    telemetry.environmentMetrics.copy { temperature = temperatureFahrenheit }
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

        /* Environment Metric Cards */
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(processedTelemetries) { telemetry ->
                EnvironmentMetricsCard(
                    telemetry,
                    state.isFahrenheit
                )
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun EnvironmentMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }
    val (oldest, newest) = remember(key1 = telemetries) {
        Pair(
            telemetries.minBy { it.time },
            telemetries.maxBy { it.time }
        )
    }
    val timeDiff = newest.time - oldest.time

    TimeLabels(
        oldest = oldest.time,
        newest = newest.time
    )

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface

    /* Grab the combined min and max for all data being plotted. */
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
    val (minIAQ, maxIAQ) = remember(key1 = telemetries) {
        Pair(
            telemetries.minBy { it.environmentMetrics.iaq },
            telemetries.maxBy { it.environmentMetrics.iaq }
        )
    }
    val min = minOf(
        minTemp.environmentMetrics.temperature,
        minHumidity.environmentMetrics.relativeHumidity,
        minIAQ.environmentMetrics.iaq.toFloat()
    )
    val max = maxOf(
        maxTemp.environmentMetrics.temperature,
        maxHumidity.environmentMetrics.relativeHumidity,
        maxIAQ.environmentMetrics.iaq.toFloat()
    )
    val diff = max - min

    val scrollState = rememberScrollState()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val dp by remember(key1 = selectedTime) {
        mutableStateOf(selectedTime.dp(screenWidth, time = timeDiff.toLong()))
    }

    Row {
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
                oldest = oldest.time,
                newest = newest.time,
                selectedTime.lineInterval()
            )

            Canvas(modifier = modifier.width(dp)) {
                val height = size.height
                val width = size.width

                /* Temperature */
                var index = 0
                var first: Int
                while (index < telemetries.size) {
                    first = index
                    val path = Path()
                    index = createPath(
                        telemetries = telemetries,
                        index = index,
                        path = path,
                        oldestTime = oldest.time,
                        timeRange = timeDiff,
                        width = width,
                        timeThreshold = selectedTime.timeThreshold()
                    ) { i ->
                        val telemetry = telemetries.getOrNull(i) ?: telemetries.last()
                        val ratio = (telemetry.environmentMetrics.temperature - min) / diff
                        val y = height - (ratio * height)
                        return@createPath y
                    }
                    drawPathWithGradient(
                        path = path,
                        color = Environment.TEMPERATURE.color,
                        height = height,
                        x1 = ((telemetries[index - 1].time - oldest.time).toFloat() / timeDiff) * width,
                        x2 = ((telemetries[first].time - oldest.time).toFloat() / timeDiff) * width
                    )
                }

                /* Relative Humidity */
                index = 0
                while (index < telemetries.size) {
                    first = index
                    val path = Path()
                    index = createPath(
                        telemetries = telemetries,
                        index = index,
                        path = path,
                        oldestTime = oldest.time,
                        timeRange = timeDiff,
                        width = width,
                        timeThreshold = selectedTime.timeThreshold()
                    ) { i ->
                        val telemetry = telemetries.getOrNull(i) ?: telemetries.last()
                        val ratio = (telemetry.environmentMetrics.relativeHumidity - min) / diff
                        val y = height - (ratio * height)
                        return@createPath y
                    }
                    drawPathWithGradient(
                        path = path,
                        color = Environment.HUMIDITY.color,
                        height = height,
                        x1 = ((telemetries[index - 1].time - oldest.time).toFloat() / timeDiff) * width,
                        x2 = ((telemetries[first].time - oldest.time).toFloat() / timeDiff) * width
                    )
                }

                /* Air Quality */
                index = 0
                while (index < telemetries.size) {
                    first = index
                    val path = Path()
                    index = createPath(
                        telemetries = telemetries,
                        index = index,
                        path = path,
                        oldestTime = oldest.time,
                        timeRange = timeDiff,
                        width = width,
                        timeThreshold = selectedTime.timeThreshold()
                    ) { i ->
                        val telemetry = telemetries.getOrNull(i) ?: telemetries.last()
                        val ratio = (telemetry.environmentMetrics.iaq - min) / diff
                        val y = height - (ratio * height)
                        return@createPath y
                    }
                    drawPathWithGradient(
                        path = path,
                        color = Environment.IAQ.color,
                        height = height,
                        x1 = ((telemetries[index - 1].time - oldest.time).toFloat() / timeDiff) * width,
                        x2 = ((telemetries[first].time - oldest.time).toFloat() / timeDiff) * width
                    )
                }
            }
        }
        YAxisLabels(
            modifier = modifier.weight(weight = .1f),
            graphColor,
            minValue = min,
            maxValue = max
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Legend(LEGEND_DATA, promptInfoDialog = promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

@Suppress("LongMethod")
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
                            fontSize = MaterialTheme.typography.button.fontSize
                        )
                        val textFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
                        Text(
                            text = textFormat.format(
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
                    if (telemetry.environmentMetrics.hasIaq()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        /* Air Quality */
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,

                        ) {
                            Text(
                                text = stringResource(R.string.iaq),
                                color = MaterialTheme.colors.onSurface,
                                fontSize = MaterialTheme.typography.button.fontSize
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
