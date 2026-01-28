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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
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
import org.meshtastic.core.ui.component.OptionLabel
import org.meshtastic.core.ui.component.SlidingSelector
import org.meshtastic.core.ui.theme.GraphColors.InfantryBlue
import org.meshtastic.core.ui.theme.GraphColors.Red
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.TelemetryProtos.Telemetry
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor

@Suppress("MagicNumber")
private enum class Power(val color: Color, val min: Float, val max: Float) {
    CURRENT(InfantryBlue, -500f, 500f),
    ;

    /** Difference between the metrics `max` and `min` values. */
    fun difference() = max - min
}

private enum class PowerChannel(val strRes: StringResource) {
    ONE(Res.string.channel_1),
    TWO(Res.string.channel_2),
    THREE(Res.string.channel_3),
}

private const val VOLTAGE_STICK_TO_ZERO_RANGE = 2f

private val VOLTAGE_COLOR = Red

fun minMaxGraphVoltage(valueMin: Float, valueMax: Float): Pair<Float, Float> {
    val valueMin = floor(valueMin)
    val min =
        if (valueMin == 0f || (valueMin >= 0f && valueMin - VOLTAGE_STICK_TO_ZERO_RANGE <= 0f)) {
            0f
        } else {
            valueMin - VOLTAGE_STICK_TO_ZERO_RANGE
        }
    val max = ceil(valueMax)

    return Pair(min, max)
}

private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.current, color = Power.CURRENT.color, isLine = true, environmentMetric = null),
        LegendData(nameRes = Res.string.voltage, color = VOLTAGE_COLOR, isLine = true, environmentMetric = null),
    )

@Suppress("LongMethod")
@Composable
fun PowerMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val data = state.powerMetricsFiltered(selectedTimeFrame)
    var selectedChannel by remember { mutableStateOf(PowerChannel.ONE) }

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
                selectedChannel,
            )

            SlidingSelector(
                PowerChannel.entries.toList(),
                selectedChannel,
                onOptionSelected = { selectedChannel = it },
            ) {
                OptionLabel(stringResource(it.strRes))
            }
            Spacer(modifier = Modifier.height(2.dp))
            SlidingSelector(
                TimeFrame.entries.toList(),
                selectedTimeFrame,
                onOptionSelected = { viewModel.setTimeFrame(it) },
            ) {
                OptionLabel(stringResource(it.strRes))
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) { items(data) { telemetry -> PowerMetricsCard(telemetry) } }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun PowerMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedChannel: PowerChannel,
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val currentDiff = Power.CURRENT.difference()
    val (voltageMin, voltageMax) =
        remember(telemetries, selectedChannel) {
            minMaxGraphVoltage(
                retrieveVoltage(selectedChannel, telemetries.minBy { retrieveVoltage(selectedChannel, it) }),
                retrieveVoltage(selectedChannel, telemetries.maxBy { retrieveVoltage(selectedChannel, it) }),
            )
        }
    val voltageDiff = voltageMax - voltageMin

    LaunchedEffect(telemetries, selectedChannel) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = telemetries.map { it.time },
                    y = telemetries.map { (retrieveCurrent(selectedChannel, it) - Power.CURRENT.min) / currentDiff },
                )
                series(
                    x = telemetries.map { it.time },
                    y = telemetries.map { (retrieveVoltage(selectedChannel, it) - voltageMin) / voltageDiff },
                )
            }
        }
    }

    CartesianChartHost(
        chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(Power.CURRENT.color)),
                    ),
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(VOLTAGE_COLOR)),
                    ),
                ),
            ),
            startAxis =
            VerticalAxis.rememberStart(
                valueFormatter = { _, value, _ ->
                    val actualValue = value * currentDiff.toDouble() + Power.CURRENT.min
                    "%.0f".format(actualValue)
                },
            ),
            endAxis =
            VerticalAxis.rememberEnd(
                valueFormatter = { _, value, _ ->
                    val actualValue = value * voltageDiff.toDouble() + voltageMin
                    "%.1f".format(actualValue)
                },
            ),
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

    Legend(legendData = LEGEND_DATA, displayInfoIcon = false)
}

@Composable
private fun PowerMetricsCard(telemetry: Telemetry) {
    val time = telemetry.time * MS_PER_SEC
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
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
