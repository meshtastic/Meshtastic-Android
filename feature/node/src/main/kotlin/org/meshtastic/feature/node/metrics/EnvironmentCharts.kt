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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.baro_pressure
import org.meshtastic.core.strings.humidity
import org.meshtastic.core.strings.iaq
import org.meshtastic.core.strings.lux
import org.meshtastic.core.strings.soil_moisture
import org.meshtastic.core.strings.soil_temperature
import org.meshtastic.core.strings.temperature
import org.meshtastic.core.strings.uv_lux
import org.meshtastic.proto.Telemetry

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
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    Column(modifier = modifier) {
        if (telemetries.isEmpty()) {
            return@Column
        }

        val modelProducer = remember { CartesianChartModelProducer() }
        val shouldPlot = graphData.shouldPlot
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        val allLegendData =
            (LEGEND_DATA_1 + LEGEND_DATA_2 + LEGEND_DATA_3).filter {
                graphData.shouldPlot[it.environmentMetric?.ordinal ?: 0]
            }
        val colorToLabel = allLegendData.associate { it.color to stringResource(it.nameRes) }

        LaunchedEffect(telemetries, graphData) {
            modelProducer.runTransaction {
                /* Pressure on its own layer/axis */
                if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) {
                    lineSeries {
                        val pressureData =
                            telemetries.filter {
                                val v = Environment.BAROMETRIC_PRESSURE.getValue(it)
                                it.time != 0 && v != null && !v.isNaN()
                            }
                        series(
                            x = pressureData.map { it.time },
                            y = pressureData.map { Environment.BAROMETRIC_PRESSURE.getValue(it)!! },
                        )
                    }
                }
                /* Everything else on the default axis */
                Environment.entries.forEach { metric ->
                    if (metric != Environment.BAROMETRIC_PRESSURE && shouldPlot[metric.ordinal]) {
                        lineSeries {
                            val metricData =
                                telemetries.filter {
                                    val v = metric.getValue(it)
                                    it.time != 0 && v != null && !v.isNaN()
                                }
                            series(x = metricData.map { it.time }, y = metricData.map { metric.getValue(it)!! })
                        }
                    }
                }
            }
        }

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    val label = colorToLabel[color.copy(alpha = 1f)] ?: ""
                    "%s: %.1f".format(label, value)
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
            val otherMetricsPlotted =
                Environment.entries.filter { it != Environment.BAROMETRIC_PRESSURE && shouldPlot[it.ordinal] }
            val endAxisColor = if (otherMetricsPlotted.size == 1) otherMetricsPlotted.first().color else onSurfaceColor

            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).padding(bottom = 0.dp),
                layers = layers,
                startAxis =
                if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) {
                    VerticalAxis.rememberStart(
                        label = ChartStyling.rememberAxisLabel(color = Environment.BAROMETRIC_PRESSURE.color),
                        valueFormatter = { _, value, _ -> "%.0f hPa".format(value) },
                    )
                } else {
                    null
                },
                endAxis =
                VerticalAxis.rememberEnd(
                    label = ChartStyling.rememberAxisLabel(color = endAxisColor),
                    valueFormatter = { _, value, _ -> "%.0f".format(value) },
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
        }

        Legend(legendData = allLegendData, modifier = Modifier.padding(top = 0.dp))
    }
}
