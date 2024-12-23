/*
 * Copyright (c) 2024 Meshtastic LLC
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
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
import com.geeksville.mesh.ui.BatteryInfo
import com.geeksville.mesh.ui.components.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.ui.components.CommonCharts.DATE_TIME_FORMAT
import com.geeksville.mesh.ui.theme.Orange
import com.geeksville.mesh.util.GraphUtil.plotPoint
import com.geeksville.mesh.util.GraphUtil.createPath

private val DEVICE_METRICS_COLORS = listOf(Color.Green, Color.Magenta, Color.Cyan)
private const val MAX_PERCENT_VALUE = 100f
private enum class Device {
    BATTERY,
    CH_UTIL,
    AIR_UTIL
}
private val LEGEND_DATA = listOf(
    LegendData(nameRes = R.string.battery, color = DEVICE_METRICS_COLORS[Device.BATTERY.ordinal], isLine = true),
    LegendData(nameRes = R.string.channel_utilization, color = DEVICE_METRICS_COLORS[Device.CH_UTIL.ordinal]),
    LegendData(nameRes = R.string.air_utilization, color = DEVICE_METRICS_COLORS[Device.AIR_UTIL.ordinal]),
)

@Composable
fun DeviceMetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var displayInfoDialog by remember { mutableStateOf(false) }
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val data = state.deviceMetricsFiltered(selectedTimeFrame)

    Column {

        if (displayInfoDialog) {
            LegendInfoDialog(
                pairedRes = listOf(
                    Pair(R.string.channel_utilization, R.string.ch_util_definition),
                    Pair(R.string.air_utilization, R.string.air_util_definition)
                ),
                onDismiss = { displayInfoDialog = false }
            )
        }

        DeviceMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            data.reversed(),
            selectedTimeFrame,
            promptInfoDialog = { displayInfoDialog = true }
        )

        MetricsTimeSelector(
            selectedTimeFrame,
            onOptionSelected = { viewModel.setTimeFrame(it) }
        ) {
            TimeLabel(stringResource(it.strRes))
        }

        /* Device Metric Cards */
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(data) { telemetry -> DeviceMetricsCard(telemetry) }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun DeviceMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit
) {

    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) return

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
    val scrollState = rememberScrollState()

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val dp by remember(key1 = selectedTime) {
        mutableStateOf(selectedTime.dp(screenWidth, time = (newest.time - oldest.time).toLong()))
    }

    Row {
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier
                .horizontalScroll(state = scrollState, reverseScrolling = true)
                .weight(1f)
        ) {

            /*
             * The order of the colors are with respect to the ChUtil.
             * 25 - 49  Orange
             * 50 - 100 Red
             */
            HorizontalLinesOverlay(
                modifier.width(dp),
                lineColors = listOf(graphColor, Orange, Color.Red, graphColor, graphColor),
                minValue = 0f,
                maxValue = 100f
            )

            TimeAxisOverlay(
                modifier.width(dp),
                oldest = oldest.time,
                newest = newest.time,
                selectedTime.lineInterval()
            )

            /* Plot Battery Line, ChUtil, and AirUtilTx */
            Canvas(modifier = modifier.width(dp)) {

                val height = size.height
                val width = size.width
                val dataPointRadius = 2.dp.toPx()
                for (i in telemetries.indices) {
                    val telemetry = telemetries[i]

                    /* x-value time */
                    val xRatio = (telemetry.time - oldest.time).toFloat() / timeDiff
                    val x = xRatio * width

                    /* Channel Utilization */
                    plotPoint(
                        drawContext = drawContext,
                        color = DEVICE_METRICS_COLORS[Device.CH_UTIL.ordinal],
                        radius = dataPointRadius,
                        x = x,
                        value = telemetry.deviceMetrics.channelUtilization,
                        divisor = MAX_PERCENT_VALUE
                    )

                    /* Air Utilization Transmit */
                    plotPoint(
                        drawContext = drawContext,
                        color = DEVICE_METRICS_COLORS[Device.AIR_UTIL.ordinal],
                        radius = dataPointRadius,
                        x = x,
                        value = telemetry.deviceMetrics.airUtilTx,
                        divisor = MAX_PERCENT_VALUE
                    )
                }

                /* Battery Line */
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
                        val ratio = telemetry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE
                        val y = height - (ratio * height)
                        return@createPath y
                    }
                    drawPath(
                        path = path,
                        color = DEVICE_METRICS_COLORS[Device.BATTERY.ordinal],
                        style = Stroke(
                            width = dataPointRadius,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }
        YAxisLabels(
            modifier = modifier.weight(weight = .1f),
            graphColor,
            minValue = 0f,
            maxValue = 100f
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    Legend(legendData = LEGEND_DATA, promptInfoDialog)

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
                            text = DATE_TIME_FORMAT.format(time),
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
                        val text = stringResource(R.string.channel_air_util).format(
                            deviceMetrics.channelUtilization,
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
