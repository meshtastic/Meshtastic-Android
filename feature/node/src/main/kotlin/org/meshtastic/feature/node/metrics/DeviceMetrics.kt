/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.air_util_definition
import org.meshtastic.core.strings.air_utilization
import org.meshtastic.core.strings.battery
import org.meshtastic.core.strings.ch_util_definition
import org.meshtastic.core.strings.channel_air_util
import org.meshtastic.core.strings.channel_utilization
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.OptionLabel
import org.meshtastic.core.ui.component.SlidingSelector
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.GraphColors.Cyan
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.Magenta
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.proto.TelemetryProtos.Telemetry
import java.util.Date

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

@Suppress("LongMethod")
@Composable
fun DeviceMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var displayInfoDialog by remember { mutableStateOf(false) }
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val data = state.deviceMetricsFiltered(selectedTimeFrame)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NodeRequestEffect.ShowFeedback -> {
                    @Suppress("SpreadOperator")
                    snackbarHostState.showSnackbar(getString(effect.resource, *effect.args.toTypedArray()))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.longName ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        IconButton(onClick = { viewModel.requestTelemetry(TelemetryType.DEVICE) }) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
    promptInfoDialog: () -> Unit,
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(telemetries) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = telemetries.map { it.time }, y = telemetries.map { it.deviceMetrics.batteryLevel })
                series(x = telemetries.map { it.time }, y = telemetries.map { it.deviceMetrics.channelUtilization })
                series(x = telemetries.map { it.time }, y = telemetries.map { it.deviceMetrics.airUtilTx })
            }
        }
    }

    CartesianChartHost(
        chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createBoldLine(
                        lineColor = Device.BATTERY.color,
                        pointSize = ChartStyling.MEDIUM_POINT_SIZE_DP,
                    ),
                    ChartStyling.createPointOnlyLine(
                        pointColor = Device.CH_UTIL.color,
                        pointSize = ChartStyling.LARGE_POINT_SIZE_DP,
                    ),
                    ChartStyling.createPointOnlyLine(
                        pointColor = Device.AIR_UTIL.color,
                        pointSize = ChartStyling.LARGE_POINT_SIZE_DP,
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis =
            HorizontalAxis.rememberBottom(
                valueFormatter = { _, value, _ ->
                    CommonCharts.TIME_MINUTE_FORMAT.format(
                        Date((value * CommonCharts.MS_PER_SEC.toDouble()).toLong()),
                    )
                },
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier.padding(8.dp),
    )

    Legend(legendData = LEGEND_DATA, promptInfoDialog = promptInfoDialog)
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
        DeviceMetricsChart(modifier = Modifier.height(400.dp), telemetries = telemetries, promptInfoDialog = {})
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
                    telemetries = telemetries.reversed(),
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
