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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.effectiveLongName
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
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Save
import org.meshtastic.core.ui.theme.GraphColors.Gold
import org.meshtastic.core.ui.theme.GraphColors.InfantryBlue
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.Telemetry

private enum class PowerMetric(val color: Color) {
    CURRENT(InfantryBlue),
    VOLTAGE(Gold),
}

// PowerMetrics.ch4-ch8 are deprecated in the proto: protobufs#877 moved multi-channel ADC readings to
// EnvironmentMetrics.adc_voltage_ch0-7, which this app already reads and graphs. These reads stay because
// firmware predating that move still reports on ch4-ch8, and dropping them would blank the channels those
// devices show today. Delete them, and PowerChannel.FOUR..EIGHT, once the proto removes the fields.
// Note the migration covers voltage only - ch4-ch8 *current* has no successor field at all.
internal enum class PowerChannel(val strRes: StringResource) {
    ONE(Res.string.channel_1),
    TWO(Res.string.channel_2),
    THREE(Res.string.channel_3),
    FOUR(Res.string.channel_4),
    FIVE(Res.string.channel_5),
    SIX(Res.string.channel_6),
    SEVEN(Res.string.channel_7),
    EIGHT(Res.string.channel_8),
    ;

    /** The reported voltage in volts, or null when this channel did not report one. */
    @Suppress("CyclomaticComplexMethod", "DEPRECATION")
    fun voltage(pm: org.meshtastic.proto.PowerMetrics): Float? = when (this) {
        ONE -> pm.ch1_voltage
        TWO -> pm.ch2_voltage
        THREE -> pm.ch3_voltage
        FOUR -> pm.ch4_voltage
        FIVE -> pm.ch5_voltage
        SIX -> pm.ch6_voltage
        SEVEN -> pm.ch7_voltage
        EIGHT -> pm.ch8_voltage
    }

    /** The reported current in milliamps, or null when this channel did not report one. */
    @Suppress("CyclomaticComplexMethod", "DEPRECATION")
    fun current(pm: org.meshtastic.proto.PowerMetrics): Float? = when (this) {
        ONE -> pm.ch1_current
        TWO -> pm.ch2_current
        THREE -> pm.ch3_current
        FOUR -> pm.ch4_current
        FIVE -> pm.ch5_current
        SIX -> pm.ch6_current
        SEVEN -> pm.ch7_current
        EIGHT -> pm.ch8_current
    }
}

/** The user's label for a power channel, or [fallback] ("Channel N") when it is missing or blank. */
internal fun powerChannelLabel(customLabel: String?, fallback: String): String =
    customLabel?.takeIf { it.isNotBlank() } ?: fallback

/** The channels in [metrics] that report a voltage or a current, in channel order. */
internal fun reportingPowerChannels(metrics: org.meshtastic.proto.PowerMetrics?): List<PowerChannel> =
    PowerChannel.entries.filter { metrics != null && (it.voltage(metrics) != null || it.current(metrics) != null) }

/** Display text for the halves a channel reported; a half it did not report is null, never "0". */
internal data class PowerChannelTexts(val voltage: String?, val current: String?)

internal fun powerChannelTexts(voltage: Float?, current: Float?) = PowerChannelTexts(
    voltage = voltage?.let { MetricFormatter.voltage(it) },
    current = current?.let { MetricFormatter.current(it) },
)

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
    var chosenChannel by rememberSaveable { mutableStateOf<PowerChannel?>(null) }
    val selectedChannel =
        chosenChannel?.takeIf { it in availableChannels } ?: availableChannels.firstOrNull() ?: PowerChannel.ONE
    val channelLabels = state.node?.powerChannelLabels.orEmpty()

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.POWER,
        titleRes = Res.string.power_metrics_log,
        nodeName = state.node?.effectiveLongName.orEmpty(),
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
                        val customLabel = channelLabels.getOrNull(channel.ordinal).orEmpty()
                        FilterChip(
                            selected = selectedChannel == channel,
                            onClick = { chosenChannel = channel },
                            label = { Text(powerChannelLabel(customLabel, stringResource(channel.strRes))) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val nodeNum = state.node?.num
                if (nodeNum != null) {
                    PowerChannelLabelEditor(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        currentLabel = channelLabels.getOrNull(selectedChannel.ordinal).orEmpty(),
                        channelName = stringResource(selectedChannel.strRes),
                        onSave = { label -> viewModel.setPowerChannelLabel(nodeNum, selectedChannel.ordinal, label) },
                    )
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
                itemsIndexed(
                    data,
                    key = { index, telemetry -> "${telemetry.time}_$index" },
                    contentType = { _, _ -> "power_metrics" },
                ) { _, telemetry ->
                    PowerMetricsCard(
                        telemetry = telemetry,
                        channelLabels = channelLabels,
                        isSelected = telemetry.time.toDouble() == selectedX,
                        onClick = { onCardClick(telemetry.time.toDouble()) },
                    )
                }
            }
        },
    )
}

/** Lets the user assign a role/name (e.g. "Solar", "Battery") to the currently selected power channel. */
@Composable
private fun PowerChannelLabelEditor(
    currentLabel: String,
    channelName: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by remember(currentLabel) { mutableStateOf(currentLabel) }
    val edited = label.trim() != currentLabel.trim()
    val keyboardController = LocalSoftwareKeyboardController.current
    val save: () -> Unit = {
        onSave(label.trim())
        keyboardController?.hide()
    }
    OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        modifier = modifier.fillMaxWidth(),
        // Floating label (not a placeholder) so the channel stays identifiable after a custom name is typed.
        label = { Text(channelName) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = save, enabled = edited) {
                Icon(imageVector = MeshtasticIcons.Save, contentDescription = stringResource(Res.string.save))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
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
        // The formatter runs outside composition, so resolve the labels here.
        val currentLabel = stringResource(Res.string.current)
        val voltageLabel = stringResource(Res.string.voltage)
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color) {
                        currentColor -> "$currentLabel: ${MetricFormatter.current(value.toFloat(), 1)}"
                        voltageColor -> "$voltageLabel: ${MetricFormatter.voltage(value.toFloat(), 2)}"
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
                    lineModel {
                        splitAtGaps(currentData) { it.time }
                            .forEach { run ->
                                series(x = run.map { it.time }, y = run.map { retrieveCurrent(selectedChannel, it) })
                            }
                    }
                }
                if (voltageData.isNotEmpty()) {
                    lineModel {
                        splitAtGaps(voltageData) { it.time }
                            .forEach { run ->
                                series(x = run.map { it.time }, y = run.map { retrieveVoltage(selectedChannel, it) })
                            }
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
                        valueFormatter = { _, value, _ -> MetricFormatter.voltage(value.toFloat(), 1) },
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
private fun PowerMetricsCard(
    telemetry: Telemetry,
    channelLabels: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
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
                    PowerChannelsRows(pm, channelLabels)
                }
            }
        }
    }
}

@Composable
private fun PowerChannelsRows(pm: org.meshtastic.proto.PowerMetrics, channelLabels: List<String>) {
    reportingPowerChannels(pm).chunked(CHANNELS_PER_ROW).forEachIndexed { index, row ->
        if (index > 0) Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            row.forEach { channel ->
                PowerChannelColumn(
                    title = powerChannelLabel(channelLabels.getOrNull(channel.ordinal), stringResource(channel.strRes)),
                    voltage = channel.voltage(pm),
                    current = channel.current(pm),
                )
            }
        }
    }
}

private const val CHANNELS_PER_ROW = 3

@Composable
private fun PowerChannelColumn(title: String, voltage: Float?, current: Float?) {
    val texts = powerChannelTexts(voltage, current)
    Column {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        texts.voltage?.let { MetricValueRow(color = PowerMetric.VOLTAGE.color, text = it) }
        texts.current?.let { MetricValueRow(color = PowerMetric.CURRENT.color, text = it) }
    }
}

/** Retrieves the appropriate voltage depending on `channelSelected`. */
private fun retrieveVoltage(channelSelected: PowerChannel, telemetry: Telemetry): Float =
    telemetry.power_metrics?.let { channelSelected.voltage(it) } ?: Float.NaN

/** Retrieves the appropriate current depending on `channelSelected`. */
private fun retrieveCurrent(channelSelected: PowerChannel, telemetry: Telemetry): Float =
    telemetry.power_metrics?.let { channelSelected.current(it) } ?: Float.NaN
