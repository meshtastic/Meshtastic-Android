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
@file:Suppress("MagicNumber", "MatchingDeclarationName")

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
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.traceroute_duration
import org.meshtastic.core.resources.traceroute_forward_hops
import org.meshtastic.core.resources.traceroute_outgoing_route
import org.meshtastic.core.resources.traceroute_return_hops
import org.meshtastic.core.resources.traceroute_return_route
import org.meshtastic.core.resources.traceroute_round_trip
import org.meshtastic.core.ui.theme.GraphColors

/** Resolved traceroute data point pairing a request with its optional response. */
internal data class TraceroutePoint(
    val request: MeshLog,
    val result: MeshLog?,
    /** Request timestamp in epoch seconds, used as the chart X coordinate. */
    val timeSeconds: Double,
    /** Number of intermediate hops toward the destination, or null if no response received. */
    val forwardHops: Int?,
    /** Number of intermediate hops on the return path, or null if unavailable. */
    val returnHops: Int?,
    /** Round-trip duration in seconds between request sent and response received, or null. */
    val roundTripSeconds: Double?,
)

/** Chart series colours for the three traceroute metrics. */
private enum class TracerouteMetric(val color: Color) {
    FORWARD_HOPS(GraphColors.Blue),
    RETURN_HOPS(GraphColors.Green),
    ROUND_TRIP(GraphColors.Orange),
}

/** Legend entries for the traceroute chart — forward hops, return hops, and round-trip duration. */
internal val TRACEROUTE_LEGEND_DATA =
    listOf(
        LegendData(
            nameRes = Res.string.traceroute_forward_hops,
            color = TracerouteMetric.FORWARD_HOPS.color,
            isLine = true,
        ),
        LegendData(
            nameRes = Res.string.traceroute_return_hops,
            color = TracerouteMetric.RETURN_HOPS.color,
            isLine = true,
        ),
        LegendData(
            nameRes = Res.string.traceroute_round_trip,
            color = TracerouteMetric.ROUND_TRIP.color,
            isLine = true,
        ),
    )

/** Info-dialog entries describing each traceroute metric for the legend help overlay. */
internal val TRACEROUTE_INFO_DATA =
    listOf(
        InfoDialogData(
            titleRes = Res.string.traceroute_forward_hops,
            definitionRes = Res.string.traceroute_outgoing_route,
            color = TracerouteMetric.FORWARD_HOPS.color,
        ),
        InfoDialogData(
            titleRes = Res.string.traceroute_return_hops,
            definitionRes = Res.string.traceroute_return_route,
            color = TracerouteMetric.RETURN_HOPS.color,
        ),
        InfoDialogData(
            titleRes = Res.string.traceroute_round_trip,
            definitionRes = Res.string.traceroute_duration,
            color = TracerouteMetric.ROUND_TRIP.color,
        ),
    )

/**
 * Matches each traceroute request with its response (if any) and computes hop counts and round-trip duration. Results
 * are ordered the same as [requests] — newest-first when coming from the ViewModel.
 */
internal fun resolveTraceroutePoints(requests: List<MeshLog>, results: List<MeshLog>): List<TraceroutePoint> =
    requests.map { request ->
        val requestPacketId = request.fromRadio.packet?.id
        val result = results.find { it.fromRadio.packet?.decoded?.request_id == requestPacketId }
        val route = result?.fromRadio?.packet?.fullRouteDiscovery
        val timeSeconds = (request.received_date / MS_PER_SEC).toDouble()

        val forwardHops = route?.let { maxOf(0, it.route.size - 2) }
        val returnHops = route?.let { if (it.route_back.isNotEmpty()) maxOf(0, it.route_back.size - 2) else null }
        val roundTrip =
            if (result != null) {
                (result.received_date - request.received_date).coerceAtLeast(0).toDouble() / MS_PER_SEC
            } else {
                null
            }

        TraceroutePoint(
            request = request,
            result = result,
            timeSeconds = timeSeconds,
            forwardHops = forwardHops,
            returnHops = returnHops,
            roundTripSeconds = roundTrip,
        )
    }

/**
 * Vico chart composable that renders forward hops, return hops, and round-trip duration as separate line series with
 * dual Y-axes: hops on the start axis (fixed min 0) and RTT seconds on the end axis.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun TracerouteMetricsChart(
    modifier: Modifier = Modifier,
    points: List<TraceroutePoint>,
    vicoScrollState: VicoScrollState,
    selectedX: Double?,
    onPointSelected: (Double) -> Unit,
) {
    MetricChartScaffold(isEmpty = points.isEmpty(), legendData = TRACEROUTE_LEGEND_DATA, modifier = modifier) {
            modelProducer,
            chartModifier,
        ->
        val forwardData = remember(points) { points.filter { it.forwardHops != null } }
        val returnData = remember(points) { points.filter { it.returnHops != null } }
        val rttData = remember(points) { points.filter { it.roundTripSeconds != null } }

        LaunchedEffect(forwardData, returnData, rttData) {
            modelProducer.runTransaction {
                if (forwardData.isNotEmpty()) {
                    lineSeries {
                        series(x = forwardData.map { it.timeSeconds }, y = forwardData.map { it.forwardHops!! })
                    }
                }
                if (returnData.isNotEmpty()) {
                    lineSeries { series(x = returnData.map { it.timeSeconds }, y = returnData.map { it.returnHops!! }) }
                }
                if (rttData.isNotEmpty()) {
                    lineSeries { series(x = rttData.map { it.timeSeconds }, y = rttData.map { it.roundTripSeconds!! }) }
                }
            }
        }

        val forwardColor = TracerouteMetric.FORWARD_HOPS.color
        val returnColor = TracerouteMetric.RETURN_HOPS.color
        val rttColor = TracerouteMetric.ROUND_TRIP.color

        val marker =
            ChartStyling.rememberMarker(
                valueFormatter =
                ChartStyling.createColoredMarkerValueFormatter { value, color ->
                    when (color) {
                        forwardColor -> formatString("Fwd: %.0f hops", value)
                        returnColor -> formatString("Ret: %.0f hops", value)
                        else -> formatString("RTT: %.1f s", value)
                    }
                },
            )

        val forwardLayer =
            rememberConditionalLayer(
                hasData = forwardData.isNotEmpty(),
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createStyledLine(
                        forwardColor,
                        interpolator = LineCartesianLayer.Interpolator.Sharp,
                    ),
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = CartesianLayerRangeProvider.auto(),
            )

        val returnLayer =
            rememberConditionalLayer(
                hasData = returnData.isNotEmpty(),
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    ChartStyling.createDashedLine(returnColor, interpolator = LineCartesianLayer.Interpolator.Sharp),
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                rangeProvider = CartesianLayerRangeProvider.auto(),
            )

        val rttLayer =
            rememberConditionalLayer(
                hasData = rttData.isNotEmpty(),
                lineProvider = LineCartesianLayer.LineProvider.series(ChartStyling.createGradientLine(rttColor)),
                verticalAxisPosition = Axis.Position.Vertical.End,
            )

        val layers =
            remember(forwardLayer, returnLayer, rttLayer) { listOfNotNull(forwardLayer, returnLayer, rttLayer) }

        if (layers.isNotEmpty()) {
            GenericMetricChart(
                modelProducer = modelProducer,
                modifier = chartModifier,
                layers = layers,
                startAxis =
                if (forwardData.isNotEmpty() || returnData.isNotEmpty()) {
                    VerticalAxis.rememberStart(
                        label = ChartStyling.rememberAxisLabel(color = forwardColor),
                        valueFormatter = { _, value, _ -> formatString("%.0f", value) },
                    )
                } else {
                    null
                },
                endAxis =
                if (rttData.isNotEmpty()) {
                    VerticalAxis.rememberEnd(
                        label = ChartStyling.rememberAxisLabel(color = rttColor),
                        valueFormatter = { _, value, _ -> formatString("%.1f s", value) },
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
