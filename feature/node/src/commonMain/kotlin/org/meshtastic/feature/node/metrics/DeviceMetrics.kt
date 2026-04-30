/*
 * Copyright (c) 2026 Meshtastic LLC
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_util_definition
import org.meshtastic.core.resources.air_utilization
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.ch_util_definition
import org.meshtastic.core.resources.channel_utilization
import org.meshtastic.core.resources.device_metrics_label_value
import org.meshtastic.core.resources.device_metrics_log
import org.meshtastic.core.resources.device_metrics_numeric_value
import org.meshtastic.core.resources.device_metrics_percent_value
import org.meshtastic.core.resources.device_metrics_voltage_value
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.GraphColors.Cyan
import org.meshtastic.core.ui.theme.GraphColors.Gold
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.Purple
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.Telemetry

private enum class Device(val color: Color) {
    BATTERY(Green) {
        override fun getValue(telemetry: Telemetry): Float = (telemetry.device_metrics?.battery_level ?: 0).toFloat()
    },
    VOLTAGE(Gold) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.device_metrics?.voltage ?: 0f
    },
    CH_UTIL(Purple) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.device_metrics?.channel_utilization ?: 0f
    },
    AIR_UTIL(Cyan) {
        override fun getValue(telemetry: Telemetry): Float = telemetry.device_metrics?.air_util_tx ?: 0f
    }, ;

    abstract fun getValue(telemetry: Telemetry): Float
}

private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.battery, color = Device.BATTERY.color, isLine = true),
        LegendData(nameRes = Res.string.voltage, color = Device.VOLTAGE.color, isLine = true),
        LegendData(nameRes = Res.string.channel_utilization, color = Device.CH_UTIL.color, isLine = true),
        LegendData(nameRes = Res.string.air_utilization, color = Device.AIR_UTIL.color, isLine = true),
    )

@Suppress("LongMethod")
@Composable
fun DeviceMetricsScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val data = state.deviceMetrics.filter { it.time.toLong() >= timeFrame.timeThreshold() }

    val exportLauncher = rememberSaveFileLauncher { uri -> viewModel.saveDeviceMetricsCSV(uri, data) }

    val hasBattery = remember(data) { data.any { it.device_metrics?.battery_level != null } }
    val hasVoltage = remember(data) { data.any { it.device_metrics?.voltage != null } }
    val hasChUtil = remember(data) { data.any { it.device_metrics?.channel_utilization != null } }
    val hasAirUtil = remember(data) { data.any { it.device_metrics?.air_util_tx != null } }

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
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.DEVICE,
        titleRes = Res.string.device_metrics_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = data,
        timeProvider = { it.time.toDouble() },
        infoData = infoItems,
        onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.DEVICE) },
        onExportCsv = { exportLauncher("device_metrics.csv", "text/csv") },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
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
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
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

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun DeviceMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    legendData: List<LegendData>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    MetricChartScaffold(isEmpty = telemetries.isEmpty(), legendData = legendData, modifier = modifier) {
            modelProducer,
            chartModifier,
        ->
        val batteryColor = Device.BATTERY.color
        val voltageColor = Device.VOLTAGE.color
        val chUtilColor = Device.CH_UTIL.color
        val airUtilColor = Device.AIR_UTIL.color
        val batteryLabel = stringResource(Res.string.battery)
        val voltageLabel = stringResource(Res.string.voltage)
        val channelUtilizationLabel = stringResource(Res.string.channel_utilization)
        val airUtilizationLabel = stringResource(Res.string.air_utilization)
        val percentValueTemplate = stringResource(Res.string.device_metrics_percent_value)
        val voltageValueTemplate = stringResource(Res.string.device_metrics_voltage_value)
        val numericValueTemplate = stringResource(Res.string.device_metrics_numeric_value)
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    val formatted = NumberFormatter.format(value, 1)
                    when (color) {
                        batteryColor -> formatString(percentValueTemplate, batteryLabel, formatted)
                        voltageColor -> formatString(voltageValueTemplate, voltageLabel, formatted)
                        chUtilColor -> formatString(percentValueTemplate, channelUtilizationLabel, formatted)
                        airUtilColor -> formatString(percentValueTemplate, airUtilizationLabel, formatted)
                        else -> formatString(numericValueTemplate, formatted)
                    }
                },
            )

        val batteryData = remember(telemetries) { telemetries.filter { it.device_metrics?.battery_level != null } }
        val chUtilData = remember(telemetries) { telemetries.filter { it.device_metrics?.channel_utilization != null } }
        val airUtilData = remember(telemetries) { telemetries.filter { it.device_metrics?.air_util_tx != null } }
        val voltageData = remember(telemetries) { telemetries.filter { it.device_metrics?.voltage != null } }

        val batteryStyle =
            if (batteryData.isNotEmpty()) {
                ChartStyling.createBoldLine(batteryColor)
            } else {
                null
            }
        val chUtilStyle =
            if (chUtilData.isNotEmpty()) {
                ChartStyling.createSubtleLine(chUtilColor)
            } else {
                null
            }
        val airUtilStyle =
            if (airUtilData.isNotEmpty()) {
                ChartStyling.createDashedLine(airUtilColor)
            } else {
                null
            }

        val leftLayerSeriesStyles =
            remember(batteryStyle, chUtilStyle, airUtilStyle) { listOfNotNull(batteryStyle, chUtilStyle, airUtilStyle) }

        LaunchedEffect(batteryData, chUtilData, airUtilData, voltageData, leftLayerSeriesStyles) {
            modelProducer.runTransaction {
                /* Series for Left Axis (0-100%) */
                if (leftLayerSeriesStyles.isNotEmpty()) {
                    lineSeries {
                        if (batteryData.isNotEmpty()) {
                            series(
                                x = batteryData.map { it.time },
                                y = batteryData.map { (it.device_metrics?.battery_level ?: 0).toFloat() },
                            )
                        }
                        if (chUtilData.isNotEmpty()) {
                            series(
                                x = chUtilData.map { it.time },
                                y = chUtilData.map { it.device_metrics?.channel_utilization ?: 0f },
                            )
                        }
                        if (airUtilData.isNotEmpty()) {
                            series(
                                x = airUtilData.map { it.time },
                                y = airUtilData.map { it.device_metrics?.air_util_tx ?: 0f },
                            )
                        }
                    }
                }
                /* Series for Right Axis (Voltage) */
                if (voltageData.isNotEmpty()) {
                    lineSeries {
                        series(
                            x = voltageData.map { it.time },
                            y = voltageData.map { it.device_metrics?.voltage ?: 0f },
                        )
                    }
                }
            }
        }

        val percentRangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0) }
        val leftLayer =
            rememberConditionalLayer(
                hasData = leftLayerSeriesStyles.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(leftLayerSeriesStyles),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = percentRangeProvider,
            )

        val rightLayer =
            rememberConditionalLayer(
                hasData = voltageData.isNotEmpty(),
                lineProvider =
                LineCartesianLayer.LineProvider.series(ChartStyling.createGradientLine(lineColor = voltageColor)),
                verticalAxisPosition = Axis.Position.Vertical.End,
            )

        val layers = remember(leftLayer, rightLayer) { listOfNotNull(leftLayer, rightLayer) }

        if (layers.isNotEmpty()) {
            val decorations = buildList {
                if (leftLayer != null) {
                    add(ChartStyling.rememberThresholdLine(y = 20.0, color = batteryColor, label = "20%"))
                }
            }

            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = layers,
                startAxis =
                if (leftLayer != null) {
                    VerticalAxis.rememberStart(
                        label = ChartStyling.rememberAxisLabel(color = batteryColor),
                        valueFormatter = { _, value, _ -> MetricFormatter.percent(value.toFloat(), 0) },
                    )
                } else {
                    null
                },
                endAxis =
                if (rightLayer != null) {
                    VerticalAxis.rememberEnd(
                        label = ChartStyling.rememberAxisLabel(color = voltageColor),
                        valueFormatter = { _, value, _ -> "${NumberFormatter.format(value.toFloat(), 1)} V" },
                    )
                } else {
                    null
                },
                bottomAxis = CommonCharts.rememberBottomTimeAxis(),
                marker = marker,
                decorations = decorations,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
                vicoScrollState = vicoScrollState,
            )
        }
    }
}

@PreviewLightDark
@Suppress("detekt:MagicNumber") // Compose preview with fake data
@Composable
private fun DeviceMetricsChartPreview() {
    val now = nowSeconds.toInt()
    val telemetries =
        List(20) { i ->
            Telemetry(
                time = now - (19 - i) * 60 * 60, // 1-hour intervals, oldest first
                device_metrics =
                org.meshtastic.proto.DeviceMetrics(
                    battery_level = 80 - i,
                    voltage = 3.7f - i * 0.02f,
                    channel_utilization = 10f + i * 2,
                    air_util_tx = 5f + i,
                    uptime_seconds = 3600 + i * 300,
                ),
            )
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

@Composable
@Suppress("LongMethod")
private fun DeviceMetricsCard(telemetry: Telemetry, isSelected: Boolean, onClick: () -> Unit) {
    val deviceMetrics = telemetry.device_metrics
    val time = telemetry.time.toLong() * MS_PER_SEC
    val channelUtilizationLabel = stringResource(Res.string.channel_utilization)
    val airUtilizationLabel = stringResource(Res.string.air_utilization)
    val uptimeLabel = stringResource(Res.string.uptime)
    val percentValueTemplate = stringResource(Res.string.device_metrics_percent_value)
    val labelValueTemplate = stringResource(Res.string.device_metrics_label_value)
    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            /* Time, Battery, and Voltage */
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = DateFormatter.formatDateTime(time),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.Bold,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (deviceMetrics?.battery_level != null) {
                        MetricIndicator(Device.BATTERY.color)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (deviceMetrics?.voltage != null) {
                        MetricIndicator(Device.VOLTAGE.color)
                        Spacer(Modifier.width(8.dp))
                    }
                    MaterialBatteryInfo(
                        level = deviceMetrics?.battery_level ?: 0,
                        voltage = deviceMetrics?.voltage ?: 0f,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            /* Channel Utilization and Air Utilization Tx */
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (deviceMetrics?.channel_utilization != null) {
                        MetricValueRow(
                            color = Device.CH_UTIL.color,
                            text =
                            formatString(
                                percentValueTemplate,
                                channelUtilizationLabel,
                                NumberFormatter.format(deviceMetrics.channel_utilization ?: 0f, 1),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    if (deviceMetrics?.air_util_tx != null) {
                        MetricValueRow(
                            color = Device.AIR_UTIL.color,
                            text =
                            formatString(
                                percentValueTemplate,
                                airUtilizationLabel,
                                NumberFormatter.format(deviceMetrics.air_util_tx ?: 0f, 1),
                            ),
                        )
                    }
                }
                Text(
                    text =
                    formatString(labelValueTemplate, uptimeLabel, formatUptime(deviceMetrics?.uptime_seconds ?: 0)),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@PreviewLightDark
@Suppress("detekt:MagicNumber") // Compose preview with fake data
@Composable
private fun DeviceMetricsCardPreview() {
    val now = nowSeconds.toInt()
    val telemetry =
        Telemetry(
            time = now,
            device_metrics =
            org.meshtastic.proto.DeviceMetrics(
                battery_level = 75,
                voltage = 3.65f,
                channel_utilization = 22.5f,
                air_util_tx = 12.0f,
                uptime_seconds = 7200,
            ),
        )
    AppTheme { DeviceMetricsCard(telemetry = telemetry, isSelected = false, onClick = {}) }
}

@PreviewLightDark
@Suppress("detekt:MagicNumber") // Compose preview with fake data
@Composable
private fun DeviceMetricsScreenPreview() {
    val now = nowSeconds.toInt()
    val telemetries =
        List(24) { i ->
            Telemetry(
                time = now - (23 - i) * 60 * 60, // 1-hour intervals, oldest first
                device_metrics =
                org.meshtastic.proto.DeviceMetrics(
                    battery_level = 85 - i * 2, // Battery decreases over time
                    voltage = 3.8f - i * 0.01f, // Voltage decreases slightly
                    channel_utilization = 15f + i * 1.5f, // Channel utilization increases
                    air_util_tx = 8f + i * 0.8f, // Air utilization increases
                    uptime_seconds = 3600 + i * 3600, // Uptime increases by 1 hour each
                ),
            )
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
