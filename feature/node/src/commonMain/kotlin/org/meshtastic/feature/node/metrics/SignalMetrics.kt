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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.resources.snr
import org.meshtastic.core.ui.component.LoraSignalIndicator
import org.meshtastic.core.ui.theme.GraphColors.Blue
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.MeshPacket

private enum class SignalMetric(val color: Color) {
    SNR(Green),
    RSSI(Blue),
}

private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.rssi, color = SignalMetric.RSSI.color),
        LegendData(nameRes = Res.string.snr, color = SignalMetric.SNR.color),
    )

@Suppress("LongMethod")
@Composable
fun SignalMetricsScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val data = state.signalMetrics.filter { it.rx_time.toLong() >= timeFrame.timeThreshold() }

    val exportLauncher = rememberSaveFileLauncher { uri -> viewModel.saveSignalMetricsCSV(uri, data) }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.LOCAL_STATS,
        titleRes = Res.string.signal_quality,
        nodeName = state.node?.user?.long_name ?: "",
        data = data,
        timeProvider = { it.rx_time.toDouble() },
        onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.LOCAL_STATS) },
        onExportCsv = { exportLauncher("signal_metrics.csv", "text/csv") },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        chartPart = { modifier, selectedX, vicoScrollState, onPointSelected ->
            SignalMetricsChart(
                modifier = modifier,
                meshPackets = data.reversed(),
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(data) { _, meshPacket ->
                    SignalMetricsCard(
                        meshPacket = meshPacket,
                        isSelected = meshPacket.rx_time.toDouble() == selectedX,
                        onClick = { onCardClick(meshPacket.rx_time.toDouble()) },
                    )
                }
            }
        },
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun SignalMetricsChart(
    modifier: Modifier = Modifier,
    meshPackets: List<MeshPacket>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    MetricChartScaffold(isEmpty = meshPackets.isEmpty(), legendData = LEGEND_DATA, modifier = modifier) {
            modelProducer,
            chartModifier,
        ->
        val rssiColor = SignalMetric.RSSI.color
        val snrColor = SignalMetric.SNR.color

        val rssiData = remember(meshPackets) { meshPackets.filter { it.rx_rssi != 0 } }
        val snrData = remember(meshPackets) { meshPackets.filter { !it.rx_snr.isNaN() } }

        LaunchedEffect(rssiData, snrData) {
            modelProducer.runTransaction {
                if (rssiData.isNotEmpty()) {
                    /* Use separate lineSeries calls to associate them with different vertical axes */
                    lineSeries { series(x = rssiData.map { it.rx_time }, y = rssiData.map { it.rx_rssi }) }
                }
                if (snrData.isNotEmpty()) {
                    lineSeries { series(x = snrData.map { it.rx_time }, y = snrData.map { it.rx_snr }) }
                }
            }
        }

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    if (color == rssiColor) {
                        "RSSI: ${MetricFormatter.rssi(value.toInt())}"
                    } else {
                        "SNR: ${MetricFormatter.snr(value.toFloat())}"
                    }
                },
            )

        val rssiLayer =
            rememberConditionalLayer(
                hasData = rssiData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createStyledLine(rssiColor)),
                verticalAxisPosition = Axis.Position.Vertical.Start,
            )

        val snrLayer =
            rememberConditionalLayer(
                hasData = snrData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createDashedLine(snrColor)),
                verticalAxisPosition = Axis.Position.Vertical.End,
            )

        val layers = remember(rssiLayer, snrLayer) { listOfNotNull(rssiLayer, snrLayer) }

        if (layers.isNotEmpty()) {
            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = layers,
                startAxis =
                if (rssiData.isNotEmpty()) {
                    VerticalAxis.rememberStart(
                        label = ChartStyling.rememberAxisLabel(color = rssiColor),
                        valueFormatter = { _, value, _ -> MetricFormatter.rssi(value.toInt()) },
                    )
                } else {
                    null
                },
                endAxis =
                if (snrData.isNotEmpty()) {
                    VerticalAxis.rememberEnd(
                        label = ChartStyling.rememberAxisLabel(color = snrColor),
                        valueFormatter = { _, value, _ -> MetricFormatter.snr(value.toFloat()) },
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
private fun SignalMetricsCard(meshPacket: MeshPacket, isSelected: Boolean, onClick: () -> Unit) {
    val time = meshPacket.rx_time.toLong() * MS_PER_SEC
    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            /* Data */
            Box(modifier = Modifier.weight(weight = 5f).height(IntrinsicSize.Min)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    /* Time */
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = DateFormatter.formatDateTime(time),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    /* SNR and RSSI */
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricValueRow(color = SignalMetric.RSSI.color, text = MetricFormatter.rssi(meshPacket.rx_rssi))
                        Spacer(Modifier.width(12.dp))
                        MetricValueRow(color = SignalMetric.SNR.color, text = MetricFormatter.snr(meshPacket.rx_snr))
                    }
                }
            }

            /* Signal Indicator */
            Box(modifier = Modifier.weight(weight = 3f).height(IntrinsicSize.Max)) {
                LoraSignalIndicator(meshPacket.rx_snr, meshPacket.rx_rssi)
            }
        }
    }
}
