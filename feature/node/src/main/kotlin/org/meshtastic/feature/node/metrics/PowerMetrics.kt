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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.channel_1
import org.meshtastic.core.strings.channel_2
import org.meshtastic.core.strings.channel_3
import org.meshtastic.core.strings.current
import org.meshtastic.core.strings.voltage
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.theme.GraphColors.InfantryBlue
import org.meshtastic.core.ui.theme.GraphColors.Red
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.proto.TelemetryProtos.Telemetry

private enum class PowerMetric(val color: Color) {
    CURRENT(InfantryBlue),
    VOLTAGE(Red),
}

private enum class PowerChannel(val strRes: StringResource) {
    ONE(Res.string.channel_1),
    TWO(Res.string.channel_2),
    THREE(Res.string.channel_3),
}

private val LEGEND_DATA =
    listOf(
        LegendData(
            nameRes = Res.string.current,
            color = PowerMetric.CURRENT.color,
            isLine = true,
            environmentMetric = null,
        ),
        LegendData(
            nameRes = Res.string.voltage,
            color = PowerMetric.VOLTAGE.color,
            isLine = true,
            environmentMetric = null,
        ),
    )

@Suppress("LongMethod")
@Composable
fun PowerMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val data = state.powerMetrics
    var selectedChannel by remember { mutableStateOf(PowerChannel.ONE) }

    val lazyListState = rememberLazyListState()
    val vicoScrollState = rememberVicoScrollState()
    val coroutineScope = rememberCoroutineScope()

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
                        IconButton(onClick = { viewModel.requestTelemetry(TelemetryType.POWER) }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
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
            PowerMetricsChart(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f),
                telemetries = data.reversed(),
                selectedChannel = selectedChannel,
                vicoScrollState = vicoScrollState,
                onPointSelected = { x ->
                    val index = data.indexOfFirst { it.time.toDouble() == x }
                    if (index != -1) {
                        coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                    }
                },
            )

            LazyColumn(modifier = Modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(data) { _, telemetry ->
                    PowerMetricsCard(
                        telemetry = telemetry,
                        onClick = {
                            coroutineScope.launch {
                                vicoScrollState.animateScroll(Scroll.Absolute.x(telemetry.time.toDouble(), 0.5f))
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
private fun PowerMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedChannel: PowerChannel,
    vicoScrollState: VicoScrollState,
    onPointSelected: (Double) -> Unit,
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(telemetries, selectedChannel) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = telemetries.map { it.time }, y = telemetries.map { retrieveCurrent(selectedChannel, it) })
            }
            lineSeries {
                series(x = telemetries.map { it.time }, y = telemetries.map { retrieveVoltage(selectedChannel, it) })
            }
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

    CartesianChartHost(
        chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createBoldLine(PowerMetric.CURRENT.color, ChartStyling.MEDIUM_POINT_SIZE_DP),
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createGradientLine(
                        PowerMetric.VOLTAGE.color,
                        ChartStyling.MEDIUM_POINT_SIZE_DP,
                    ),
                ),
                verticalAxisPosition = Axis.Position.Vertical.End,
            ),
            startAxis =
            VerticalAxis.rememberStart(
                label = axisLabel,
                valueFormatter = { _, value, _ -> "%.0f mA".format(value) },
            ),
            endAxis =
            VerticalAxis.rememberEnd(
                label = axisLabel,
                valueFormatter = { _, value, _ -> "%.1f V".format(value) },
            ),
            bottomAxis =
            HorizontalAxis.rememberBottom(
                label = axisLabel,
                valueFormatter = CommonCharts.dynamicTimeFormatter,
            ),
            marker = ChartStyling.rememberMarker(),
            markerVisibilityListener = markerVisibilityListener,
        ),
        modelProducer = modelProducer,
        modifier = modifier.padding(8.dp),
        scrollState = vicoScrollState,
        zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.Content),
    )

    Legend(legendData = LEGEND_DATA, displayInfoIcon = false)
}

@Composable
private fun PowerMetricsCard(telemetry: Telemetry, onClick: () -> Unit) {
    val time = telemetry.time * MS_PER_SEC
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onClick() }) {
        Surface {
            SelectionContainer {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        /* Time */
                        Row {
                            Text(
                                text = DATE_TIME_FORMAT.format(time),
                                style = TextStyle(fontWeight = FontWeight.Bold),
                                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            if (telemetry.powerMetrics.hasCh1Current() || telemetry.powerMetrics.hasCh1Voltage()) {
                                PowerChannelColumn(
                                    Res.string.channel_1,
                                    telemetry.powerMetrics.ch1Voltage,
                                    telemetry.powerMetrics.ch1Current,
                                )
                            }
                            if (telemetry.powerMetrics.hasCh2Current() || telemetry.powerMetrics.hasCh2Voltage()) {
                                PowerChannelColumn(
                                    Res.string.channel_2,
                                    telemetry.powerMetrics.ch2Voltage,
                                    telemetry.powerMetrics.ch2Current,
                                )
                            }
                            if (telemetry.powerMetrics.hasCh3Current() || telemetry.powerMetrics.hasCh3Voltage()) {
                                PowerChannelColumn(
                                    Res.string.channel_3,
                                    telemetry.powerMetrics.ch3Voltage,
                                    telemetry.powerMetrics.ch3Current,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerChannelColumn(titleRes: StringResource, voltage: Float, current: Float) {
    Column {
        Text(
            text = stringResource(titleRes),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
        Text(
            text = "%.2fV".format(voltage),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
        Text(
            text = "%.1fmA".format(current),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
    }
}

/** Retrieves the appropriate voltage depending on `channelSelected`. */
private fun retrieveVoltage(channelSelected: PowerChannel, telemetry: Telemetry): Float = when (channelSelected) {
    PowerChannel.ONE -> telemetry.powerMetrics.ch1Voltage
    PowerChannel.TWO -> telemetry.powerMetrics.ch2Voltage
    PowerChannel.THREE -> telemetry.powerMetrics.ch3Voltage
}

/** Retrieves the appropriate current depending on `channelSelected`. */
private fun retrieveCurrent(channelSelected: PowerChannel, telemetry: Telemetry): Float = when (channelSelected) {
    PowerChannel.ONE -> telemetry.powerMetrics.ch1Current
    PowerChannel.TWO -> telemetry.powerMetrics.ch2Current
    PowerChannel.THREE -> telemetry.powerMetrics.ch3Current
}
