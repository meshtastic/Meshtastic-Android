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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.baro_pressure
import org.meshtastic.core.strings.humidity
import org.meshtastic.core.strings.iaq
import org.meshtastic.core.strings.lux
import org.meshtastic.core.strings.soil_moisture
import org.meshtastic.core.strings.soil_temperature
import org.meshtastic.core.strings.temperature
import org.meshtastic.core.strings.uv_lux
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.TelemetryProtos.Telemetry
import java.util.Date

private const val CHART_WEIGHT = 1f
private const val Y_AXIS_WEIGHT = 0.1f

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

@Composable
fun EnvironmentMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    graphData: EnvironmentGraphingData,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit,
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val shouldPlot = graphData.shouldPlot
    val (pressureMin, pressureMax) = graphData.leftMinMax
    val pressureDiff = if ((pressureMax - pressureMin) == 0f) 1f else pressureMax - pressureMin
    val (rightMin, rightMax) = graphData.rightMinMax
    val rightDiff = if ((rightMax - rightMin) == 0f) 1f else rightMax - rightMin

    LaunchedEffect(telemetries, graphData) {
        modelProducer.runTransaction {
            lineSeries {
                Environment.entries.forEach { metric ->
                    if (shouldPlot[metric.ordinal]) {
                        val isPressure = metric == Environment.BAROMETRIC_PRESSURE
                        val min = if (isPressure) pressureMin else rightMin
                        val diff = if (isPressure) pressureDiff else rightDiff

                        val xValues = mutableListOf<Number>()
                        val yValues = mutableListOf<Number>()

                        telemetries.forEach { telemetry ->
                            val rawValue = metric.getValue(telemetry)
                            if (rawValue != null && !rawValue.isNaN()) {
                                xValues.add(telemetry.time)
                                yValues.add((rawValue - min) / diff)
                            }
                        }

                        if (xValues.isNotEmpty()) {
                            series(x = xValues, y = yValues)
                        } else {
                            // Ensure series count matches lines count by adding empty series if needed
                            series(x = emptyList(), y = emptyList())
                        }
                    }
                }
            }
        }
    }

    val lines =
        Environment.entries
            .filter { shouldPlot[it.ordinal] }
            .map { metric ->
                LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(Fill(metric.color)))
            }

    if (lines.isNotEmpty()) {
        CartesianChartHost(
            chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(lineProvider = LineCartesianLayer.LineProvider.series(lines)),
                startAxis =
                if (shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal]) {
                    VerticalAxis.rememberStart(
                        valueFormatter = { _, value, _ ->
                            val actualValue = value * pressureDiff.toDouble() + pressureMin
                            "%.0f".format(actualValue)
                        },
                    )
                } else {
                    null
                },
                endAxis =
                VerticalAxis.rememberEnd(
                    valueFormatter = { _, value, _ ->
                        val actualValue = value * rightDiff.toDouble() + rightMin
                        "%.0f".format(actualValue)
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
