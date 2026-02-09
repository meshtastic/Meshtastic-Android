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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.channel_1
import org.meshtastic.core.strings.channel_2
import org.meshtastic.core.strings.channel_3
import org.meshtastic.core.strings.current
import org.meshtastic.core.strings.logs
import org.meshtastic.core.strings.power_metrics_log
import org.meshtastic.core.strings.voltage
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.theme.GraphColors.Gold
import org.meshtastic.core.ui.theme.GraphColors.InfantryBlue
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.proto.Telemetry

private enum class PowerMetric(val color: Color) {
    CURRENT(InfantryBlue),
    VOLTAGE(Gold),
}

private enum class PowerChannel(val strRes: StringResource) {
    ONE(Res.string.channel_1),
    TWO(Res.string.channel_2),
    THREE(Res.string.channel_3),
}

private const val Y_AXIS_WEIGHT = 0.1f

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

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun PowerMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val data = state.powerMetrics.filter { (it.time ?: 0).toLong() >= timeFrame.timeThreshold() }
    var selectedChannel by remember { mutableStateOf(PowerChannel.ONE) }

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
                title = state.node?.user?.long_name ?: "",
                subtitle =
                stringResource(Res.string.power_metrics_log) + " (${data.size} ${stringResource(Res.string.logs)})",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        IconButton(onClick = { viewModel.requestTelemetry(TelemetryType.POWER) }) {
                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PowerChannel.entries.forEach { channel ->
                    FilterChip(
                        selected = selectedChannel == channel,
                        onClick = { selectedChannel = channel },
                        label = { Text(stringResource(channel.strRes)) },
                    )
                }
            }

            AdaptiveMetricLayout(
                chartPart = { modifier ->
                    PowerMetricsChart(
                        modifier = modifier,
                        telemetries = data.reversed(),
                        selectedChannel = selectedChannel,
                        vicoScrollState = vicoScrollState,
                        selectedX = selectedX,
                        onPointSelected = { x ->
                            selectedX = x
                            val index = data.indexOfFirst { (it.time ?: 0).toDouble() == x }
                            if (index != -1) {
                                coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                            }
                        },
                    )
                },
                listPart = { modifier ->
                    LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                        itemsIndexed(data) { _, telemetry ->
                            PowerMetricsCard(
                                telemetry = telemetry,
                                isSelected = (telemetry.time ?: 0).toDouble() == selectedX,
                                onClick = {
                                    selectedX = (telemetry.time ?: 0).toDouble()
                                    coroutineScope.launch {
                                        vicoScrollState.animateScroll(
                                            Scroll.Absolute.x((telemetry.time ?: 0).toDouble(), 0.5f),
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
            )
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
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    Column(modifier = modifier) {
        if (telemetries.isEmpty()) return@Column

        val modelProducer = remember { CartesianChartModelProducer() }
        val currentColor = PowerMetric.CURRENT.color
        val voltageColor = PowerMetric.VOLTAGE.color
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color.copy(1f)) {
                        currentColor -> "Current: %.0f mA".format(value)
                        voltageColor -> "Voltage: %.1f V".format(value)
                        else -> "%.1f".format(value)
                    }
                },
            )

        LaunchedEffect(telemetries, selectedChannel) {
            modelProducer.runTransaction {
                lineSeries {
                    val currentData = telemetries.filter { !retrieveCurrent(selectedChannel, it).isNaN() }
                    series(
                        x = currentData.map { it.time ?: 0 },
                        y = currentData.map { retrieveCurrent(selectedChannel, it) },
                    )
                }
                lineSeries {
                    val voltageData = telemetries.filter { !retrieveVoltage(selectedChannel, it).isNaN() }
                    series(
                        x = voltageData.map { it.time ?: 0 },
                        y = voltageData.map { retrieveVoltage(selectedChannel, it) },
                    )
                }
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
                        ChartStyling.createBoldLine(currentColor, ChartStyling.MEDIUM_POINT_SIZE_DP),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                ),
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createGradientLine(voltageColor, ChartStyling.MEDIUM_POINT_SIZE_DP),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.End,
                ),
            ),
            startAxis =
            VerticalAxis.rememberStart(
                label = ChartStyling.rememberAxisLabel(color = currentColor),
                valueFormatter = { _, value, _ -> "%.0f mA".format(value) },
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
private fun PowerMetricsCard(telemetry: Telemetry, isSelected: Boolean, onClick: () -> Unit) {
    val time = (telemetry.time ?: 0).toLong() * MS_PER_SEC
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
        Surface {
            SelectionContainer {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        /* Time */
                        Row {
                            Text(
                                text = DATE_TIME_FORMAT.format(time),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            val pm = telemetry.power_metrics
                            if (pm != null) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricIndicator(PowerMetric.VOLTAGE.color)
            Spacer(Modifier.width(4.dp))
            Text(
                text = "%.2fV".format(voltage),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricIndicator(PowerMetric.CURRENT.color)
            Spacer(Modifier.width(4.dp))
            Text(
                text = "%.1fmA".format(current),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
    }
}

/** Retrieves the appropriate voltage depending on `channelSelected`. */
private fun retrieveVoltage(channelSelected: PowerChannel, telemetry: Telemetry): Float = when (channelSelected) {
    PowerChannel.ONE -> telemetry.power_metrics?.ch1_voltage ?: Float.NaN
    PowerChannel.TWO -> telemetry.power_metrics?.ch2_voltage ?: Float.NaN
    PowerChannel.THREE -> telemetry.power_metrics?.ch3_voltage ?: Float.NaN
}

/** Retrieves the appropriate current depending on `channelSelected`. */
private fun retrieveCurrent(channelSelected: PowerChannel, telemetry: Telemetry): Float = when (channelSelected) {
    PowerChannel.ONE -> telemetry.power_metrics?.ch1_current ?: Float.NaN
    PowerChannel.TWO -> telemetry.power_metrics?.ch2_current ?: Float.NaN
    PowerChannel.THREE -> telemetry.power_metrics?.ch3_current ?: Float.NaN
}
