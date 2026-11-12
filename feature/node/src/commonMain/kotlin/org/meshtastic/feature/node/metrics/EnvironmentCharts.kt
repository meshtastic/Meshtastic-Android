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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import org.meshtastic.core.common.util.MeasureUnitKind
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.VOLT_SYMBOL
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.adc_voltage
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
    Environment.oneWireTemperatures.mapIndexed { index, entry ->
        LegendData(
            nameRes = Res.string.one_wire_temperature,
            channelNumber = index + 1,
            color = entry.color,
            isLine = true,
            metricKey = entry,
        )
    }

private val LEGEND_DATA_5 =
    Environment.adcVoltages.mapIndexed { index, entry ->
        LegendData(
            nameRes = Res.string.adc_voltage,
            channelNumber = index + 1,
            color = entry.color,
            isLine = true,
            metricKey = entry,
        )
    }

/**
 * Wind speed arrives in m/s and is charted in the unit the cards show — km/h for metric, mph for imperial — so a
 * reading and its graph never disagree. Temperatures are already converted upstream by the view model, so they pass
 * through here unchanged.
 */
internal fun chartValue(metric: Environment, telemetry: Telemetry, isImperial: Boolean): Float? =
    metric.getValue(telemetry)?.let {
        if (metric == Environment.WIND_SPEED) {
            // Wind is plotted in the same unit the cards show — km/h or mph, never the sensor's raw m/s.
            if (isImperial) UnitConversions.metersPerSecondToMph(it) else UnitConversions.metersPerSecondToKph(it)
        } else {
            it
        }
    }

/**
 * Unit suffix for a plotted metric's axis and marker labels, in the user's display units, or "" for a dimensionless
 * one. Includes any leading space, so it appends directly to a formatted value.
 */
internal fun unitSuffix(metric: Environment, isFahrenheit: Boolean, isImperial: Boolean): String = when {
    metric == Environment.TEMPERATURE ||
        metric == Environment.SOIL_TEMPERATURE ||
        metric in Environment.oneWireTemperatures -> MetricFormatter.degreeSymbol(isFahrenheit)

    metric in Environment.adcVoltages -> " $VOLT_SYMBOL"

    metric == Environment.HUMIDITY || metric == Environment.SOIL_MOISTURE -> "%"

    metric == Environment.BAROMETRIC_PRESSURE -> " hPa"

    metric == Environment.LUX -> " lx"

    metric == Environment.UV_LUX -> " UVlx"

    metric == Environment.RADIATION -> " µR/h"

    metric == Environment.GAS_RESISTANCE -> " MΩ"

    // Taken from the same source the cards format with, so an axis and its readings cannot drift apart.
    metric == Environment.WIND_SPEED ->
        " " + (if (isImperial) MeasureUnitKind.MILE_PER_HOUR else MeasureUnitKind.KILOMETER_PER_HOUR).symbol

    else -> ""
}

private val CHART_GROUP_HEIGHT_MIN = 140.dp
private const val MAX_FILLING_GROUPS = 2

private fun groupRangeProvider(group: EnvironmentChartGroup) = object : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        environmentAxisRange(group, minY, maxY)?.first
            ?: CartesianLayerRangeProvider.auto().getMinY(minY, maxY, extraStore)

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        environmentAxisRange(group, minY, maxY)?.second
            ?: CartesianLayerRangeProvider.auto().getMaxY(minY, maxY, extraStore)
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun EnvironmentMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    graphData: EnvironmentGraphingData,
    isFahrenheit: Boolean,
    isImperial: Boolean,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    Column(modifier = modifier) {
        if (telemetries.isEmpty()) {
            return@Column
        }

        val allLegendData =
            (LEGEND_DATA_1 + LEGEND_DATA_2 + LEGEND_DATA_3 + LEGEND_DATA_4 + LEGEND_DATA_5).filter {
                graphData.shouldPlot[(it.metricKey as? Environment)?.ordinal ?: 0]
            }

        // Track hidden metrics by key (not index) so toggling survives changes in allLegendData ordering.
        var hiddenMetrics by remember { mutableStateOf(emptySet<Environment>()) }
        val hiddenIndices =
            remember(hiddenMetrics, allLegendData) {
                allLegendData.indices.filter { (allLegendData[it].metricKey as? Environment) in hiddenMetrics }.toSet()
            }

        val colorToLabel = allLegendData.associate { it.color to legendLabel(it) }
        val colorToUnit = allLegendData.associate { legend ->
            val metric = legend.metricKey as? Environment
            legend.color to (metric?.let { unitSuffix(it, isFahrenheit, isImperial) } ?: "")
        }

        val metricData =
            remember(telemetries, graphData) {
                Environment.entries
                    .filter { graphData.shouldPlot[it.ordinal] }
                    .associateWith { metric ->
                        telemetries.filter {
                            val v = metric.getValue(it)
                            it.time != 0 && v != null && !v.isNaN()
                        }
                    }
                    .filterValues { it.isNotEmpty() }
            }
        val groups =
            remember(metricData, hiddenMetrics) {
                groupEnvironmentMetrics(metricData.keys.filter { it !in hiddenMetrics })
            }

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    val label = colorToLabel[color] ?: ""
                    formatString("%s: %.1f", label, value) + (colorToUnit[color] ?: "")
                },
            )

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val groupHeight =
                (maxHeight / minOf(groups.size, MAX_FILLING_GROUPS).coerceAtLeast(1)).coerceAtLeast(
                    CHART_GROUP_HEIGHT_MIN,
                )
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                groups.forEachIndexed { index, (group, metrics) ->
                    key(group) {
                        EnvironmentGroupChart(
                            group = group,
                            metrics = metrics,
                            metricData = metricData,
                            isFahrenheit = isFahrenheit,
                            isImperial = isImperial,
                            marker = marker,
                            vicoScrollState =
                            if (index == 0) {
                                vicoScrollState
                            } else {
                                rememberVicoScrollState(
                                    autoScroll = Scroll.Absolute.End,
                                    autoScrollCondition = AutoScrollCondition.OnModelGrowth,
                                )
                            },
                            followsSelection = index != 0,
                            selectedX = selectedX,
                            onPointSelected = onPointSelected,
                            modifier = Modifier.fillMaxWidth().height(groupHeight).padding(horizontal = 8.dp),
                        )
                    }
                }
            }
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

@Composable
private fun EnvironmentGroupChart(
    group: EnvironmentChartGroup,
    metrics: List<Environment>,
    metricData: Map<Environment, List<Telemetry>>,
    isFahrenheit: Boolean,
    isImperial: Boolean,
    marker: CartesianMarker,
    vicoScrollState: VicoScrollState,
    followsSelection: Boolean,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    // The screen scrolls the first chart on a log-row tap; the others follow the same selection.
    LaunchedEffect(selectedX) {
        if (followsSelection && selectedX != null) {
            vicoScrollState.animateScroll(Scroll.Absolute.x(selectedX, CommonCharts.SCROLL_BIAS))
        }
    }
    LaunchedEffect(metrics, metricData, isImperial) {
        modelProducer.runTransaction {
            metrics.forEach { metric ->
                val data = metricData[metric].orEmpty()
                lineModel {
                    series(x = data.map { it.time }, y = data.mapNotNull { chartValue(metric, it, isImperial) })
                }
            }
        }
    }

    val rangeProvider = remember(group) { groupRangeProvider(group) }
    val layers = metrics.map { metric ->
        val line =
            when {
                metric == Environment.WIND_SPEED -> ChartStyling.createDashedLine(metric.color)
                group == EnvironmentChartGroup.PRESSURE -> ChartStyling.createGradientLine(metric.color)
                else -> ChartStyling.createStyledLine(metric.color)
            }
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(line),
            verticalAxisPosition = Axis.Position.Vertical.Start,
            rangeProvider = rangeProvider,
        )
    }

    val unit = metrics.map { unitSuffix(it, isFahrenheit, isImperial) }.distinct().singleOrNull().orEmpty()
    val axisColor = metrics.singleOrNull()?.color ?: MaterialTheme.colorScheme.onSurface
    GenericMetricChart(
        modelProducer = modelProducer,
        modifier = modifier,
        layers = layers,
        startAxis =
        VerticalAxis.rememberStart(
            label = ChartStyling.rememberAxisLabel(color = axisColor),
            valueFormatter = { context, value, position ->
                val decimals = axisLabelDecimals(context.ranges.getYRange(position).length)
                NumberFormatter.format(value, decimals) + unit
            },
        ),
        bottomAxis = CommonCharts.rememberBottomTimeAxis(),
        marker = marker,
        selectedX = selectedX,
        onPointSelected = onPointSelected,
        vicoScrollState = vicoScrollState,
    )
}
