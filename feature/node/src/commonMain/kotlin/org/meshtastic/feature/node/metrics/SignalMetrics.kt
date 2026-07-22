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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.busy_noise_floor
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.local_stats_bad
import org.meshtastic.core.resources.local_stats_nodes
import org.meshtastic.core.resources.local_stats_noise
import org.meshtastic.core.resources.local_stats_relays
import org.meshtastic.core.resources.local_stats_traffic
import org.meshtastic.core.resources.local_stats_uptime
import org.meshtastic.core.resources.no_local_stats
import org.meshtastic.core.resources.noise_floor
import org.meshtastic.core.resources.noise_floor_definition
import org.meshtastic.core.resources.noise_floor_no_reading
import org.meshtastic.core.resources.request
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.rssi_definition
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.snr_definition
import org.meshtastic.core.ui.component.LoraSignalIndicator
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Save
import org.meshtastic.core.ui.theme.GraphColors.Blue
import org.meshtastic.core.ui.theme.GraphColors.Gold
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.Orange
import org.meshtastic.core.ui.theme.GraphColors.Red
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Telemetry

private const val QUIET_NOISE_FLOOR_DBM = -95
private const val BUSY_FLOOR_DBM = -85
private const val MIN_DBM_AXIS = -120.0
private const val MAX_DBM_AXIS = 0.0

private enum class SignalMetric(val color: Color) {
    NOISE_FLOOR(Gold),
    BUSY_FLOOR(Red),
    SNR(Green),
    RSSI(Blue),
}

private val LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.noise_floor, color = SignalMetric.NOISE_FLOOR.color, isLine = true),
        LegendData(nameRes = Res.string.rssi, color = SignalMetric.RSSI.color),
        LegendData(nameRes = Res.string.snr, color = SignalMetric.SNR.color),
    )

private sealed interface SignalLogEntry {
    val timeSeconds: Int

    /** Stable, collision-free identity for use as a LazyColumn item key across both entry types. */
    val key: Any

    /** Distinguishes the two card layouts so Compose can reuse compositions per type. */
    val contentType: Any

    data class LocalStatsEntry(val telemetry: Telemetry, val index: Int) : SignalLogEntry {
        override val timeSeconds: Int = telemetry.time

        // Local stats telemetry is an id-less proto; the source-list index disambiguates same-second samples.
        override val key: Any = "local_stats_${telemetry.time}_$index"
        override val contentType: Any = "local_stats"
    }

    data class PacketEntry(val meshPacket: MeshPacket) : SignalLogEntry {
        override val timeSeconds: Int = meshPacket.rx_time
        override val key: Any = "packet_${meshPacket.id}"
        override val contentType: Any = "signal_packet"
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SignalMetricsScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val threshold = timeFrame.timeThreshold()
    val signalData = state.signalMetrics.filter { it.rx_time.toLong() >= threshold }
    val localStatsData = state.localStats.filter { it.time.toLong() >= threshold && it.local_stats != null }
    val data =
        remember(signalData, localStatsData) {
            (
                localStatsData.mapIndexed { index, telemetry -> SignalLogEntry.LocalStatsEntry(telemetry, index) } +
                    signalData.map { SignalLogEntry.PacketEntry(it) }
                )
                .sortedByDescending { it.timeSeconds }
        }
    val hasNoiseFloor = remember(localStatsData) { localStatsData.any { it.local_stats?.noise_floor != 0 } }
    val hasRssi = remember(signalData) { signalData.any { it.rx_rssi != 0 } }
    val hasSnr = remember(signalData) { signalData.any { !it.rx_snr.isNaN() } }
    val hasAnyLocalStats = state.localStats.isNotEmpty()
    val localStatsExportLauncher = rememberSaveFileLauncher { uri -> viewModel.saveLocalStatsCSV(uri, localStatsData) }
    val signalExportLauncher = rememberSaveFileLauncher { uri -> viewModel.saveSignalMetricsCSV(uri, signalData) }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = null,
        titleRes = Res.string.signal_quality,
        nodeName = state.node?.user?.long_name ?: "",
        data = data,
        timeProvider = { it.timeSeconds.toDouble() },
        modifier = modifier,
        onExportCsv =
        if (signalData.isNotEmpty()) {
            { signalExportLauncher("signal_metrics.csv", "text/csv") }
        } else {
            null
        },
        infoData =
        buildList {
            if (hasNoiseFloor) {
                add(
                    InfoDialogData(
                        Res.string.noise_floor,
                        Res.string.noise_floor_definition,
                        SignalMetric.NOISE_FLOOR.color,
                    ),
                )
            }
            if (hasSnr) add(InfoDialogData(Res.string.snr, Res.string.snr_definition, SignalMetric.SNR.color))
            if (hasRssi) add(InfoDialogData(Res.string.rssi, Res.string.rssi_definition, SignalMetric.RSSI.color))
        },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        chartPart = { contentModifier, selectedX, vicoScrollState, onPointSelected ->
            SignalMetricsChart(
                modifier = contentModifier,
                localStats = localStatsData.reversed(),
                meshPackets = signalData.reversed(),
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { contentModifier, selectedX, lazyListState, onCardClick ->
            if (data.isEmpty()) {
                Box(modifier = contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.no_local_stats),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = contentModifier.fillMaxSize(), state = lazyListState) {
                    itemsIndexed(
                        data,
                        key = { _, entry -> entry.key },
                        contentType = { _, entry -> entry.contentType },
                    ) { _, entry ->
                        when (entry) {
                            is SignalLogEntry.LocalStatsEntry ->
                                LocalStatsCard(
                                    telemetry = entry.telemetry,
                                    isSelected = entry.timeSeconds.toDouble() == selectedX,
                                    onClick = { onCardClick(entry.timeSeconds.toDouble()) },
                                )

                            is SignalLogEntry.PacketEntry ->
                                SignalMetricsCard(
                                    meshPacket = entry.meshPacket,
                                    isSelected = entry.timeSeconds.toDouble() == selectedX,
                                    onClick = { onCardClick(entry.timeSeconds.toDouble()) },
                                )
                        }
                    }
                }
            }
        },
        bottomContent = {
            LocalStatsActionButtons(
                hasLocalStats = hasAnyLocalStats,
                hasVisibleLocalStats = localStatsData.isNotEmpty(),
                onClear = viewModel::clearLocalStats,
                onRequest = { viewModel.requestTelemetry(TelemetryType.LOCAL_STATS) },
                onSave = { localStatsExportLauncher("local_stats.csv", "text/csv") },
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalStatsActionButtons(
    hasLocalStats: Boolean,
    hasVisibleLocalStats: Boolean,
    onClear: () -> Unit,
    onRequest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasLocalStats) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onClear,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.clear))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(Res.string.clear), maxLines = 1)
            }
        }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onRequest) {
            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = stringResource(Res.string.request))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(Res.string.request), maxLines = 1)
        }
        if (hasVisibleLocalStats) {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onSave) {
                Icon(imageVector = MeshtasticIcons.Save, contentDescription = stringResource(Res.string.save))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(Res.string.save), maxLines = 1)
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun SignalMetricsChart(
    modifier: Modifier = Modifier,
    localStats: List<Telemetry>,
    meshPackets: List<MeshPacket>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    val noiseFloorData = remember(localStats) { localStats.filter { it.local_stats?.noise_floor != 0 } }
    val busyFloorData =
        remember(noiseFloorData) {
            if (noiseFloorData.size > 1) listOf(noiseFloorData.first(), noiseFloorData.last()) else emptyList()
        }
    val rssiData = remember(meshPackets) { meshPackets.filter { it.rx_rssi != 0 } }
    val snrData = remember(meshPackets) { meshPackets.filter { !it.rx_snr.isNaN() } }
    val legendData =
        remember(noiseFloorData, rssiData, snrData) {
            LEGEND_DATA.filter { legend ->
                when (legend.nameRes) {
                    Res.string.noise_floor -> noiseFloorData.isNotEmpty()
                    Res.string.rssi -> rssiData.isNotEmpty()
                    Res.string.snr -> snrData.isNotEmpty()
                    else -> true
                }
            }
        }

    MetricChartScaffold(
        isEmpty = meshPackets.isEmpty() && localStats.isEmpty(),
        legendData = legendData,
        modifier = modifier,
    ) { modelProducer, chartModifier ->
        val noiseFloorColor = SignalMetric.NOISE_FLOOR.color
        val busyFloorColor = SignalMetric.BUSY_FLOOR.color
        val rssiColor = SignalMetric.RSSI.color
        val snrColor = SignalMetric.SNR.color
        val noiseFloorLabel = stringResource(Res.string.noise_floor)
        val busyFloorLabel = stringResource(Res.string.busy_noise_floor)
        val rssiLabel = stringResource(Res.string.rssi)
        val snrLabel = stringResource(Res.string.snr)

        LaunchedEffect(noiseFloorData, busyFloorData, rssiData, snrData) {
            modelProducer.runTransaction {
                if (noiseFloorData.isNotEmpty()) {
                    lineModel {
                        series(
                            x = noiseFloorData.map { it.time },
                            y = noiseFloorData.map { it.local_stats?.noise_floor ?: 0 },
                        )
                    }
                }
                if (busyFloorData.isNotEmpty()) {
                    lineModel { series(x = busyFloorData.map { it.time }, y = busyFloorData.map { BUSY_FLOOR_DBM }) }
                }
                if (rssiData.isNotEmpty()) {
                    lineModel { series(x = rssiData.map { it.rx_time }, y = rssiData.map { it.rx_rssi }) }
                }
                if (snrData.isNotEmpty()) {
                    /* Use a separate lineModel call to associate SNR with the right axis. */
                    lineModel { series(x = snrData.map { it.rx_time }, y = snrData.map { it.rx_snr }) }
                }
            }
        }

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color.copy(alpha = 1f)) {
                        noiseFloorColor -> "$noiseFloorLabel: ${MetricFormatter.rssi(value.toInt())}"
                        busyFloorColor -> "$busyFloorLabel: ${MetricFormatter.rssi(value.toInt())}"
                        rssiColor -> "$rssiLabel: ${MetricFormatter.rssi(value.toInt())}"
                        snrColor -> "$snrLabel: ${MetricFormatter.snr(value.toFloat())}"
                        else -> value.toString()
                    }
                },
            )

        val dbmRangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = MIN_DBM_AXIS, maxY = MAX_DBM_AXIS) }
        val noiseFloorLayer =
            rememberConditionalLayer(
                hasData = noiseFloorData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createBoldLine(noiseFloorColor)),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = dbmRangeProvider,
            )
        val busyFloorLayer =
            rememberConditionalLayer(
                hasData = busyFloorData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createDashedLine(busyFloorColor)),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = dbmRangeProvider,
            )
        val rssiLayer =
            rememberConditionalLayer(
                hasData = rssiData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createDashedLine(rssiColor)),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = dbmRangeProvider,
            )
        val snrLayer =
            rememberConditionalLayer(
                hasData = snrData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createDashedLine(snrColor)),
                verticalAxisPosition = Axis.Position.Vertical.End,
            )

        val layers =
            remember(noiseFloorLayer, busyFloorLayer, rssiLayer, snrLayer) {
                listOfNotNull(noiseFloorLayer, busyFloorLayer, rssiLayer, snrLayer)
            }

        if (layers.isNotEmpty()) {
            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = layers,
                startAxis =
                if (noiseFloorData.isNotEmpty() || rssiData.isNotEmpty()) {
                    VerticalAxis.rememberStart(
                        label =
                        ChartStyling.rememberAxisLabel(
                            color = if (noiseFloorData.isNotEmpty()) noiseFloorColor else rssiColor,
                        ),
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
private fun noiseFloorTextColor(value: Int): Color = when {
    value == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    value < QUIET_NOISE_FLOOR_DBM -> SignalMetric.SNR.color
    value < BUSY_FLOOR_DBM -> Orange
    else -> MaterialTheme.colorScheme.error
}

@Suppress("LongMethod")
@Composable
private fun LocalStatsCard(telemetry: Telemetry, isSelected: Boolean, onClick: () -> Unit) {
    val localStats = telemetry.local_stats
    val time = telemetry.time.toLong() * MS_PER_SEC
    val noiseFloor = localStats?.noise_floor ?: 0

    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = DateFormatter.formatDateTime(time),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text =
                    if (noiseFloor != 0) {
                        stringResource(Res.string.local_stats_noise, noiseFloor)
                    } else {
                        stringResource(Res.string.noise_floor_no_reading)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = noiseFloorTextColor(noiseFloor),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FlowRow(SpaceBetween): the relays stat wraps to its own line when the traffic label is too long,
            // instead of being crushed and wrapped one character per line.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                    stringResource(
                        Res.string.local_stats_traffic,
                        localStats?.num_packets_tx ?: 0,
                        localStats?.num_packets_rx ?: 0,
                        localStats?.num_rx_dupe ?: 0,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text =
                    stringResource(
                        Res.string.local_stats_relays,
                        localStats?.num_tx_relay ?: 0,
                        localStats?.num_tx_relay_canceled ?: 0,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // FlowRow(SpaceBetween): the uptime stat wraps to its own line when the nodes label is too long,
            // instead of being crushed and wrapped one character per line.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                    stringResource(
                        Res.string.local_stats_nodes,
                        localStats?.num_online_nodes ?: 0,
                        localStats?.num_total_nodes ?: 0,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(Res.string.local_stats_uptime, formatUptime(localStats?.uptime_seconds ?: 0)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if ((localStats?.num_packets_rx_bad ?: 0) > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.local_stats_bad, localStats?.num_packets_rx_bad ?: 0),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
                LoraSignalIndicator(snr = meshPacket.rx_snr)
            }
        }
    }
}
