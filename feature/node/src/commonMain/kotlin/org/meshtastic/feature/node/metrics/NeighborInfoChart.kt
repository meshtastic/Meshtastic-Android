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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.neighborInfo
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.neighbor_count
import org.meshtastic.core.resources.neighbor_count_description
import org.meshtastic.core.resources.neighbor_mean_snr
import org.meshtastic.core.resources.neighbor_mean_snr_description
import org.meshtastic.core.ui.theme.GraphColors
import org.meshtastic.feature.node.model.NeighborInfoEntry

private val NEIGHBOR_COUNT_COLOR = GraphColors.Blue
private val NEIGHBOR_SNR_COLOR = GraphColors.Teal

internal val NEIGHBOR_INFO_LEGEND_DATA =
    listOf(
        LegendData(nameRes = Res.string.neighbor_count, color = NEIGHBOR_COUNT_COLOR, isLine = true),
        LegendData(nameRes = Res.string.neighbor_mean_snr, color = NEIGHBOR_SNR_COLOR, isLine = true),
    )

internal val NEIGHBOR_INFO_INFO_DATA =
    listOf(
        InfoDialogData(
            titleRes = Res.string.neighbor_count,
            definitionRes = Res.string.neighbor_count_description,
            color = NEIGHBOR_COUNT_COLOR,
        ),
        InfoDialogData(
            titleRes = Res.string.neighbor_mean_snr,
            definitionRes = Res.string.neighbor_mean_snr_description,
            color = NEIGHBOR_SNR_COLOR,
        ),
    )

/** One reported neighbor list: how many neighbors, and their mean SNR when there are any. */
internal data class NeighborInfoChartPoint(val timeSeconds: Double, val neighborCount: Int, val meanSnr: Double?)

/** Chart points for the entries that carry a neighbor list, oldest first; unanswered requests have none. */
internal fun buildNeighborInfoChartPoints(entries: List<NeighborInfoEntry>): List<NeighborInfoChartPoint> = entries
    .mapNotNull { entry ->
        val neighbors = entry.report?.fromRadio?.packet?.neighborInfo?.neighbors ?: return@mapNotNull null
        NeighborInfoChartPoint(
            timeSeconds = entry.timeSeconds,
            neighborCount = neighbors.size,
            meanSnr = neighbors.takeIf { it.isNotEmpty() }?.map { it.snr.toDouble() }?.average(),
        )
    }
    .sortedBy { it.timeSeconds }

/** Neighbor count on the start axis and mean neighbor SNR on the end axis, one point per reported neighbor list. */
@Suppress("LongMethod")
@Composable
internal fun NeighborInfoChart(
    entries: List<NeighborInfoEntry>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onSelectPoint: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = remember(entries) { buildNeighborInfoChartPoints(entries) }
    MetricChartScaffold(isEmpty = points.isEmpty(), legendData = NEIGHBOR_INFO_LEGEND_DATA, modifier = modifier) {
            modelProducer,
            chartModifier,
        ->
        val snrPoints = remember(points) { points.filter { it.meanSnr != null } }

        LaunchedEffect(points) {
            modelProducer.runTransaction {
                lineModel {
                    series(x = points.map { it.timeSeconds }, y = points.map { it.neighborCount })
                }
                if (snrPoints.isNotEmpty()) {
                    lineModel {
                        series(x = snrPoints.map { it.timeSeconds }, y = snrPoints.map { it.meanSnr ?: 0.0 })
                    }
                }
            }
        }

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color) {
                        NEIGHBOR_COUNT_COLOR -> formatString("%.0f", value)
                        else -> MetricFormatter.snr(value.toFloat())
                    }
                },
            )

        val countLayer =
            rememberConditionalLayer(
                hasData = points.isNotEmpty(),
                lineProvider =
                LineCartesianLayer.LineProvider.series(ChartStyling.createStyledLine(NEIGHBOR_COUNT_COLOR)),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0),
            )
        val snrLayer =
            rememberConditionalLayer(
                hasData = snrPoints.isNotEmpty(),
                lineProvider =
                LineCartesianLayer.LineProvider.series(ChartStyling.createDashedLine(NEIGHBOR_SNR_COLOR)),
                verticalAxisPosition = Axis.Position.Vertical.End,
            )
        val layers = remember(countLayer, snrLayer) { listOfNotNull(countLayer, snrLayer) }

        if (layers.isNotEmpty()) {
            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = layers,
                startAxis =
                VerticalAxis.rememberStart(
                    label = ChartStyling.rememberAxisLabel(color = NEIGHBOR_COUNT_COLOR),
                    valueFormatter = { _, value, _ -> formatString("%.0f", value) },
                ),
                endAxis =
                if (snrPoints.isNotEmpty()) {
                    VerticalAxis.rememberEnd(
                        label = ChartStyling.rememberAxisLabel(color = NEIGHBOR_SNR_COLOR),
                        valueFormatter = { _, value, _ -> formatString("%.0f", value) },
                    )
                } else {
                    null
                },
                bottomAxis = CommonCharts.rememberBottomTimeAxis(),
                marker = marker,
                selectedX = selectedX,
                onPointSelected = onSelectPoint,
                vicoScrollState = vicoScrollState,
            )
        }
    }
}
