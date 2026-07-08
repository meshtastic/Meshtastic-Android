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
@file:Suppress("MagicNumber", "MatchingDeclarationName") // file groups the AirQuality enum with its chart composables

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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_quality_metrics_log
import org.meshtastic.core.resources.co2
import org.meshtastic.core.resources.co2_humidity
import org.meshtastic.core.resources.co2_temperature
import org.meshtastic.core.resources.pm10
import org.meshtastic.core.resources.pm1_0
import org.meshtastic.core.resources.pm2_5
import org.meshtastic.core.ui.component.Co2Severity
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.GraphColors.Blue
import org.meshtastic.core.ui.theme.GraphColors.Cyan
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.Red
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.AirQualityMetrics as AirQualityMetricsProto

/**
 * Selectable chart metric enum for air quality data series. Internal (not private) so [getValue] can be unit-tested.
 */
internal enum class AirQuality(val labelRes: StringResource, val unit: String, val color: Color) {
    PM1_0(Res.string.pm1_0, "µg/m³", Blue),
    PM2_5(Res.string.pm2_5, "µg/m³", Cyan),
    PM10(Res.string.pm10, "µg/m³", Green),
    CO2(Res.string.co2, "ppm", Red),
    ;

    fun getValue(telemetry: Telemetry): Float? {
        val aq = telemetry.air_quality_metrics ?: return null
        // A field that is present-and-zero is a real reading (e.g. a PM sensor in clean air reports 0 µg/m³) and must
        // be plotted. The `?.` already excludes genuinely-absent fields (Wire decodes an unset optional uint32 to
        // null), so no zero-suppression guard is needed — adding one would discard valid clean-air data.
        return when (this) {
            PM1_0 -> aq.pm10_standard?.toFloat()
            PM2_5 -> aq.pm25_standard?.toFloat()
            PM10 -> aq.pm100_standard?.toFloat()
            CO2 -> aq.co2?.toFloat()
        }
    }
}

private val LEGEND_DATA =
    AirQuality.entries.map { metric -> LegendData(nameRes = metric.labelRes, color = metric.color, isLine = true) }

@Suppress("LongMethod")
@Composable
fun AirQualityMetricsScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val data = state.airQualityMetrics.filter { it.time.toLong() >= timeFrame.timeThreshold() }

    val exportLauncher = rememberSaveFileLauncher { uri -> viewModel.saveAirQualityMetricsCSV(uri, data) }

    val availableMetrics =
        remember(data) { AirQuality.entries.filter { metric -> data.any { metric.getValue(it) != null } } }
    var selectedMetrics by rememberSaveable { mutableStateOf(setOf(AirQuality.PM2_5, AirQuality.CO2)) }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.AIR_QUALITY,
        titleRes = Res.string.air_quality_metrics_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = data,
        timeProvider = { it.time.toDouble() },
        onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.AIR_QUALITY) },
        onExportCsv = { exportLauncher("air_quality_metrics.csv", "text/csv") },
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
                    availableMetrics.forEach { metric ->
                        FilterChip(
                            selected = metric in selectedMetrics,
                            onClick = {
                                selectedMetrics =
                                    if (metric in selectedMetrics) {
                                        selectedMetrics - metric
                                    } else {
                                        selectedMetrics + metric
                                    }
                            },
                            label = { Text(stringResource(metric.labelRes)) },
                        )
                    }
                }
            }
        },
        chartPart = { modifier, selectedX, vicoScrollState, onPointSelected ->
            AirQualityChart(
                telemetries = data.reversed(),
                selectedMetrics = selectedMetrics,
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onSelectPoint = onPointSelected,
                modifier = modifier,
            )
        },
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(
                    data,
                    key = { index, telemetry -> "${telemetry.time}_$index" },
                    contentType = { _, _ -> "air_quality_metrics" },
                ) { _, telemetry ->
                    AirQualityMetricsCard(
                        telemetry = telemetry,
                        isFahrenheit = state.isFahrenheit,
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
private fun AirQualityChart(
    telemetries: List<Telemetry>,
    selectedMetrics: Set<AirQuality>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onSelectPoint: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeMetrics = AirQuality.entries.filter { it in selectedMetrics }
    val metricLabels = activeMetrics.associateWith { stringResource(it.labelRes) }
    MetricChartScaffold(
        isEmpty = telemetries.isEmpty() || activeMetrics.isEmpty(),
        legendData = LEGEND_DATA.filter { ld -> activeMetrics.any { it.labelRes == ld.nameRes } },
        modifier = modifier,
    ) { modelProducer, chartModifier ->
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    val metric = activeMetrics.firstOrNull { it.color == color }
                    if (metric != null) {
                        val label = metricLabels[metric] ?: ""
                        "$label: ${NumberFormatter.format(value.toFloat(), 0)} ${metric.unit}"
                    } else {
                        NumberFormatter.format(value.toFloat(), 0)
                    }
                },
            )

        val metricDataSets =
            remember(telemetries, activeMetrics) {
                activeMetrics.map { metric -> telemetries.filter { metric.getValue(it) != null } }
            }

        LaunchedEffect(telemetries, activeMetrics) {
            modelProducer.runTransaction {
                activeMetrics.forEachIndexed { index, metric ->
                    val metricData = metricDataSets[index]
                    if (metricData.isNotEmpty()) {
                        lineModel {
                            series(x = metricData.map { it.time }, y = metricData.map { metric.getValue(it) ?: 0f })
                        }
                    }
                }
            }
        }

        val layers =
            remember(activeMetrics, metricDataSets) {
                activeMetrics.mapIndexedNotNull { index, metric ->
                    if (metricDataSets[index].isNotEmpty()) {
                        metric to metricDataSets[index]
                    } else {
                        null
                    }
                }
            }

        val chartLayers =
            layers.map { (metric, _) ->
                rememberConditionalLayer(
                    hasData = true,
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createStyledLine(metric.color, ChartStyling.THIN_LINE_WIDTH_DP),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                )
            }

        val nonNullLayers = remember(chartLayers) { chartLayers.filterNotNull() }

        if (nonNullLayers.isNotEmpty()) {
            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = nonNullLayers,
                marker = marker,
                selectedX = selectedX,
                onPointSelected = onSelectPoint,
                vicoScrollState = vicoScrollState,
            )
        }
    }
}

@Composable
private fun AirQualityMetricsCard(
    telemetry: Telemetry,
    isSelected: Boolean,
    onClick: () -> Unit,
    isFahrenheit: Boolean = false,
    timeTextOverride: String? = null,
) {
    val aq = telemetry.air_quality_metrics ?: return
    val time = timeTextOverride ?: DateFormatter.formatDateTime(telemetry.time.toLong() * MS_PER_SEC)

    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        // SelectableMetricCard's SelectionContainer imposes no layout of its own,
        // so the card content must bring its own Column (matches EnvironmentMetricsContent).
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    // Present-and-zero is a valid clean-air reading; only `?.` (absent field) hides a row.
                    aq.pm10_standard?.let { Text("PM1.0: $it µg/m³", style = MaterialTheme.typography.bodySmall) }
                    aq.pm25_standard?.let { Text("PM2.5: $it µg/m³", style = MaterialTheme.typography.bodySmall) }
                    aq.pm100_standard?.let { Text("PM10: $it µg/m³", style = MaterialTheme.typography.bodySmall) }
                }
                Column {
                    aq.co2?.let { co2 ->
                        val severity = Co2Severity.fromPpm(co2)
                        Text(
                            text = "CO₂: $co2 ppm",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = severity?.color ?: MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    // SCD4x CO₂ sensors also report temperature/humidity (#5873); present-and-zero is valid, so only
                    // `?.` (absent field) hides a row.
                    aq.co2_temperature?.let {
                        Text(
                            "${stringResource(Res.string.co2_temperature)}: ${it.toTempString(isFahrenheit)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    aq.co2_humidity?.let {
                        Text(
                            "${stringResource(Res.string.co2_humidity)}: ${NumberFormatter.format(it, 0)}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Suppress("MagicNumber", "PreviewPublic") // fake data; public so :screenshot-tests can reference it
@Composable
fun PreviewAirQualityCards() {
    val readings =
        listOf(
            Telemetry(
                time = 1700000000,
                air_quality_metrics =
                AirQualityMetricsProto(
                    pm10_standard = 4,
                    pm25_standard = 9,
                    pm100_standard = 12,
                    co2 = 620,
                    co2_temperature = 21.5f,
                    co2_humidity = 58f,
                ),
            ) to "2023-11-14 20:13",
            Telemetry(
                time = 1700003600,
                air_quality_metrics =
                AirQualityMetricsProto(pm10_standard = 6, pm25_standard = 14, pm100_standard = 19, co2 = 1450),
            ) to "2023-11-14 21:13",
            Telemetry(
                time = 1700007200,
                air_quality_metrics =
                AirQualityMetricsProto(pm10_standard = 11, pm25_standard = 25, pm100_standard = 33, co2 = 2300),
            ) to "2023-11-14 22:13",
        )
    AppTheme {
        Surface {
            Column {
                readings.forEach { (telemetry, timeText) ->
                    AirQualityMetricsCard(
                        telemetry = telemetry,
                        isSelected = false,
                        onClick = {},
                        timeTextOverride = timeText,
                    )
                }
            }
        }
    }
}
