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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.air_util_definition
import org.meshtastic.core.strings.air_utilization
import org.meshtastic.core.strings.battery
import org.meshtastic.core.strings.ch_util_definition
import org.meshtastic.core.strings.channel_utilization
import org.meshtastic.core.strings.device_metrics_log
import org.meshtastic.core.strings.uptime
import org.meshtastic.core.strings.voltage
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.GraphColors.Cyan
import org.meshtastic.core.ui.theme.GraphColors.Gold
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.Purple
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.proto.TelemetryProtos.Telemetry

private enum class Device(val color: Color) {
    BATTERY(Green) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.deviceMetrics.batteryLevel.toFloat()
    },
    VOLTAGE(Gold) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.deviceMetrics.voltage
    },
    CH_UTIL(Purple) {
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
        LegendData(nameRes = Res.string.voltage, color = Device.VOLTAGE.color, isLine = true, environmentMetric = null),
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
    val data = state.deviceMetrics

    val hasBattery = remember(data) { data.any { it.deviceMetrics.hasBatteryLevel() } }
    val hasVoltage = remember(data) { data.any { it.deviceMetrics.hasVoltage() } }
    val hasChUtil = remember(data) { data.any { it.deviceMetrics.hasChannelUtilization() } }
    val hasAirUtil = remember(data) { data.any { it.deviceMetrics.hasAirUtilTx() } }

    val filteredLegendData =
        remember(hasBattery, hasVoltage, hasChUtil, hasAirUtil) {
            LEGEND_DATA.filter { d ->
                when (d.nameRes) {
                    Res.string.battery -> hasBattery
                    Res.string.voltage -> hasVoltage
                    Res.string.channel_utilization -> hasChUtil
                    Res.string.air_utilization -> hasAirUtil
                    else -> true
                }
            }
        }

    val infoItems =
        remember(hasChUtil, hasAirUtil) {
            buildList {
                if (hasChUtil) {
                    add(
                        InfoDialogData(
                            Res.string.channel_utilization,
                            Res.string.ch_util_definition,
                            Device.CH_UTIL.color,
                        ),
                    )
                }
                if (hasAirUtil) {
                    add(
                        InfoDialogData(
                            Res.string.air_utilization,
                            Res.string.air_util_definition,
                            Device.AIR_UTIL.color,
                        ),
                    )
                }
            }
        }

    BaseMetricScreen(
        viewModel = viewModel,
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.DEVICE,
        titleRes = Res.string.device_metrics_log,
        data = data,
        timeProvider = { it.time.toDouble() },
        infoData = infoItems,
        chartPart = { modifier, selectedX, vicoScrollState, onPointSelected ->
            DeviceMetricsChart(
                modifier = modifier,
                telemetries = data.reversed(),
                legendData = filteredLegendData,
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { modifier, selectedX, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize()) {
                itemsIndexed(data) { _, telemetry ->
                    DeviceMetricsCard(
                        telemetry = telemetry,
                        isSelected = telemetry.time.toDouble() == selectedX,
                        onClick = { onCardClick(telemetry.time.toDouble()) },
                    )
                }
            }
        },
    )
}

@Suppress("LongMethod")
@Composable
private fun DeviceMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    legendData: List<LegendData>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    Column(modifier = modifier) {
        if (telemetries.isEmpty()) return@Column

        val modelProducer = remember { CartesianChartModelProducer() }
        val batteryColor = Device.BATTERY.color
        val voltageColor = Device.VOLTAGE.color
        val chUtilColor = Device.CH_UTIL.color
        val airUtilColor = Device.AIR_UTIL.color
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color.copy(alpha = 1f)) {
                        batteryColor -> "Battery: %.1f%%".format(value)
                        voltageColor -> "Voltage: %.1f V".format(value)
                        chUtilColor -> "ChUtil: %.1f%%".format(value)
                        airUtilColor -> "AirUtil: %.1f%%".format(value)
                        else -> "%.1f".format(value)
                    }
                },
            )

        LaunchedEffect(telemetries) {
            modelProducer.runTransaction {
                /* Series for Left Axis (0-100%) */
                lineSeries {
                    series(x = telemetries.map { it.time }, y = telemetries.map { it.deviceMetrics.batteryLevel })
                    series(x = telemetries.map { it.time }, y = telemetries.map { it.deviceMetrics.channelUtilization })
                    series(x = telemetries.map { it.time }, y = telemetries.map { it.deviceMetrics.airUtilTx })
                }
                /* Series for Right Axis (Voltage) */
                lineSeries { series(x = telemetries.map { it.time }, y = telemetries.map { it.deviceMetrics.voltage }) }
            }
        }

        GenericMetricChart(
            modelProducer = modelProducer,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).padding(bottom = 0.dp),
            layers =
            listOf(
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createBoldLine(
                            lineColor = batteryColor,
                            pointSize = ChartStyling.MEDIUM_POINT_SIZE_DP,
                        ),
                        ChartStyling.createPointOnlyLine(
                            pointColor = chUtilColor,
                            pointSize = ChartStyling.LARGE_POINT_SIZE_DP,
                        ),
                        ChartStyling.createPointOnlyLine(
                            pointColor = airUtilColor,
                            pointSize = ChartStyling.LARGE_POINT_SIZE_DP,
                        ),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                ),
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createGradientLine(
                            lineColor = voltageColor,
                            pointSize = ChartStyling.MEDIUM_POINT_SIZE_DP,
                        ),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.End,
                ),
            ),
            startAxis =
            VerticalAxis.rememberStart(
                label = ChartStyling.rememberAxisLabel(color = batteryColor),
                valueFormatter = { _, value, _ -> "%.0f%%".format(value) },
            ),
            endAxis =
            VerticalAxis.rememberEnd(
                label = ChartStyling.rememberAxisLabel(color = voltageColor),
                valueFormatter = { _, value, _ -> "%.1f V".format(value) },
            ),
            bottomAxis =
            HorizontalAxis.rememberBottom(
                label = ChartStyling.rememberAxisLabel(),
                valueFormatter = CommonCharts.dynamicTimeFormatter,
                itemPlacer = ChartStyling.rememberItemPlacer(spacing = 20),
                labelRotationDegrees = 45f,
            ),
            marker = marker,
            selectedX = selectedX,
            onPointSelected = onPointSelected,
            vicoScrollState = vicoScrollState,
        )

        Legend(legendData = legendData, modifier = Modifier.padding(top = 0.dp))
    }
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
            legendData = LEGEND_DATA,
            vicoScrollState = rememberVicoScrollState(),
            selectedX = null,
            onPointSelected = {},
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongMethod")
private fun DeviceMetricsCard(telemetry: Telemetry, isSelected: Boolean, onClick: () -> Unit) {
    val deviceMetrics = telemetry.deviceMetrics
    val time = telemetry.time * MS_PER_SEC
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onClick() },
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors =
        CardDefaults.cardColors(
            containerColor =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Surface(color = Color.Transparent) {
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    /* Time, Battery, and Voltage */
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = DATE_TIME_FORMAT.format(time),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (deviceMetrics.hasBatteryLevel()) {
                                MetricIndicator(Device.BATTERY.color)
                                Spacer(Modifier.width(4.dp))
                            }
                            if (deviceMetrics.hasVoltage()) {
                                MetricIndicator(Device.VOLTAGE.color)
                                Spacer(Modifier.width(8.dp))
                            }
                            MaterialBatteryInfo(level = deviceMetrics.batteryLevel, voltage = deviceMetrics.voltage)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    /* Channel Utilization and Air Utilization Tx */
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (deviceMetrics.hasChannelUtilization()) {
                                MetricIndicator(Device.CH_UTIL.color)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Ch: %.1f%%".format(deviceMetrics.channelUtilization),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            if (deviceMetrics.hasAirUtilTx()) {
                                MetricIndicator(Device.AIR_UTIL.color)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Air: %.1f%%".format(deviceMetrics.airUtilTx),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                                )
                            }
                        }
                        Text(
                            text = stringResource(Res.string.uptime) + ": " + formatUptime(deviceMetrics.uptimeSeconds),
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
    AppTheme { DeviceMetricsCard(telemetry = telemetry, isSelected = false, onClick = {}) }
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
                        infoData =
                        listOf(
                            InfoDialogData(
                                Res.string.channel_utilization,
                                Res.string.ch_util_definition,
                                Device.CH_UTIL.color,
                            ),
                            InfoDialogData(
                                Res.string.air_utilization,
                                Res.string.air_util_definition,
                                Device.AIR_UTIL.color,
                            ),
                        ),
                        onDismiss = { displayInfoDialog = false },
                    )
                }

                DeviceMetricsChart(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f),
                    telemetries = telemetries.reversed(),
                    legendData = LEGEND_DATA,
                    vicoScrollState = rememberVicoScrollState(),
                    selectedX = null,
                    onPointSelected = {},
                )

                /* Device Metric Cards */
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(telemetries) { _, telemetry ->
                        DeviceMetricsCard(telemetry = telemetry, isSelected = false, onClick = {})
                    }
                }
            }
        }
    }
}
