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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.request_telemetry
import org.meshtastic.core.strings.rssi
import org.meshtastic.core.strings.rssi_definition
import org.meshtastic.core.strings.signal_quality
import org.meshtastic.core.strings.snr
import org.meshtastic.core.strings.snr_definition
import org.meshtastic.core.ui.component.LoraSignalIndicator
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SnrAndRssi
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.feature.node.metrics.CommonCharts.SCROLL_BIAS
import org.meshtastic.proto.MeshProtos.MeshPacket

private enum class SignalMetric(val color: Color) {
    SNR(Color.Green),
    RSSI(Color.Blue),
}

private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.rssi, color = SignalMetric.RSSI.color, environmentMetric = null),
        LegendData(nameRes = Res.string.snr, color = SignalMetric.SNR.color, environmentMetric = null),
    )

@Suppress("LongMethod")
@Composable
fun SignalMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var displayInfoDialog by remember { mutableStateOf(false) }
    val data = state.signalMetrics

    val lazyListState = rememberLazyListState()
    val vicoScrollState = rememberVicoScrollState()
    val coroutineScope = rememberCoroutineScope()
    var selectedX by remember { mutableStateOf<Double?>(null) }

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
                        IconButton(onClick = { viewModel.requestTelemetry(TelemetryType.LOCAL_STATS) }) {
                            androidx.compose.material3.Icon(
                                imageVector = MeshtasticIcons.Refresh,
                                contentDescription =
                                stringResource(Res.string.signal_quality) +
                                    " " +
                                    stringResource(Res.string.request_telemetry),
                            )
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
                        Pair(Res.string.snr, Res.string.snr_definition),
                        Pair(Res.string.rssi, Res.string.rssi_definition),
                    ),
                    onDismiss = { displayInfoDialog = false },
                )
            }

            SignalMetricsChart(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f),
                meshPackets = data.reversed(),
                promptInfoDialog = { displayInfoDialog = true },
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = { x ->
                    selectedX = x
                    val index = data.indexOfFirst { it.rxTime.toDouble() == x }
                    if (index != -1) {
                        coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                    }
                },
            )

            LazyColumn(modifier = Modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(data) { _, meshPacket ->
                    SignalMetricsCard(
                        meshPacket = meshPacket,
                        isSelected = meshPacket.rxTime.toDouble() == selectedX,
                        onClick = {
                            selectedX = meshPacket.rxTime.toDouble()
                            coroutineScope.launch {
                                vicoScrollState.animateScroll(
                                    Scroll.Absolute.x(meshPacket.rxTime.toDouble(), SCROLL_BIAS),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun SignalMetricsChart(
    modifier: Modifier = Modifier,
    meshPackets: List<MeshPacket>,
    promptInfoDialog: () -> Unit,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    ChartHeader(amount = meshPackets.size)
    if (meshPackets.isEmpty()) {
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(meshPackets) {
        modelProducer.runTransaction {
            /* Use separate lineSeries calls to associate them with different vertical axes */
            lineSeries { series(x = meshPackets.map { it.rxTime }, y = meshPackets.map { it.rxRssi }) }
            lineSeries { series(x = meshPackets.map { it.rxTime }, y = meshPackets.map { it.rxSnr }) }
        }
    }

    val markerVisibilityListener =
        remember(onPointSelected) {
            object : CartesianMarkerVisibilityListener {
                override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                    targets.firstOrNull()?.let { onPointSelected(it.x) }
                }

                override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                    targets.firstOrNull()?.let { onPointSelected(it.x) }
                }
            }
        }

    val axisLabel = ChartStyling.rememberAxisLabel()
    val marker =
        ChartStyling.rememberMarker(
            valueFormatter = { _, targets ->
                targets.joinToString { target ->
                    @Suppress("MagicNumber")
                    when (target) {
                        is LineCartesianLayerMarkerTarget -> {
                            target.points.joinToString { point ->
                                // Vico 3.x stores real Y values if not normalized
                                if (point.entry.y < -20) { // Probable RSSI
                                    "RSSI: %.0f dBm".format(point.entry.y)
                                } else { // Probable SNR
                                    "SNR: %.1f dB".format(point.entry.y)
                                }
                            }
                        }
                        else -> ""
                    }
                }
            },
        )

    CartesianChartHost(
        chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createPointOnlyLine(SignalMetric.RSSI.color, ChartStyling.LARGE_POINT_SIZE_DP),
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createPointOnlyLine(SignalMetric.SNR.color, ChartStyling.LARGE_POINT_SIZE_DP),
                ),
                verticalAxisPosition = Axis.Position.Vertical.End,
            ),
            startAxis =
            VerticalAxis.rememberStart(
                label = axisLabel,
                valueFormatter = { _, value, _ -> "%.0f dBm".format(value) },
            ),
            endAxis =
            VerticalAxis.rememberEnd(
                label = axisLabel,
                valueFormatter = { _, value, _ -> "%.1f dB".format(value) },
            ),
            bottomAxis =
            HorizontalAxis.rememberBottom(
                label = axisLabel,
                valueFormatter = CommonCharts.dynamicTimeFormatter,
            ),
            marker = marker,
            markerVisibilityListener = markerVisibilityListener,
            persistentMarkers = { _ -> selectedX?.let { x -> marker at x } },
        ),
        modelProducer = modelProducer,
        modifier = modifier.padding(8.dp),
        scrollState = vicoScrollState,
        zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.Content),
    )

    Legend(legendData = LEGEND_DATA, promptInfoDialog = promptInfoDialog)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignalMetricsCard(meshPacket: MeshPacket, isSelected: Boolean, onClick: () -> Unit) {
    val time = meshPacket.rxTime * MS_PER_SEC
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
                Row(modifier = Modifier.fillMaxWidth()) {
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
                            SnrAndRssi(meshPacket.rxSnr, meshPacket.rxRssi)
                        }
                    }

                    /* Signal Indicator */
                    Box(modifier = Modifier.weight(weight = 3f).height(IntrinsicSize.Max)) {
                        LoraSignalIndicator(meshPacket.rxSnr, meshPacket.rxRssi)
                    }
                }
            }
        }
    }
}
