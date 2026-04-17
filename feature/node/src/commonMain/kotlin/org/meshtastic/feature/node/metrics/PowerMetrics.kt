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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_1
import org.meshtastic.core.resources.channel_2
import org.meshtastic.core.resources.channel_3
import org.meshtastic.core.resources.channel_4
import org.meshtastic.core.resources.channel_5
import org.meshtastic.core.resources.channel_6
import org.meshtastic.core.resources.channel_7
import org.meshtastic.core.resources.channel_8
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.power_metrics_log
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.ui.theme.GraphColors.Gold
import org.meshtastic.core.ui.theme.GraphColors.InfantryBlue
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.Telemetry

private enum class PowerMetric(val color: Color) {
    CURRENT(InfantryBlue),
    VOLTAGE(Gold),
}

private enum class PowerChannel(val strRes: StringResource) {
    ONE(Res.string.channel_1),
    TWO(Res.string.channel_2),
    THREE(Res.string.channel_3),
    FOUR(Res.string.channel_4),
    FIVE(Res.string.channel_5),
    SIX(Res.string.channel_6),
    SEVEN(Res.string.channel_7),
    EIGHT(Res.string.channel_8),
}

private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.current, color = PowerMetric.CURRENT.color, isLine = true),
        LegendData(nameRes = Res.string.voltage, color = PowerMetric.VOLTAGE.color, isLine = true),
    )

@Suppress("LongMethod")
@Composable
fun PowerMetricsScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val data = state.powerMetrics.filter { it.time.toLong() >= timeFrame.timeThreshold() }

    val exportLauncher = rememberSaveFileLauncher { uri -> viewModel.savePowerMetricsCSV(uri, data) }

    val availableChannels =
        remember(data) {
            PowerChannel.entries.filter { channel ->
                data.any { !retrieveVoltage(channel, it).isNaN() || !retrieveCurrent(channel, it).isNaN() }
            }
        }
    var selectedChannel by rememberSaveable { mutableStateOf(PowerChannel.ONE) }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.POWER,
        titleRes = Res.string.power_metrics_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = data,
        timeProvider = { it.time.toDouble() },
        onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.POWER) },
        onExportCsv = { exportLauncher("power_metrics.csv", "text/csv") },
        controlPart = {
            Column {
                TimeFrameSelector(
                    selectedTimeFrame = timeFrame,
                    availableTimeFrames = availableTimeFrames,
                    onTimeFrameSelected = viewModel::setTimeFrame,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableChannels.forEach { channel ->
                        FilterChip(
                            selected = selectedChannel == channel,
                            onClick = { selectedChannel = channel },
                            label = { Text(stringResource(channel.strRes)) },
                        )
                    }
                }
            }
        },
        chartPart = { modifier, selectedX, vicoScrollState, onPointSelected ->
            PowerMetricsChart(
                modifier = modifier,
                telemetries = data.reversed(),
                selectedChannel = selectedChannel,
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(data) { _, telemetry ->
                    PowerMetricsCard(
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
private fun PowerMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedChannel: PowerChannel,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    MetricChartScaffold(isEmpty = telemetries.isEmpty(), legendData = LEGEND_DATA, modifier = modifier) {
            modelProducer,
            chartModifier,
        ->
        val currentColor = PowerMetric.CURRENT.color
        val voltageColor = PowerMetric.VOLTAGE.color
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color) {
                        currentColor -> "Current: ${MetricFormatter.current(value.toFloat(), 0)}"
                        voltageColor -> "Voltage: ${NumberFormatter.format(value.toFloat(), 1)} V"
                        else -> NumberFormatter.format(value.toFloat(), 1)
                    }
                },
            )

        val currentData =
            remember(telemetries, selectedChannel) {
                telemetries.filter { !retrieveCurrent(selectedChannel, it).isNaN() }
            }
        val voltageData =
            remember(telemetries, selectedChannel) {
                telemetries.filter { !retrieveVoltage(selectedChannel, it).isNaN() }
            }

        LaunchedEffect(selectedChannel, currentData, voltageData) {
            modelProducer.runTransaction {
                if (currentData.isNotEmpty()) {
                    lineSeries {
                        series(
                            x = currentData.map { it.time },
                            y = currentData.map { retrieveCurrent(selectedChannel, it) },
                        )
                    }
                }
                if (voltageData.isNotEmpty()) {
                    lineSeries {
                        series(
                            x = voltageData.map { it.time },
                            y = voltageData.map { retrieveVoltage(selectedChannel, it) },
                        )
                    }
                }
            }
        }

        val currentLayer =
            rememberConditionalLayer(
                hasData = currentData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createBoldLine(currentColor)),
                verticalAxisPosition = Axis.Position.Vertical.Start,
            )

        val voltageLayer =
            rememberConditionalLayer(
                hasData = voltageData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createGradientLine(voltageColor)),
                verticalAxisPosition = Axis.Position.Vertical.End,
            )

        val layers = remember(currentLayer, voltageLayer) { listOfNotNull(currentLayer, voltageLayer) }

        if (layers.isNotEmpty()) {
            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = layers,
                startAxis =
                if (currentData.isNotEmpty()) {
                    VerticalAxis.rememberStart(
                        label = ChartStyling.rememberAxisLabel(color = currentColor),
                        valueFormatter = { _, value, _ -> MetricFormatter.current(value.toFloat(), 0) },
                    )
                } else {
                    null
                },
                endAxis =
                if (voltageData.isNotEmpty()) {
                    VerticalAxis.rememberEnd(
                        label = ChartStyling.rememberAxisLabel(color = voltageColor),
                        valueFormatter = { _, value, _ -> "${NumberFormatter.format(value.toFloat(), 1)} V" },
                    )
                } else {
                    null
                },
                bottomAxis = CommonCharts.rememberBottomTimeAxis(),
                marker = marker,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
                vicoScrollState = vicoScrollState,
            )
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun PowerMetricsCard(telemetry: Telemetry, isSelected: Boolean, onClick: () -> Unit) {
    val time = telemetry.time.toLong() * MS_PER_SEC
    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                /* Time */
                Row {
                    Text(
                        text = DateFormatter.formatDateTime(time),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val pm = telemetry.power_metrics
                if (pm != null) {
                    PowerChannelsRow1(pm)
                    PowerChannelsExtraRows(pm)
                }
            }
        }
    }
}

@Composable
private fun PowerChannelsRow1(pm: org.meshtastic.proto.PowerMetrics) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        if (pm.ch1_current != null || pm.ch1_voltage != null) {
            PowerChannelColumn(Res.string.channel_1, pm.ch1_voltage ?: 0f, pm.ch1_current ?: 0f)
        }
        if (pm.ch2_current != null || pm.ch2_voltage != null) {
            PowerChannelColumn(Res.string.channel_2, pm.ch2_voltage ?: 0f, pm.ch2_current ?: 0f)
        }
        if (pm.ch3_current != null || pm.ch3_voltage != null) {
            PowerChannelColumn(Res.string.channel_3, pm.ch3_voltage ?: 0f, pm.ch3_current ?: 0f)
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun PowerChannelsExtraRows(pm: org.meshtastic.proto.PowerMetrics) {
    val hasCh456 =
        hasChannelData(pm.ch4_voltage, pm.ch4_current) ||
            hasChannelData(pm.ch5_voltage, pm.ch5_current) ||
            hasChannelData(pm.ch6_voltage, pm.ch6_current)
    val hasCh78 = hasChannelData(pm.ch7_voltage, pm.ch7_current) || hasChannelData(pm.ch8_voltage, pm.ch8_current)

    if (hasCh456) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            if (hasChannelData(pm.ch4_voltage, pm.ch4_current)) {
                PowerChannelColumn(Res.string.channel_4, pm.ch4_voltage ?: 0f, pm.ch4_current ?: 0f)
            }
            if (hasChannelData(pm.ch5_voltage, pm.ch5_current)) {
                PowerChannelColumn(Res.string.channel_5, pm.ch5_voltage ?: 0f, pm.ch5_current ?: 0f)
            }
            if (hasChannelData(pm.ch6_voltage, pm.ch6_current)) {
                PowerChannelColumn(Res.string.channel_6, pm.ch6_voltage ?: 0f, pm.ch6_current ?: 0f)
            }
        }
    }
    if (hasCh78) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            if (hasChannelData(pm.ch7_voltage, pm.ch7_current)) {
                PowerChannelColumn(Res.string.channel_7, pm.ch7_voltage ?: 0f, pm.ch7_current ?: 0f)
            }
            if (hasChannelData(pm.ch8_voltage, pm.ch8_current)) {
                PowerChannelColumn(Res.string.channel_8, pm.ch8_voltage ?: 0f, pm.ch8_current ?: 0f)
            }
        }
    }
}

private fun hasChannelData(voltage: Float?, current: Float?): Boolean = voltage != null || current != null

@Composable
private fun PowerChannelColumn(titleRes: StringResource, voltage: Float, current: Float) {
    Column {
        Text(
            text = stringResource(titleRes),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
        MetricValueRow(color = PowerMetric.VOLTAGE.color, text = MetricFormatter.voltage(voltage))
        MetricValueRow(color = PowerMetric.CURRENT.color, text = MetricFormatter.current(current))
    }
}

/** Retrieves the appropriate voltage depending on `channelSelected`. */
@Suppress("CyclomaticComplexMethod")
private fun retrieveVoltage(channelSelected: PowerChannel, telemetry: Telemetry): Float = when (channelSelected) {
    PowerChannel.ONE -> telemetry.power_metrics?.ch1_voltage ?: Float.NaN
    PowerChannel.TWO -> telemetry.power_metrics?.ch2_voltage ?: Float.NaN
    PowerChannel.THREE -> telemetry.power_metrics?.ch3_voltage ?: Float.NaN
    PowerChannel.FOUR -> telemetry.power_metrics?.ch4_voltage ?: Float.NaN
    PowerChannel.FIVE -> telemetry.power_metrics?.ch5_voltage ?: Float.NaN
    PowerChannel.SIX -> telemetry.power_metrics?.ch6_voltage ?: Float.NaN
    PowerChannel.SEVEN -> telemetry.power_metrics?.ch7_voltage ?: Float.NaN
    PowerChannel.EIGHT -> telemetry.power_metrics?.ch8_voltage ?: Float.NaN
}

/** Retrieves the appropriate current depending on `channelSelected`. */
@Suppress("CyclomaticComplexMethod")
private fun retrieveCurrent(channelSelected: PowerChannel, telemetry: Telemetry): Float = when (channelSelected) {
    PowerChannel.ONE -> telemetry.power_metrics?.ch1_current ?: Float.NaN
    PowerChannel.TWO -> telemetry.power_metrics?.ch2_current ?: Float.NaN
    PowerChannel.THREE -> telemetry.power_metrics?.ch3_current ?: Float.NaN
    PowerChannel.FOUR -> telemetry.power_metrics?.ch4_current ?: Float.NaN
    PowerChannel.FIVE -> telemetry.power_metrics?.ch5_current ?: Float.NaN
    PowerChannel.SIX -> telemetry.power_metrics?.ch6_current ?: Float.NaN
    PowerChannel.SEVEN -> telemetry.power_metrics?.ch7_current ?: Float.NaN
    PowerChannel.EIGHT -> telemetry.power_metrics?.ch8_current ?: Float.NaN
}
