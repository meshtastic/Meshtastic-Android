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

import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.TimeFrame
import com.geeksville.mesh.ui.common.components.OptionLabel
import com.geeksville.mesh.ui.common.components.SlidingSelector
import com.geeksville.mesh.ui.common.theme.InfantryBlue
import com.geeksville.mesh.ui.metrics.CommonCharts.DATE_TIME_FORMAT
import com.geeksville.mesh.ui.metrics.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.util.GraphUtil
import com.geeksville.mesh.util.GraphUtil.createPath

@Suppress("MagicNumber")
private enum class Power(val color: Color, val min: Float, val max: Float) {
    CURRENT(InfantryBlue, -500f, 500f),
    VOLTAGE(Color.Red, 0f, 20f);

    /**
     * Difference between the metrics `max` and `min` values.
     */
    fun difference() = max - min
}

private enum class PowerChannel(@StringRes val strRes: Int) {
    ONE(R.string.channel_1),
    TWO(R.string.channel_2),
    THREE(R.string.channel_3)
}

private const val CHART_WEIGHT = 1f
private const val Y_AXIS_WEIGHT = 0.1f
private const val CHART_WIDTH_RATIO = CHART_WEIGHT / (CHART_WEIGHT + Y_AXIS_WEIGHT + Y_AXIS_WEIGHT)

private val LEGEND_DATA = listOf(
    LegendData(nameRes = R.string.current, color = Power.CURRENT.color, isLine = true),
    LegendData(nameRes = R.string.voltage, color = Power.VOLTAGE.color, isLine = true),
)

@Composable
fun PowerMetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    var selectedChannel by remember { mutableStateOf(PowerChannel.ONE) }
    val data = state.powerMetricsFiltered(selectedTimeFrame)

    Column {

        PowerMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            telemetries = data.reversed(),
            selectedTimeFrame,
            selectedChannel,
        )

        SlidingSelector(
            PowerChannel.entries.toList(),
            selectedChannel,
            onOptionSelected = { selectedChannel = it }
        ) {
            OptionLabel(stringResource(it.strRes))
        }
        Spacer(modifier = Modifier.height(2.dp))
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
            items(data) { telemetry -> PowerMetricsCard(telemetry) }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun PowerMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedTime: TimeFrame,
    selectedChannel: PowerChannel,
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

    val scrollState = rememberScrollState()
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val dp by remember(key1 = selectedTime) {
        mutableStateOf(selectedTime.dp(screenWidth, time = (newest.time - oldest.time).toLong()))
    }

    // Calculate visible time range based on scroll position and chart width
    val visibleTimeRange = run {
        val totalWidthPx = with(LocalDensity.current) { dp.toPx() }
        val scrollPx = scrollState.value.toFloat()
        // Calculate visible width based on actual weight distribution
        val visibleWidthPx = screenWidth * CHART_WIDTH_RATIO
        val leftRatio = (scrollPx / totalWidthPx).coerceIn(0f, 1f)
        val rightRatio = ((scrollPx + visibleWidthPx) / totalWidthPx).coerceIn(0f, 1f)
        // With reverseScrolling = true, scrolling right shows older data (left side of chart)
        val visibleOldest = oldest.time + (timeDiff * (1f - rightRatio)).toInt()
        val visibleNewest = oldest.time + (timeDiff * (1f - leftRatio)).toInt()
        visibleOldest to visibleNewest
    }

    TimeLabels(
        oldest = visibleTimeRange.first,
        newest = visibleTimeRange.second
    )

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colorScheme.onSurface
    val currentDiff = Power.CURRENT.difference()
    val voltageDiff = Power.VOLTAGE.difference()

    Row {
        YAxisLabels(
            modifier = modifier.weight(weight = Y_AXIS_WEIGHT),
            Power.CURRENT.color,
            minValue = Power.CURRENT.min,
            maxValue = Power.CURRENT.max,
        )
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier
                .horizontalScroll(state = scrollState, reverseScrolling = true)
                .weight(1f)
        ) {
            HorizontalLinesOverlay(
                modifier.width(dp),
                lineColors = List(size = 5) { graphColor },
            )

            TimeAxisOverlay(
                modifier.width(dp),
                oldest = oldest.time,
                newest = newest.time,
                selectedTime.lineInterval()
            )

            /* Plot */
            Canvas(modifier = modifier.width(dp)) {
                val width = size.width
                val height = size.height
                /* Voltage */
                var index = 0
                while (index < telemetries.size) {
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
                        val ratio = retrieveVoltage(selectedChannel, telemetry) / voltageDiff
                        val y = height - (ratio * height)
                        return@createPath y
                    }
                    drawPath(
                        path = path,
                        color = Power.VOLTAGE.color,
                        style = Stroke(
                            width = GraphUtil.RADIUS,
                            cap = StrokeCap.Round
                        )
                    )
                }
                /* Current */
                index = 0
                while (index < telemetries.size) {
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
                        val ratio = (retrieveCurrent(
                            selectedChannel,
                            telemetry
                        ) - Power.CURRENT.min) / currentDiff
                        val y = height - (ratio * height)
                        return@createPath y
                    }
                    drawPath(
                        path = path,
                        color = Power.CURRENT.color,
                        style = Stroke(
                            width = GraphUtil.RADIUS,
                            cap = StrokeCap.Round,
                        )
                    )
                }
            }
        }
        YAxisLabels(
            modifier = modifier.weight(weight = Y_AXIS_WEIGHT),
            Power.VOLTAGE.color,
            minValue = Power.VOLTAGE.min,
            maxValue = Power.VOLTAGE.max,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Legend(legendData = LEGEND_DATA, displayInfoIcon = false)

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PowerMetricsCard(telemetry: Telemetry) {
    val time = telemetry.time * MS_PER_SEC
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface {
            SelectionContainer {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                    ) {
                        /* Time */
                        Row {
                            Text(
                                text = DATE_TIME_FORMAT.format(time),
                                style = TextStyle(fontWeight = FontWeight.Bold),
                                fontSize = MaterialTheme.typography.labelLarge.fontSize
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (telemetry.powerMetrics.hasCh1Current() || telemetry.powerMetrics.hasCh1Voltage()) {
                                PowerChannelColumn(
                                    R.string.channel_1,
                                    telemetry.powerMetrics.ch1Voltage,
                                    telemetry.powerMetrics.ch1Current
                                )
                            }
                            if (telemetry.powerMetrics.hasCh2Current() || telemetry.powerMetrics.hasCh2Voltage()) {
                                PowerChannelColumn(
                                    R.string.channel_2,
                                    telemetry.powerMetrics.ch2Voltage,
                                    telemetry.powerMetrics.ch2Current
                                )
                            }
                            if (telemetry.powerMetrics.hasCh3Current() || telemetry.powerMetrics.hasCh3Voltage()) {
                                PowerChannelColumn(
                                    R.string.channel_3,
                                    telemetry.powerMetrics.ch3Voltage,
                                    telemetry.powerMetrics.ch3Current
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerChannelColumn(@StringRes titleRes: Int, voltage: Float, current: Float) {
    Column {
        Text(
            text = stringResource(titleRes),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = MaterialTheme.typography.labelLarge.fontSize
        )
        Text(
            text = "%.2fV".format(voltage),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.labelLarge.fontSize
        )
        Text(
            text = "%.1fmA".format(current),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.labelLarge.fontSize
        )
    }
}

/**
 * Retrieves the appropriate voltage depending on `channelSelected`.
 */
private fun retrieveVoltage(channelSelected: PowerChannel, telemetry: Telemetry): Float {
    return when (channelSelected) {
        PowerChannel.ONE -> telemetry.powerMetrics.ch1Voltage
        PowerChannel.TWO -> telemetry.powerMetrics.ch2Voltage
        PowerChannel.THREE -> telemetry.powerMetrics.ch3Voltage
    }
}

/**
 * Retrieves the appropriate current depending on `channelSelected`.
 */
private fun retrieveCurrent(channelSelected: PowerChannel, telemetry: Telemetry): Float {
    return when (channelSelected) {
        PowerChannel.ONE -> telemetry.powerMetrics.ch1Current
        PowerChannel.TWO -> telemetry.powerMetrics.ch2Current
        PowerChannel.THREE -> telemetry.powerMetrics.ch3Current
    }
}
