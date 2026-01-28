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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
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
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.baro_pressure
import org.meshtastic.core.strings.humidity
import org.meshtastic.core.strings.iaq
import org.meshtastic.core.strings.lux
import org.meshtastic.core.strings.soil_moisture
import org.meshtastic.core.strings.soil_temperature
import org.meshtastic.core.strings.temperature
import org.meshtastic.core.strings.uv_lux
import org.meshtastic.proto.TelemetryProtos.Telemetry

@Suppress("MagicNumber")
private val LEGEND_DATA_1 =
    listOf(
        LegendData(
            nameRes = Res.string.temperature,
            color = Environment.TEMPERATURE.color,
            isLine = true,
            environmentMetric = Environment.TEMPERATURE,
        ),
        LegendData(
            nameRes = Res.string.humidity,
            color = Environment.HUMIDITY.color,
            isLine = true,
            environmentMetric = Environment.HUMIDITY,
        ),
    )
private val LEGEND_DATA_2 =
    listOf(
        LegendData(
            nameRes = Res.string.iaq,
            color = Environment.IAQ.color,
            isLine = true,
            environmentMetric = Environment.IAQ,
        ),
        LegendData(
            nameRes = Res.string.baro_pressure,
            color = Environment.BAROMETRIC_PRESSURE.color,
            isLine = true,
            environmentMetric = Environment.BAROMETRIC_PRESSURE,
        ),
        LegendData(
            nameRes = Res.string.lux,
            color = Environment.LUX.color,
            isLine = true,
            environmentMetric = Environment.LUX,
        ),
        LegendData(
            nameRes = Res.string.uv_lux,
            color = Environment.UV_LUX.color,
            isLine = true,
            environmentMetric = Environment.UV_LUX,
        ),
    )

private val LEGEND_DATA_3 =
    listOf(
        LegendData(
            nameRes = Res.string.soil_temperature,
            color = Environment.SOIL_TEMPERATURE.color,
            isLine = true,
            environmentMetric = Environment.SOIL_TEMPERATURE,
        ),
        LegendData(
            nameRes = Res.string.soil_moisture,
            color = Environment.SOIL_MOISTURE.color,
            isLine = true,
            environmentMetric = Environment.SOIL_MOISTURE,
        ),
    )

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun EnvironmentMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    graphData: EnvironmentGraphingData,
    promptInfoDialog: () -> Unit,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val shouldPlot = graphData.shouldPlot

    LaunchedEffect(telemetries, graphData) {
        modelProducer.runTransaction {
            /* Pressure on its own layer/axis */
            if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) {
                lineSeries {
                    series(
                        x = telemetries.mapNotNull { t -> Environment.BAROMETRIC_PRESSURE.getValue(t)?.let { t.time } },
                        y = telemetries.mapNotNull { t -> Environment.BAROMETRIC_PRESSURE.getValue(t) },
                    )
                }
            }
            /* Everything else on the default axis */
            Environment.entries.forEach { metric ->
                if (metric != Environment.BAROMETRIC_PRESSURE && shouldPlot[metric.ordinal]) {
                    lineSeries {
                        series(
                            x = telemetries.mapNotNull { t -> metric.getValue(t)?.let { t.time } },
                            y = telemetries.mapNotNull { t -> metric.getValue(t) },
                        )
                    }
                }
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
    val marker =
        ChartStyling.rememberMarker(
            valueFormatter = { _, targets ->
                targets.joinToString { target ->
                    when (target) {
                        is LineCartesianLayerMarkerTarget -> {
                            target.points.joinToString { point ->
                                // We don't have unit info easily here, but we can format the raw value
                                "%.1f".format(point.entry.y)
                            }
                        }
                        else -> ""
                    }
                }
            },
        )

    val layers = mutableListOf<LineCartesianLayer>()
    if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) {
        layers.add(
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createGradientLine(
                        Environment.BAROMETRIC_PRESSURE.color,
                        ChartStyling.MEDIUM_POINT_SIZE_DP,
                    ),
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
        )
    }
    Environment.entries.forEach { metric ->
        if (metric != Environment.BAROMETRIC_PRESSURE && shouldPlot[metric.ordinal]) {
            layers.add(
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createGradientLine(metric.color, ChartStyling.MEDIUM_POINT_SIZE_DP),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.End,
                ),
            )
        }
    }

    if (layers.isNotEmpty()) {
        CartesianChartHost(
            chart =
            @Suppress("SpreadOperator")
            rememberCartesianChart(
                *layers.toTypedArray(),
                startAxis =
                if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) {
                    VerticalAxis.rememberStart(
                        label = axisLabel,
                        valueFormatter = { _, value, _ -> "%.0f hPa".format(value) },
                    )
                } else {
                    null
                },
                endAxis =
                VerticalAxis.rememberEnd(
                    label = axisLabel,
                    valueFormatter = { _, value, _ -> "%.0f".format(value) },
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
    }

    Spacer(modifier = Modifier.height(16.dp))

    MetricLegends(graphData = graphData, promptInfoDialog = promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun MetricLegends(graphData: EnvironmentGraphingData, promptInfoDialog: () -> Unit) {
    Legend(LEGEND_DATA_1.filter { graphData.shouldPlot[it.environmentMetric?.ordinal ?: 0] }, displayInfoIcon = false)
    Legend(LEGEND_DATA_3.filter { graphData.shouldPlot[it.environmentMetric?.ordinal ?: 0] }, displayInfoIcon = false)
    Legend(
        LEGEND_DATA_2.filter { graphData.shouldPlot[it.environmentMetric?.ordinal ?: 0] },
        promptInfoDialog = promptInfoDialog,
    )
}
