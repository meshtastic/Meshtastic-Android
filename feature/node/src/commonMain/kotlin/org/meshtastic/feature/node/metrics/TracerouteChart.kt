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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.inboundRouteDiscovery
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.traceroute_duration
import org.meshtastic.core.resources.traceroute_forward_hops
import org.meshtastic.core.resources.traceroute_outgoing_route
import org.meshtastic.core.resources.traceroute_return_hops
import org.meshtastic.core.resources.traceroute_return_route
import org.meshtastic.core.resources.traceroute_round_trip
import org.meshtastic.core.ui.theme.GraphColors
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.proto.RouteDiscovery

/** How an entry in a node's traceroute history came about. */
internal enum class TracerouteKind {
    /** This app sent the request; its response, if any, is paired with it. */
    REQUESTED,

    /** A response addressed to us that answers no request this app sent, such as a trace started on the radio. */
    OBSERVED,

    /** The node traced us. Only the inbound route is known: our radio answered it without the app. */
    TRACED_BY,
}

/** One entry in a node's traceroute history. */
internal data class TraceroutePoint(
    val kind: TracerouteKind,
    /** The request this app sent, or null for observed and traced-by entries. */
    val request: MeshLog?,
    /** The response for requested and observed entries, or the node's inbound request for a traced-by entry. */
    val result: MeshLog?,
    /** Request timestamp in epoch seconds (the received time for entries with no request), the chart X coordinate. */
    val timeSeconds: Double,
    /** Number of intermediate hops toward the destination, or null if no response received. */
    val forwardHops: Int?,
    /** Number of intermediate hops on the return path, or null if unavailable. */
    val returnHops: Int?,
    /** Round-trip duration in seconds between request sent and response received, or null. */
    val roundTripSeconds: Double?,
) {
    /** The row this entry is keyed, dated and deleted by. */
    val log: MeshLog
        get() = request ?: result ?: error("a traceroute entry has a request or a result")

    /** The request id: the sent request's packet id, or the id an observed response answers. */
    val requestId: Int
        get() = request?.fromRadio?.packet?.id ?: result?.fromRadio?.packet?.decoded?.request_id ?: 0

    /** The traced route: both legs for requested and observed entries, the inbound leg only for traced-by. */
    val route: RouteDiscovery?
        get() {
            val packet = result?.fromRadio?.packet ?: return null
            return if (kind == TracerouteKind.TRACED_BY) packet.inboundRouteDiscovery else packet.fullRouteDiscovery
        }
}

/**
 * Chart series colours for the three traceroute metrics. Forward and return use the same colours as the routes on the
 * traceroute map, and the round trip is purple so it isn't mistaken for either direction.
 */
private enum class TracerouteMetric(val color: Color) {
    FORWARD_HOPS(TracerouteColors.OutgoingRoute),
    RETURN_HOPS(TracerouteColors.ReturnRoute),
    ROUND_TRIP(GraphColors.Purple),
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
 * Responses from a node, among [results], that answer none of [requests]: a trace started on the radio's own screen,
 * another client on the radio, or a request whose row is gone. Firmware hands the phone only packets addressed to us,
 * so these are traces from our node to the responder.
 */
internal fun observedTracerouteResponses(requests: List<MeshLog>, results: List<MeshLog>): List<MeshLog> {
    val requestedIds = requests.mapNotNull { it.fromRadio.packet?.id }.toSet()
    return results.filter { log ->
        val packet = log.fromRadio.packet ?: return@filter false
        val requestId = packet.decoded?.request_id ?: 0
        requestId !in requestedIds && packet.to != NodeAddress.NODENUM_BROADCAST && packet.fullRouteDiscovery != null
    }
}

/** Traceroute requests from a node, among [results], addressed to [ourNodeNum]: the node traced us. */
internal fun tracedByTracerouteRequests(results: List<MeshLog>, ourNodeNum: Int?): List<MeshLog> {
    if (ourNodeNum == null) return emptyList()
    return results.filter { log ->
        val packet = log.fromRadio.packet ?: return@filter false
        packet.to == ourNodeNum && packet.inboundRouteDiscovery != null
    }
}

/**
 * Builds the node's traceroute history, newest first: each of [requests] paired with its response (if any), plus the
 * [observed] responses and [tracedBy] requests. Observed entries carry hop counts but no round trip; traced-by entries
 * carry neither, as their route runs the other way.
 */
internal fun resolveTraceroutePoints(
    requests: List<MeshLog>,
    results: List<MeshLog>,
    observed: List<MeshLog> = emptyList(),
    tracedBy: List<MeshLog> = emptyList(),
): List<TraceroutePoint> {
    val requested = requests.map { request ->
        val requestPacketId = request.fromRadio.packet?.id
        val result = results.find { it.fromRadio.packet?.decoded?.request_id == requestPacketId }
        val roundTrip = result?.let {
            (it.received_date - request.received_date).coerceAtLeast(0).toDouble() / MS_PER_SEC
        }
        responsePoint(TracerouteKind.REQUESTED, request, result, request.received_date, roundTrip)
    }
    val observedPoints = observed.map {
        responsePoint(TracerouteKind.OBSERVED, null, it, it.received_date, roundTrip = null)
    }
    val tracedByPoints = tracedBy.map {
        TraceroutePoint(
            kind = TracerouteKind.TRACED_BY,
            request = null,
            result = it,
            timeSeconds = (it.received_date / MS_PER_SEC).toDouble(),
            forwardHops = null,
            returnHops = null,
            roundTripSeconds = null,
        )
    }
    return (requested + observedPoints + tracedByPoints).sortedByDescending { it.log.received_date }
}

private fun responsePoint(
    kind: TracerouteKind,
    request: MeshLog?,
    result: MeshLog?,
    receivedDate: Long,
    roundTrip: Double?,
): TraceroutePoint {
    val route = result?.fromRadio?.packet?.fullRouteDiscovery
    return TraceroutePoint(
        kind = kind,
        request = request,
        result = result,
        timeSeconds = (receivedDate / MS_PER_SEC).toDouble(),
        forwardHops = route?.let { maxOf(0, it.route.size - 2) },
        returnHops = route?.let { if (it.route_back.isNotEmpty()) maxOf(0, it.route_back.size - 2) else null },
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
                    lineModel {
                        series(x = forwardData.map { it.timeSeconds }, y = forwardData.mapNotNull { it.forwardHops })
                    }
                }
                if (returnData.isNotEmpty()) {
                    lineModel {
                        series(x = returnData.map { it.timeSeconds }, y = returnData.mapNotNull { it.returnHops })
                    }
                }
                if (rttData.isNotEmpty()) {
                    lineModel {
                        series(x = rttData.map { it.timeSeconds }, y = rttData.mapNotNull { it.roundTripSeconds })
                    }
                }
            }
        }

        val forwardColor = TracerouteMetric.FORWARD_HOPS.color
        val returnColor = TracerouteMetric.RETURN_HOPS.color
        val rttColor = TracerouteMetric.ROUND_TRIP.color

        val observedX =
            remember(points) { points.filter { it.kind == TracerouteKind.OBSERVED }.map { it.timeSeconds }.toSet() }
        val ringFill = MaterialTheme.colorScheme.surface
        val forwardPoints =
            remember(observedX, forwardColor, ringFill) { ObservedPointProvider(observedX, forwardColor, ringFill) }
        val returnPoints =
            remember(observedX, returnColor, ringFill) { ObservedPointProvider(observedX, returnColor, ringFill) }

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
                        pointProvider = forwardPoints,
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
                    ChartStyling.createDashedLine(
                        returnColor,
                        interpolator = LineCartesianLayer.Interpolator.Sharp,
                        pointProvider = returnPoints,
                    ),
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

/** Marks observed entries with a hollow ring so they read apart from requested ones; other entries get no point. */
private class ObservedPointProvider(private val observedX: Set<Double>, color: Color, ringFill: Color) :
    LineCartesianLayer.PointProvider {
    private val ring =
        LineCartesianLayer.Point(
            component =
            ShapeComponent(
                fill = Fill(ringFill),
                shape = CircleShape,
                strokeFill = Fill(color),
                strokeThickness = OBSERVED_RING_STROKE,
            ),
            size = OBSERVED_RING_SIZE,
        )

    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point? =
        ring.takeIf {
            entry.x in observedX
        }

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = ring
}

private val OBSERVED_RING_SIZE = 9.dp
private val OBSERVED_RING_STROKE = 2.dp
