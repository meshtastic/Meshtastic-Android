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
import org.meshtastic.core.model.util.AirQualityIndex
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_quality_metrics_log
import org.meshtastic.core.resources.aqi
import org.meshtastic.core.resources.aqi_value_with_severity
import org.meshtastic.core.resources.co2
import org.meshtastic.core.resources.co2_humidity
import org.meshtastic.core.resources.co2_temperature
import org.meshtastic.core.resources.micrograms_per_cubic_meter
import org.meshtastic.core.resources.pm10
import org.meshtastic.core.resources.pm1_0
import org.meshtastic.core.resources.pm2_5
import org.meshtastic.core.resources.ppm
import org.meshtastic.core.ui.component.Co2Severity
import org.meshtastic.core.ui.component.PmAqiSeverity
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.GraphColors.Blue
import org.meshtastic.core.ui.theme.GraphColors.Cyan
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.Purple
import org.meshtastic.core.ui.theme.GraphColors.Red
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.AirQualityMetrics as AirQualityMetricsProto

/**
 * Selectable chart metric enum for air quality data series. Internal (not private) so [getValue] can be unit-tested.
 *
 * [AQI] is the odd one out: the firmware's `AirQualityMetrics` telemetry carries no AQI field, so it is derived from
 * the PM2.5 history by [AirQualityIndex] and supplied per sample via [AirQualitySample.aqi] rather than read off a
 * single [Telemetry]. It is dimensionless, hence the empty [unit].
 */
internal enum class AirQuality(val labelRes: StringResource, val unit: String, val color: Color) {
    PM1_0(Res.string.pm1_0, "µg/m³", Blue),
    PM2_5(Res.string.pm2_5, "µg/m³", Cyan),
    PM10(Res.string.pm10, "µg/m³", Green),
    CO2(Res.string.co2, "ppm", Red),
    AQI(Res.string.aqi, "", Purple),
    ;

    /**
     * The plotted value for this series, or null when the sample has no reading for it. [aqi] is the precomputed
     * NowCast AQI for this sample (see [withNowCastAqi]) and is only consulted by the [AQI] series.
     */
    fun getValue(telemetry: Telemetry, aqi: Int? = null): Float? {
        // A field that is present-and-zero is a real reading (e.g. a PM sensor in clean air reports 0 µg/m³) and must
        // be plotted. The `?.` already excludes genuinely-absent fields (Wire decodes an unset optional uint32 to
        // null), so no zero-suppression guard is needed — adding one would discard valid clean-air data.
        val aq = telemetry.air_quality_metrics
        return when (this) {
            PM1_0 -> aq?.pm10_standard?.toFloat()
            PM2_5 -> aq?.pm25_standard?.toFloat()
            PM10 -> aq?.pm100_standard?.toFloat()
            CO2 -> aq?.co2?.toFloat()
            AQI -> aqi?.toFloat() // derived, so it arrives alongside the sample rather than inside the telemetry.
        }
    }

    fun getValue(sample: AirQualitySample): Float? = getValue(sample.telemetry, sample.aqi)
}

/** An air quality [telemetry] reading paired with the NowCast [aqi] as of its own timestamp (null if unavailable). */
internal data class AirQualitySample(val telemetry: Telemetry, val aqi: Int?)

/**
 * Pairs every reading in [telemetries] with its historical NowCast AQI, returned sorted **ascending by time** (the
 * order the chart plots in; the list view uses `asReversed()`).
 *
 * Each point's AQI is scoped to its own timestamp, so the graph shows how AQI actually moved rather than smearing the
 * latest value backwards. Samples whose PM2.5 field is absent contribute no reading and get a null AQI, as do samples
 * with too little preceding history for EPA's minimum-data rule (issue #6381).
 */
internal fun withNowCastAqi(telemetries: List<Telemetry>): List<AirQualitySample> {
    val ascending = telemetries.sortedBy { it.time }
    // Keep each PM2.5 reading tied to its position in `ascending` so the returned AQI series can be mapped back onto
    // the full sample list, gaps included.
    val pm25Readings =
        ascending.mapIndexedNotNull { index, telemetry ->
            telemetry.air_quality_metrics?.pm25_standard?.let { pm25 ->
                index to (telemetry.time.toLong() to pm25.toDouble())
            }
        }
    val aqiSeries = AirQualityIndex.nowCastAqiSeries(pm25Readings.map { it.second })
    val aqiByIndex = pm25Readings.mapIndexed { position, (index, _) -> index to aqiSeries[position] }.toMap()
    return ascending.mapIndexed { index, telemetry -> AirQualitySample(telemetry, aqiByIndex[index]) }
}

/**
 * The subset of [candidates] with at least one reading in [samples]. The chart only draws series that have data, so the
 * legend must use this rather than the raw selection — otherwise a default-selected series (PM2.5) shows a legend entry
 * on nodes that never report it (issue #5873). Internal so it can be unit-tested.
 */
internal fun metricsWithData(candidates: List<AirQuality>, samples: List<AirQualitySample>): List<AirQuality> =
    candidates.filter { metric -> samples.any { metric.getValue(it) != null } }

private val LEGEND_DATA =
    AirQuality.entries.map { metric -> LegendData(nameRes = metric.labelRes, color = metric.color, isLine = true) }

@Suppress("LongMethod")
@Composable
fun AirQualityMetricsScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val data = state.airQualityMetrics.filter { it.time.toLong() >= timeFrame.timeThreshold() }

    // AQI has to be derived from the PM2.5 history (the telemetry proto carries no AQI field), so compute it once here
    // and hand the same samples to the chart, the list and the CSV export.
    val samples = remember(data) { withNowCastAqi(data) }
    val newestFirstSamples = remember(samples) { samples.asReversed() }

    // Export the chronological samples, not the newest-first list the LazyColumn renders: every sibling metrics screen
    // writes CSV rows oldest-first.
    val exportLauncher = rememberSaveFileLauncher { uri -> viewModel.saveAirQualityMetricsCSV(uri, samples) }

    val availableMetrics = remember(samples) { metricsWithData(AirQuality.entries, samples) }
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
                samples = samples,
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
                    newestFirstSamples,
                    key = { index, sample -> "${sample.telemetry.time}_$index" },
                    contentType = { _, _ -> "air_quality_metrics" },
                ) { _, sample ->
                    AirQualityMetricsCard(
                        sample = sample,
                        isFahrenheit = state.isFahrenheit,
                        isSelected = sample.telemetry.time.toDouble() == selectedX,
                        onClick = { onCardClick(sample.telemetry.time.toDouble()) },
                    )
                }
            }
        },
    )
}

@Suppress("LongMethod")
@Composable
private fun AirQualityChart(
    samples: List<AirQualitySample>,
    selectedMetrics: Set<AirQuality>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onSelectPoint: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeMetrics = AirQuality.entries.filter { it in selectedMetrics }
    val drawnMetrics = metricsWithData(activeMetrics, samples)
    val metricLabels = activeMetrics.associateWith { stringResource(it.labelRes) }
    MetricChartScaffold(
        isEmpty = samples.isEmpty() || activeMetrics.isEmpty(),
        legendData = LEGEND_DATA.filter { ld -> drawnMetrics.any { it.labelRes == ld.nameRes } },
        modifier = modifier,
    ) { modelProducer, chartModifier ->
        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    val metric = activeMetrics.firstOrNull { it.color == color }
                    if (metric != null) {
                        val label = metricLabels[metric] ?: ""
                        // AQI is dimensionless, so trim away the separator that would leave a trailing space.
                        "$label: ${NumberFormatter.format(value.toFloat(), 0)} ${metric.unit}".trimEnd()
                    } else {
                        NumberFormatter.format(value.toFloat(), 0)
                    }
                },
            )

        val metricDataSets =
            remember(samples, activeMetrics) {
                activeMetrics.map { metric -> samples.filter { metric.getValue(it) != null } }
            }

        LaunchedEffect(samples, activeMetrics) {
            modelProducer.runTransaction {
                activeMetrics.forEachIndexed { index, metric ->
                    val metricData = metricDataSets[index]
                    if (metricData.isNotEmpty()) {
                        lineModel {
                            series(
                                x = metricData.map { it.telemetry.time },
                                y = metricData.map { metric.getValue(it) ?: 0f },
                            )
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

/**
 * The NowCast AQI for one log row, derived from that row's preceding PM2.5 history (issue #6381) rather than read from
 * the telemetry — the proto carries no AQI field. Rows without enough recent history for EPA's minimum-data rule simply
 * omit this. The category name always accompanies the value, so the severity color is reinforcement, never the only
 * signal.
 */
@Composable
private fun AqiText(aqi: Int) {
    val severity = PmAqiSeverity.fromAqi(aqi)
    val value =
        severity?.let { stringResource(Res.string.aqi_value_with_severity, aqi, stringResource(it.labelRes)) }
            ?: aqi.toString()
    Text(
        text = "${stringResource(Res.string.aqi)}: $value",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = severity?.color() ?: MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AirQualityMetricsCard(
    sample: AirQualitySample,
    isSelected: Boolean,
    onClick: () -> Unit,
    isFahrenheit: Boolean = false,
    timeTextOverride: String? = null,
) {
    val telemetry = sample.telemetry
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
            val ugm3 = stringResource(Res.string.micrograms_per_cubic_meter)
            val ppmUnit = stringResource(Res.string.ppm)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    // Present-and-zero is a valid clean-air reading; only `?.` (absent field) hides a row.
                    listOfNotNull(
                        aq.pm10_standard?.let { Res.string.pm1_0 to it },
                        aq.pm25_standard?.let { Res.string.pm2_5 to it },
                        aq.pm100_standard?.let { Res.string.pm10 to it },
                    )
                        .forEach { (label, value) ->
                            Text("${stringResource(label)}: $value $ugm3", style = MaterialTheme.typography.bodySmall)
                        }
                    sample.aqi?.let { AqiText(it) }
                }
                Column {
                    aq.co2?.let { co2 ->
                        val severity = Co2Severity.fromPpm(co2)
                        Text(
                            text = "${stringResource(Res.string.co2)}: $co2 $ppmUnit",
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
    // Newest first, matching the list view; AQI is derived rather than read from the proto, so the first row has too
    // little history to show one.
    val samples = withNowCastAqi(readings.map { it.first }).asReversed()
    val timeTexts = readings.map { it.second }.asReversed()
    AppTheme {
        Surface {
            Column {
                samples.forEachIndexed { index, sample ->
                    AirQualityMetricsCard(
                        sample = sample,
                        isSelected = false,
                        onClick = {},
                        timeTextOverride = timeTexts[index],
                    )
                }
            }
        }
    }
}
