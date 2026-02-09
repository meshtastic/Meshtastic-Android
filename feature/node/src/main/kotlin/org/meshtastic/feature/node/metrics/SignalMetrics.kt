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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.rssi
import org.meshtastic.core.strings.rssi_definition
import org.meshtastic.core.strings.signal_quality
import org.meshtastic.core.strings.snr
import org.meshtastic.core.strings.snr_definition
import org.meshtastic.core.ui.component.LoraSignalIndicator
import org.meshtastic.core.ui.theme.GraphColors.Blue
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.proto.MeshPacket

private enum class SignalMetric(val color: Color) {
    SNR(Green),
    RSSI(Blue),
}

private const val Y_AXIS_WEIGHT = 0.1f
private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.rssi, color = SignalMetric.RSSI.color, environmentMetric = null),
        LegendData(nameRes = Res.string.snr, color = SignalMetric.SNR.color, environmentMetric = null),
    )

@Suppress("LongMethod")
@Composable
fun SignalMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.signalMetrics

    BaseMetricScreen(
        viewModel = viewModel,
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.LOCAL_STATS,
        titleRes = Res.string.signal_quality,
        data = data,
        timeProvider = { (it.rx_time ?: 0).toDouble() },
        infoData =
        listOf(
            InfoDialogData(Res.string.snr, Res.string.snr_definition, SignalMetric.SNR.color),
            InfoDialogData(Res.string.rssi, Res.string.rssi_definition, SignalMetric.RSSI.color),
        ),
        chartPart = { modifier, selectedX, vicoScrollState, onPointSelected ->
            SignalMetricsChart(
                modifier = modifier,
                meshPackets = data.reversed(),
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { modifier, selectedX, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize()) {
                itemsIndexed(data) { _, meshPacket ->
                    SignalMetricsCard(
                        meshPacket = meshPacket,
                        isSelected = (meshPacket.rx_time ?: 0).toDouble() == selectedX,
                        onClick = { onCardClick((meshPacket.rx_time ?: 0).toDouble()) },
                    )
                }
            }
        },
    )
}

@Suppress("LongMethod")
@Composable
private fun SignalMetricsChart(
    modifier: Modifier = Modifier,
    meshPackets: List<MeshPacket>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    Column(modifier = modifier) {
        if (meshPackets.isEmpty()) return@Column

        val modelProducer = remember { CartesianChartModelProducer() }

        LaunchedEffect(meshPackets) {
            modelProducer.runTransaction {
                /* Use separate lineSeries calls to associate them with different vertical axes */
                lineSeries {
                    val rssiData = meshPackets.filter { (it.rx_rssi ?: 0) != 0 }
                    series(x = rssiData.map { it.rx_time ?: 0 }, y = rssiData.map { it.rx_rssi ?: 0 })
                }
                lineSeries {
                    val snrData = meshPackets.filter { !((it.rx_snr ?: Float.NaN).isNaN()) }
                    series(x = snrData.map { it.rx_time ?: 0 }, y = snrData.map { it.rx_snr ?: 0f })
                }
            }
        }

        val rssiColor = SignalMetric.RSSI.color
        val snrColor = SignalMetric.SNR.color

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    if (color.copy(alpha = 1f) == rssiColor) {
                        "RSSI: %.0f dBm".format(value)
                    } else {
                        "SNR: %.1f dB".format(value)
                    }
                },
            )

        GenericMetricChart(
            modelProducer = modelProducer,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).padding(bottom = 0.dp),
            layers =
            listOf(
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createPointOnlyLine(rssiColor, ChartStyling.LARGE_POINT_SIZE_DP),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                ),
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createPointOnlyLine(snrColor, ChartStyling.LARGE_POINT_SIZE_DP),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.End,
                ),
            ),
            startAxis =
            VerticalAxis.rememberStart(
                label = ChartStyling.rememberAxisLabel(color = rssiColor),
                valueFormatter = { _, value, _ -> "%.0f dBm".format(value) },
            ),
            endAxis =
            VerticalAxis.rememberEnd(
                label = ChartStyling.rememberAxisLabel(color = snrColor),
                valueFormatter = { _, value, _ -> "%.1f dB".format(value) },
            ),
            bottomAxis =
            HorizontalAxis.rememberBottom(
                label = ChartStyling.rememberAxisLabel(),
                valueFormatter = CommonCharts.dynamicTimeFormatter,
                itemPlacer = ChartStyling.rememberItemPlacer(spacing = 50),
                labelRotationDegrees = 45f,
            ),
            marker = marker,
            selectedX = selectedX,
            onPointSelected = onPointSelected,
            vicoScrollState = vicoScrollState,
        )

        Legend(legendData = LEGEND_DATA, modifier = Modifier.padding(top = 0.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignalMetricsCard(meshPacket: MeshPacket, isSelected: Boolean, onClick: () -> Unit) {
    val time = (meshPacket.rx_time ?: 0).toLong() * MS_PER_SEC
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    /* Data */
                    Box(modifier = Modifier.weight(weight = 5f).height(IntrinsicSize.Min)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            /* Time */
                            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = DATE_TIME_FORMAT.format(time),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            /* SNR and RSSI */
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetricIndicator(SignalMetric.RSSI.color)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "%.0f dBm".format((meshPacket.rx_rssi ?: 0).toFloat()),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(Modifier.width(12.dp))
                                MetricIndicator(SignalMetric.SNR.color)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "%.1f dB".format(meshPacket.rx_snr ?: 0f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    /* Signal Indicator */
                    Box(modifier = Modifier.weight(weight = 3f).height(IntrinsicSize.Max)) {
                        LoraSignalIndicator(meshPacket.rx_snr ?: 0f, meshPacket.rx_rssi ?: 0)
                    }
                }
            }
        }
    }
}
