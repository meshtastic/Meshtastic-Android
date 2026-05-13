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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ble_devices
import org.meshtastic.core.resources.no_pax_metrics_logs
import org.meshtastic.core.resources.pax
import org.meshtastic.core.resources.pax_ble_format
import org.meshtastic.core.resources.pax_ble_marker
import org.meshtastic.core.resources.pax_metrics_log
import org.meshtastic.core.resources.pax_total_format
import org.meshtastic.core.resources.pax_total_marker
import org.meshtastic.core.resources.pax_wifi_format
import org.meshtastic.core.resources.pax_wifi_marker
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.resources.wifi_devices
import org.meshtastic.core.ui.component.IconInfo
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PeopleCount
import org.meshtastic.core.ui.theme.GraphColors.Orange
import org.meshtastic.core.ui.theme.GraphColors.Purple
import org.meshtastic.proto.Paxcount as ProtoPaxcount

private enum class PaxSeries(val color: Color, val legendRes: StringResource) {
    PAX(Color.Gray, Res.string.pax),
    BLE(Purple, Res.string.ble_devices),
    WIFI(Orange, Res.string.wifi_devices),
}

private val LEGEND_DATA =
    listOf(
        LegendData(PaxSeries.PAX.legendRes, PaxSeries.PAX.color),
        LegendData(PaxSeries.BLE.legendRes, PaxSeries.BLE.color),
        LegendData(PaxSeries.WIFI.legendRes, PaxSeries.WIFI.color),
    )

@Suppress("LongMethod")
@Composable
private fun PaxMetricsChart(
    modifier: Modifier = Modifier,
    totalSeries: List<Pair<Int, Int>>,
    bleSeries: List<Pair<Int, Int>>,
    wifiSeries: List<Pair<Int, Int>>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    MetricChartScaffold(isEmpty = totalSeries.isEmpty(), legendData = LEGEND_DATA, modifier = modifier) {
            modelProducer,
            chartModifier,
        ->
        val paxColor = PaxSeries.PAX.color
        val bleColor = PaxSeries.BLE.color
        val wifiColor = PaxSeries.WIFI.color

        LaunchedEffect(totalSeries, bleSeries, wifiSeries) {
            modelProducer.runTransaction {
                lineSeries {
                    series(x = bleSeries.map { it.first }, y = bleSeries.map { it.second })
                    series(x = wifiSeries.map { it.first }, y = wifiSeries.map { it.second })
                    series(x = totalSeries.map { it.first }, y = totalSeries.map { it.second })
                }
            }
        }

        val axisLabel = ChartStyling.rememberAxisLabel()
        val bleMarkerTemplate = stringResource(Res.string.pax_ble_marker)
        val wifiMarkerTemplate = stringResource(Res.string.pax_wifi_marker)
        val paxMarkerTemplate = stringResource(Res.string.pax_total_marker)
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    val formatted = formatString("%.0f", value)
                    when (color) {
                        bleColor -> bleMarkerTemplate.replace("%1\$s", formatted)
                        wifiColor -> wifiMarkerTemplate.replace("%1\$s", formatted)
                        paxColor -> paxMarkerTemplate.replace("%1\$s", formatted)
                        else -> formatted
                    }
                },
            )

        GenericMetricChart(
            modelProducer = modelProducer,
            modifier = chartModifier,
            layers =
            listOf(
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createGradientLine(
                            lineColor = bleColor,
                            interpolator = LineCartesianLayer.Interpolator.Sharp,
                        ),
                        ChartStyling.createGradientLine(
                            lineColor = wifiColor,
                            interpolator = LineCartesianLayer.Interpolator.Sharp,
                        ),
                        ChartStyling.createBoldLine(
                            lineColor = paxColor,
                            interpolator = LineCartesianLayer.Interpolator.Sharp,
                        ),
                    ),
                    rangeProvider = CartesianLayerRangeProvider.auto(),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(label = axisLabel),
            bottomAxis = CommonCharts.rememberBottomTimeAxis(),
            marker = marker,
            selectedX = selectedX,
            onPointSelected = onPointSelected,
            vicoScrollState = vicoScrollState,
        )
    }
}

@Composable
@Suppress("MagicNumber", "LongMethod")
fun PaxMetricsScreen(metricsViewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by metricsViewModel.state.collectAsStateWithLifecycle()
    val paxMetrics by metricsViewModel.filteredPaxMetrics.collectAsStateWithLifecycle()
    val timeFrame by metricsViewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by metricsViewModel.availableTimeFrames.collectAsStateWithLifecycle()

    // Prepare data for graph
    val graphData =
        remember(paxMetrics) {
            paxMetrics
                .map {
                    val t = (it.first.received_date / MS_PER_SEC).toInt()
                    Triple(t, it.second.ble, it.second.wifi)
                }
                .sortedBy { it.first }
        }
    val totalSeries = remember(graphData) { graphData.map { it.first to (it.second + it.third) } }
    val bleSeries = remember(graphData) { graphData.map { it.first to it.second } }
    val wifiSeries = remember(graphData) { graphData.map { it.first to it.third } }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.PAX,
        titleRes = Res.string.pax_metrics_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = paxMetrics,
        timeProvider = { (it.first.received_date / MS_PER_SEC).toDouble() },
        onRequestTelemetry = { metricsViewModel.requestTelemetry(TelemetryType.PAX) },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = metricsViewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        chartPart = { modifier, selectedX, vicoScrollState, onPointSelected ->
            if (graphData.isNotEmpty()) {
                PaxMetricsChart(
                    modifier = modifier,
                    totalSeries = totalSeries,
                    bleSeries = bleSeries,
                    wifiSeries = wifiSeries,
                    vicoScrollState = vicoScrollState,
                    selectedX = selectedX,
                    onPointSelected = onPointSelected,
                )
            }
        },
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            if (paxMetrics.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_pax_metrics_logs),
                    modifier = modifier.fillMaxSize().padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    itemsIndexed(paxMetrics) { _, (log, pax) ->
                        PaxMetricsItem(
                            log = log,
                            pax = pax,
                            isSelected = (log.received_date / MS_PER_SEC).toDouble() == selectedX,
                            onClick = { onCardClick((log.received_date / MS_PER_SEC).toDouble()) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun PaxcountInfo(
    pax: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.PeopleCount,
        contentDescription = stringResource(Res.string.pax_metrics_log),
        text = pax,
        contentColor = contentColor,
    )
}

@Composable
fun PaxMetricsItem(log: MeshLog, pax: ProtoPaxcount, isSelected: Boolean, onClick: () -> Unit) {
    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = DateFormatter.formatDateTime(log.received_date),
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    MetricValueRow(
                        color = PaxSeries.PAX.color,
                        text = stringResource(Res.string.pax_total_format, pax.ble + pax.wifi),
                    )
                    Spacer(Modifier.width(8.dp))
                    MetricValueRow(
                        color = PaxSeries.BLE.color,
                        text = stringResource(Res.string.pax_ble_format, pax.ble),
                    )
                    Spacer(Modifier.width(8.dp))
                    MetricValueRow(
                        color = PaxSeries.WIFI.color,
                        text = stringResource(Res.string.pax_wifi_format, pax.wifi),
                    )
                }

                Text(
                    text = stringResource(Res.string.uptime) + ": " + formatUptime(pax.uptime),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
