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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.baro_pressure
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.one_wire_temperature
import org.meshtastic.core.resources.radiation
import org.meshtastic.core.resources.soil_moisture
import org.meshtastic.core.resources.soil_temperature
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.uv_lux
import org.meshtastic.core.resources.wind_speed
import org.meshtastic.proto.Telemetry

@Suppress("MagicNumber")
private val LEGEND_DATA_1 =
    listOf(
        LegendData(
            nameRes = Res.string.temperature,
            color = Environment.TEMPERATURE.color,
            isLine = true,
            metricKey = Environment.TEMPERATURE,
        ),
        LegendData(
            nameRes = Res.string.humidity,
            color = Environment.HUMIDITY.color,
            isLine = true,
            metricKey = Environment.HUMIDITY,
        ),
    )
private val LEGEND_DATA_2 =
    listOf(
        LegendData(nameRes = Res.string.iaq, color = Environment.IAQ.color, isLine = true, metricKey = Environment.IAQ),
        LegendData(
            nameRes = Res.string.baro_pressure,
            color = Environment.BAROMETRIC_PRESSURE.color,
            isLine = true,
            metricKey = Environment.BAROMETRIC_PRESSURE,
        ),
        LegendData(nameRes = Res.string.lux, color = Environment.LUX.color, isLine = true, metricKey = Environment.LUX),
        LegendData(
            nameRes = Res.string.uv_lux,
            color = Environment.UV_LUX.color,
            isLine = true,
            metricKey = Environment.UV_LUX,
        ),
        LegendData(
            nameRes = Res.string.wind_speed,
            color = Environment.WIND_SPEED.color,
            isLine = true,
            metricKey = Environment.WIND_SPEED,
        ),
        LegendData(
            nameRes = Res.string.radiation,
            color = Environment.RADIATION.color,
            isLine = true,
            metricKey = Environment.RADIATION,
        ),
    )

private val LEGEND_DATA_3 =
    listOf(
        LegendData(
            nameRes = Res.string.soil_temperature,
            color = Environment.SOIL_TEMPERATURE.color,
            isLine = true,
            metricKey = Environment.SOIL_TEMPERATURE,
        ),
        LegendData(
            nameRes = Res.string.soil_moisture,
            color = Environment.SOIL_MOISTURE.color,
            isLine = true,
            metricKey = Environment.SOIL_MOISTURE,
        ),
    )

private val LEGEND_DATA_4 =
    listOf(
        Environment.ONE_WIRE_TEMP_1,
        Environment.ONE_WIRE_TEMP_2,
        Environment.ONE_WIRE_TEMP_3,
        Environment.ONE_WIRE_TEMP_4,
        Environment.ONE_WIRE_TEMP_5,
        Environment.ONE_WIRE_TEMP_6,
        Environment.ONE_WIRE_TEMP_7,
        Environment.ONE_WIRE_TEMP_8,
    )
        .mapIndexed { index, entry ->
            LegendData(
                nameRes = Res.string.one_wire_temperature,
                labelOverride = "1-Wire Temp ${index + 1}",
                color = entry.color,
                isLine = true,
                metricKey = entry,
            )
        }

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
            (LEGEND_DATA_1 + LEGEND_DATA_2 + LEGEND_DATA_3 + LEGEND_DATA_4).filter {
                graphData.shouldPlot[(it.metricKey as? Environment)?.ordinal ?: 0]
            }

        // Track hidden metrics by key (not index) so toggling survives changes in allLegendData ordering.
        var hiddenMetrics by remember { mutableStateOf(emptySet<Environment>()) }
        val hiddenIndices =
            remember(hiddenMetrics, allLegendData) {
                allLegendData.indices.filter { (allLegendData[it].metricKey as? Environment) in hiddenMetrics }.toSet()
            }

        val colorToLabel = allLegendData.associate { it.color to (it.labelOverride ?: stringResource(it.nameRes)) }

        val showPressure =
            shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal] && Environment.BAROMETRIC_PRESSURE !in hiddenMetrics
        val pressureData =
            remember(telemetries, showPressure) {
                if (!showPressure) return@remember emptyList()
                telemetries.filter {
                    val v = Environment.BAROMETRIC_PRESSURE.getValue(it)
                    it.time != 0 && v != null && !v.isNaN()
                }
            }

        val otherMetrics =
            remember(telemetries, shouldPlot, hiddenMetrics) {
                Environment.entries.filter { metric ->
                    metric != Environment.BAROMETRIC_PRESSURE &&
                        metric !in hiddenMetrics &&
                        shouldPlot[metric.ordinal] &&
                        telemetries.any {
                            val v = metric.getValue(it)
                            it.time != 0 && v != null && !v.isNaN()
                        }
                }
            }

        val otherMetricsData =
            remember(telemetries, otherMetrics) {
                otherMetrics.associateWith { metric ->
                    telemetries.filter {
                        val v = metric.getValue(it)
                        it.time != 0 && v != null && !v.isNaN()
                    }
                }
            }

        LaunchedEffect(pressureData, otherMetricsData) {
            modelProducer.runTransaction {
                /* Pressure on its own layer/axis */
                if (showPressure && pressureData.isNotEmpty()) {
                    lineSeries {
                        series(
                            x = pressureData.map { it.time },
                            y = pressureData.map { Environment.BAROMETRIC_PRESSURE.getValue(it)!! },
                        )
                    }
                }
                /* Everything else on the default axis */
                otherMetrics.forEach { metric ->
                    val metricData = otherMetricsData[metric] ?: emptyList()
                    if (metricData.isNotEmpty()) {
                        lineSeries {
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
                    val label = colorToLabel[color] ?: ""
                    formatString("%s: %.1f", label, value)
                },
            )

        val pressureRangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 700.0, maxY = 1200.0) }
        val layers = mutableListOf<LineCartesianLayer>()
        if (showPressure && pressureData.isNotEmpty()) {
            layers.add(
                rememberLineCartesianLayer(
                    lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        ChartStyling.createGradientLine(Environment.BAROMETRIC_PRESSURE.color),
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                    // Fixed range per Oscar's UX guidance: barometric pressure should NOT autoscale,
                    // otherwise trends (storms) are invisible. 700-1200 hPa covers sea-level to altitude.
                    rangeProvider = pressureRangeProvider,
                ),
            )
        }
        otherMetrics.forEach { metric ->
            // Radiation and wind speed use fixed minY=0 per Oscar's UX guidance
            val rangeProvider =
                when (metric) {
                    Environment.RADIATION,
                    Environment.WIND_SPEED,
                    -> CartesianLayerRangeProvider.auto()

                    else -> null
                }
            val lineStyle =
                if (metric == Environment.WIND_SPEED) {
                    ChartStyling.createDashedLine(metric.color)
                } else {
                    ChartStyling.createStyledLine(metric.color)
                }
            layers.add(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(lineStyle),
                    verticalAxisPosition = Axis.Position.Vertical.End,
                    rangeProvider = rangeProvider ?: CartesianLayerRangeProvider.auto(),
                ),
            )
        }

        if (layers.isNotEmpty()) {
            val endAxisColor = if (otherMetrics.size == 1) otherMetrics.first().color else onSurfaceColor

            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).padding(bottom = 0.dp),
                layers = layers,
                startAxis =
                if (showPressure && pressureData.isNotEmpty()) {
                    VerticalAxis.rememberStart(
                        label = ChartStyling.rememberAxisLabel(color = Environment.BAROMETRIC_PRESSURE.color),
                        valueFormatter = { _, value, _ -> formatString("%.0f hPa", value) },
                    )
                } else {
                    null
                },
                endAxis =
                if (otherMetrics.isNotEmpty()) {
                    VerticalAxis.rememberEnd(
                        label = ChartStyling.rememberAxisLabel(color = endAxisColor),
                        valueFormatter = { _, value, _ -> formatString("%.0f", value) },
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

        Legend(
            legendData = allLegendData,
            modifier = Modifier.padding(top = 0.dp),
            hiddenSet = hiddenIndices,
            onToggle = { index ->
                val metric = allLegendData.getOrNull(index)?.metricKey as? Environment ?: return@Legend
                hiddenMetrics = if (metric in hiddenMetrics) hiddenMetrics - metric else hiddenMetrics + metric
            },
        )
    }
}
