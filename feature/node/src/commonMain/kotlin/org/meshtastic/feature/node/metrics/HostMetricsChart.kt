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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.free_memory
import org.meshtastic.core.resources.free_memory_description
import org.meshtastic.core.resources.load_15_min
import org.meshtastic.core.resources.load_15_min_description
import org.meshtastic.core.resources.load_1_min
import org.meshtastic.core.resources.load_1_min_description
import org.meshtastic.core.resources.load_5_min
import org.meshtastic.core.resources.load_5_min_description
import org.meshtastic.core.ui.theme.GraphColors
import org.meshtastic.proto.Telemetry

/** Chart series colours for the four host metrics. */
private enum class HostMetric(val color: Color) {
    LOAD_1(GraphColors.Blue),
    LOAD_5(GraphColors.Green),
    LOAD_15(GraphColors.Orange),
    FREE_MEM(GraphColors.Teal),
}

/** Legend entries for the host metrics chart. */
internal val HOST_METRICS_LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.load_1_min, color = HostMetric.LOAD_1.color, isLine = true),
        LegendData(nameRes = Res.string.load_5_min, color = HostMetric.LOAD_5.color, isLine = true),
        LegendData(nameRes = Res.string.load_15_min, color = HostMetric.LOAD_15.color, isLine = true),
        LegendData(nameRes = Res.string.free_memory, color = HostMetric.FREE_MEM.color, isLine = true),
    )

/** Info-dialog entries describing each host metric for the legend help overlay. */
internal val HOST_METRICS_INFO_DATA =
    listOf(
        InfoDialogData(
            titleRes = Res.string.load_1_min,
            definitionRes = Res.string.load_1_min_description,
            color = HostMetric.LOAD_1.color,
        ),
        InfoDialogData(
            titleRes = Res.string.load_5_min,
            definitionRes = Res.string.load_5_min_description,
            color = HostMetric.LOAD_5.color,
        ),
        InfoDialogData(
            titleRes = Res.string.load_15_min,
            definitionRes = Res.string.load_15_min_description,
            color = HostMetric.LOAD_15.color,
        ),
        InfoDialogData(
            titleRes = Res.string.free_memory,
            definitionRes = Res.string.free_memory_description,
            color = HostMetric.FREE_MEM.color,
        ),
    )

internal data class HostMetricsChartPoint(val time: Int, val value: Double)

internal data class HostMetricsChartData(
    val load1: List<HostMetricsChartPoint> = emptyList(),
    val load5: List<HostMetricsChartPoint> = emptyList(),
    val load15: List<HostMetricsChartPoint> = emptyList(),
    val freeMemoryMb: List<HostMetricsChartPoint> = emptyList(),
) {
    val hasLoad: Boolean
        get() = load1.isNotEmpty() || load5.isNotEmpty() || load15.isNotEmpty()
}

internal fun buildHostMetricsChartData(data: List<Telemetry>): HostMetricsChartData = HostMetricsChartData(
    load1 =
    data.mapNotNull { telemetry ->
        telemetry.host_metrics
            ?.load1
            ?.takeIf { it > 0 }
            ?.let { HostMetricsChartPoint(time = telemetry.time, value = it / 100.0) }
    },
    load5 =
    data.mapNotNull { telemetry ->
        telemetry.host_metrics
            ?.load5
            ?.takeIf { it > 0 }
            ?.let { HostMetricsChartPoint(time = telemetry.time, value = it / 100.0) }
    },
    load15 =
    data.mapNotNull { telemetry ->
        telemetry.host_metrics
            ?.load15
            ?.takeIf { it > 0 }
            ?.let { HostMetricsChartPoint(time = telemetry.time, value = it / 100.0) }
    },
    freeMemoryMb =
    data.mapNotNull { telemetry ->
        telemetry.host_metrics
            ?.freemem_bytes
            ?.takeIf { it > 0 }
            ?.let { HostMetricsChartPoint(time = telemetry.time, value = it.toDouble() / BYTES_IN_MB) }
    },
)

/**
 * Vico chart composable that renders load averages (1m, 5m, 15m) and free memory as dual-axis line series: load on the
 * start axis (fixed min 0), free memory in MB on the end axis.
 *
 * Load values from the proto are in 1/100ths (e.g. 150 = 1.50 load). They are divided by 100 for display.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun HostMetricsChart(
    modifier: Modifier = Modifier,
    data: List<Telemetry>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    MetricChartScaffold(isEmpty = data.isEmpty(), legendData = HOST_METRICS_LEGEND_DATA, modifier = modifier) {
            modelProducer,
            chartModifier,
        ->
        val chartData = remember(data) { buildHostMetricsChartData(data) }
        val load1Data = chartData.load1
        val load5Data = chartData.load5
        val load15Data = chartData.load15
        val memData = chartData.freeMemoryMb

        LaunchedEffect(chartData) {
            modelProducer.runTransaction {
                if (chartData.hasLoad) {
                    lineModel {
                        if (load1Data.isNotEmpty()) {
                            series(x = load1Data.map { it.time }, y = load1Data.map { it.value })
                        }
                        if (load5Data.isNotEmpty()) {
                            series(x = load5Data.map { it.time }, y = load5Data.map { it.value })
                        }
                        if (load15Data.isNotEmpty()) {
                            series(x = load15Data.map { it.time }, y = load15Data.map { it.value })
                        }
                    }
                }
                if (memData.isNotEmpty()) {
                    lineModel { series(x = memData.map { it.time }, y = memData.map { it.value }) }
                }
            }
        }

        val load1Color = HostMetric.LOAD_1.color
        val load5Color = HostMetric.LOAD_5.color
        val load15Color = HostMetric.LOAD_15.color
        val memColor = HostMetric.FREE_MEM.color

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color) {
                        load1Color -> formatString("L1: %.2f", value)
                        load5Color -> formatString("L5: %.2f", value)
                        load15Color -> formatString("L15: %.2f", value)
                        else -> formatString("Mem: %.0f MB", value)
                    }
                },
            )

        val hasLoad = chartData.hasLoad
        val load1Style = if (load1Data.isNotEmpty()) ChartStyling.createStyledLine(load1Color) else null
        val load5Style = if (load5Data.isNotEmpty()) ChartStyling.createDashedLine(load5Color) else null
        val load15Style = if (load15Data.isNotEmpty()) ChartStyling.createSubtleLine(load15Color) else null
        val loadStyles = listOfNotNull(load1Style, load5Style, load15Style)

        val loadLayer =
            rememberConditionalLayer(
                hasData = hasLoad,
                lineProvider = LineCartesianLayer.LineProvider.series(loadStyles),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0),
            )

        val memLayer =
            rememberConditionalLayer(
                hasData = memData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createGradientLine(memColor)),
                verticalAxisPosition = Axis.Position.Vertical.End,
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0),
            )

        val layers = remember(loadLayer, memLayer) { listOfNotNull(loadLayer, memLayer) }

        if (layers.isNotEmpty()) {
            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = layers,
                startAxis =
                if (hasLoad) {
                    VerticalAxis.rememberStart(
                        label = ChartStyling.rememberAxisLabel(color = load1Color),
                        valueFormatter = { _, value, _ -> formatString("%.1f", value) },
                    )
                } else {
                    null
                },
                endAxis =
                if (memData.isNotEmpty()) {
                    VerticalAxis.rememberEnd(
                        label = ChartStyling.rememberAxisLabel(color = memColor),
                        valueFormatter = { _, value, _ -> formatString("%.0f MB", value) },
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
