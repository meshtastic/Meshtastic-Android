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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.OptionLabel
import org.meshtastic.core.ui.component.SlidingSelector
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.GraphColors.Cyan
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.Magenta
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MAX_PERCENT_VALUE
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.feature.node.metrics.GraphUtil.createPath
import org.meshtastic.feature.node.metrics.GraphUtil.plotPoint
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.proto.TelemetryProtos.Telemetry
import org.meshtastic.core.strings.R as Res

private const val CHART_WEIGHT = 1f
private const val Y_AXIS_WEIGHT = 0.1f
private const val CHART_WIDTH_RATIO = CHART_WEIGHT / (CHART_WEIGHT + Y_AXIS_WEIGHT + Y_AXIS_WEIGHT)

private enum class Device(val color: Color) {
    BATTERY(Green) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.deviceMetrics.batteryLevel.toFloat()
    },
    CH_UTIL(Magenta) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.deviceMetrics.channelUtilization
    },
    AIR_UTIL(Cyan) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.deviceMetrics.airUtilTx
    }, ;

    abstract fun getValue(telemetry: Telemetry): Float
}

private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.battery, color = Device.BATTERY.color, isLine = true, environmentMetric = null),
        LegendData(
            nameRes = Res.string.channel_utilization,
            color = Device.CH_UTIL.color,
            isLine = false,
            environmentMetric = null,
        ),
        LegendData(
            nameRes = Res.string.air_utilization,
            color = Device.AIR_UTIL.color,
            isLine = false,
            environmentMetric = null,
        ),
    )

@Composable
fun DeviceMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var displayInfoDialog by remember { mutableStateOf(false) }
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val data = state.deviceMetricsFiltered(selectedTimeFrame)

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.longName ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (displayInfoDialog) {
                LegendInfoDialog(
                    pairedRes =
                    listOf(
                        Pair(Res.string.channel_utilization, Res.string.ch_util_definition),
                        Pair(Res.string.air_utilization, Res.string.air_util_definition),
                    ),
                    onDismiss = { displayInfoDialog = false },
                )
            }

            DeviceMetricsChart(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f),
                telemetries = data.reversed(),
                selectedTimeFrame,
                promptInfoDialog = { displayInfoDialog = true },
            )

            SlidingSelector(
                TimeFrame.entries.toList(),
                selectedTimeFrame,
                onOptionSelected = { viewModel.setTimeFrame(it) },
            ) {
                OptionLabel(stringResource(it.strRes))
            }

            /* Device Metric Cards */
            LazyColumn(modifier = Modifier.fillMaxSize()) { items(data) { telemetry -> DeviceMetricsCard(telemetry) } }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun DeviceMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit,
) {
    val graphColor = MaterialTheme.colorScheme.onSurface

    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) return

    val (oldest, newest) =
        remember(key1 = telemetries) { Pair(telemetries.minBy { it.time }, telemetries.maxBy { it.time }) }
    val timeDiff = newest.time - oldest.time

    val scrollState = rememberScrollState()
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val dp by remember(key1 = selectedTime) { mutableStateOf(selectedTime.dp(screenWidth, time = timeDiff.toLong())) }

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

    TimeLabels(oldest = visibleTimeRange.first, newest = visibleTimeRange.second)

    Spacer(modifier = Modifier.height(16.dp))

    Row {
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier.horizontalScroll(state = scrollState, reverseScrolling = true).weight(weight = 1f),
        ) {
            /*
             * The order of the colors are with respect to the ChUtil.
             * 25 - 49  Orange
             * 50 - 100 Red
             */
            HorizontalLinesOverlay(
                modifier.width(dp),
                lineColors = listOf(graphColor, Color.Yellow, Color.Red, graphColor, graphColor),
            )

            TimeAxisOverlay(modifier.width(dp), oldest = oldest.time, newest = newest.time, selectedTime.lineInterval())

            /* Plot Battery Line, ChUtil, and AirUtilTx */
            Canvas(modifier = modifier.width(dp)) {
                val height = size.height
                val width = size.width
                for (i in telemetries.indices) {
                    val telemetry = telemetries[i]

                    /* x-value time */
                    val xRatio = (telemetry.time - oldest.time).toFloat() / timeDiff
                    val x = xRatio * width

                    /* Channel Utilization */
                    plotPoint(
                        drawContext = drawContext,
                        color = Device.CH_UTIL.color,
                        x = x,
                        value = telemetry.deviceMetrics.channelUtilization,
                        divisor = MAX_PERCENT_VALUE,
                    )

                    /* Air Utilization Transmit */
                    plotPoint(
                        drawContext = drawContext,
                        color = Device.AIR_UTIL.color,
                        x = x,
                        value = telemetry.deviceMetrics.airUtilTx,
                        divisor = MAX_PERCENT_VALUE,
                    )
                }

                /* Battery Line */
                var index = 0
                while (index < telemetries.size) {
                    val path = Path()
                    index =
                        createPath(
                            telemetries = telemetries,
                            index = index,
                            path = path,
                            oldestTime = oldest.time,
                            timeRange = timeDiff,
                            width = width,
                            timeThreshold = selectedTime.timeThreshold(),
                        ) { i ->
                            val telemetry = telemetries.getOrNull(i) ?: telemetries.last()
                            val ratio = telemetry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE
                            val y = height - (ratio * height)
                            return@createPath y
                        }
                    drawPath(
                        path = path,
                        color = Device.BATTERY.color,
                        style = Stroke(width = GraphUtil.RADIUS, cap = StrokeCap.Round),
                    )
                }
            }
        }
        YAxisLabels(modifier = modifier.weight(weight = Y_AXIS_WEIGHT), graphColor, minValue = 0f, maxValue = 100f)
    }
    Spacer(modifier = Modifier.height(16.dp))

    Legend(legendData = LEGEND_DATA, promptInfoDialog = promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

@Suppress("detekt:MagicNumber") // fake data
@PreviewLightDark
@Composable
private fun DeviceMetricsChartPreview() {
    val now = (System.currentTimeMillis() / 1000).toInt()
    val telemetries =
        List(20) { i ->
            Telemetry.newBuilder()
                .setTime(now - (19 - i) * 60 * 60) // 1-hour intervals, oldest first
                .setDeviceMetrics(
                    TelemetryProtos.DeviceMetrics.newBuilder()
                        .setBatteryLevel(80 - i)
                        .setVoltage(3.7f - i * 0.02f)
                        .setChannelUtilization(10f + i * 2)
                        .setAirUtilTx(5f + i)
                        .setUptimeSeconds(3600 + i * 300),
                )
                .build()
        }
    AppTheme {
        DeviceMetricsChart(
            modifier = Modifier.height(400.dp),
            telemetries = telemetries,
            selectedTime = TimeFrame.TWENTY_FOUR_HOURS,
            promptInfoDialog = {},
        )
    }
}

@Composable
private fun DeviceMetricsCard(telemetry: Telemetry) {
    val deviceMetrics = telemetry.deviceMetrics
    val time = telemetry.time * MS_PER_SEC
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Surface {
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    /* Time, Battery, and Voltage */
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = DATE_TIME_FORMAT.format(time),
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        )

                        MaterialBatteryInfo(level = deviceMetrics.batteryLevel, voltage = deviceMetrics.voltage)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    /* Channel Utilization and Air Utilization Tx */
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val text =
                            stringResource(Res.string.channel_air_util)
                                .format(deviceMetrics.channelUtilization, deviceMetrics.airUtilTx)
                        Text(
                            text = text,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        )
                    }
                }
            }
        }
    }
}

@Suppress("detekt:MagicNumber") // fake data
@PreviewLightDark
@Composable
private fun DeviceMetricsCardPreview() {
    val now = (System.currentTimeMillis() / 1000).toInt()
    val telemetry =
        Telemetry.newBuilder()
            .setTime(now)
            .setDeviceMetrics(
                TelemetryProtos.DeviceMetrics.newBuilder()
                    .setBatteryLevel(75)
                    .setVoltage(3.65f)
                    .setChannelUtilization(22.5f)
                    .setAirUtilTx(12.0f)
                    .setUptimeSeconds(7200),
            )
            .build()
    AppTheme { DeviceMetricsCard(telemetry = telemetry) }
}

@Suppress("detekt:MagicNumber") // fake data
@PreviewLightDark
@Composable
private fun DeviceMetricsScreenPreview() {
    val now = (System.currentTimeMillis() / 1000).toInt()
    val telemetries =
        List(24) { i ->
            Telemetry.newBuilder()
                .setTime(now - (23 - i) * 60 * 60) // 1-hour intervals, oldest first
                .setDeviceMetrics(
                    TelemetryProtos.DeviceMetrics.newBuilder()
                        .setBatteryLevel(85 - i * 2) // Battery decreases over time
                        .setVoltage(3.8f - i * 0.01f) // Voltage decreases slightly
                        .setChannelUtilization(15f + i * 1.5f) // Channel utilization increases
                        .setAirUtilTx(8f + i * 0.8f) // Air utilization increases
                        .setUptimeSeconds(3600 + i * 3600), // Uptime increases by 1 hour each
                )
                .build()
        }

    AppTheme {
        Surface {
            Column {
                var displayInfoDialog by remember { mutableStateOf(false) }

                if (displayInfoDialog) {
                    LegendInfoDialog(
                        pairedRes =
                        listOf(
                            Pair(Res.string.channel_utilization, Res.string.ch_util_definition),
                            Pair(Res.string.air_utilization, Res.string.air_util_definition),
                        ),
                        onDismiss = { displayInfoDialog = false },
                    )
                }

                DeviceMetricsChart(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f),
                    telemetries.reversed(),
                    TimeFrame.TWENTY_FOUR_HOURS,
                    promptInfoDialog = { displayInfoDialog = true },
                )

                SlidingSelector(
                    TimeFrame.entries.toList(),
                    TimeFrame.TWENTY_FOUR_HOURS,
                    onOptionSelected = { /* Preview only */ },
                ) {
                    OptionLabel(stringResource(it.strRes))
                }

                /* Device Metric Cards */
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(telemetries) { telemetry -> DeviceMetricsCard(telemetry) }
                }
            }
        }
    }
}
