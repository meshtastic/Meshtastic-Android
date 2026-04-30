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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getTracerouteResponse
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.traceroute_diff
import org.meshtastic.core.resources.traceroute_direct
import org.meshtastic.core.resources.traceroute_duration
import org.meshtastic.core.resources.traceroute_forward_hops
import org.meshtastic.core.resources.traceroute_hops
import org.meshtastic.core.resources.traceroute_log
import org.meshtastic.core.resources.traceroute_no_response
import org.meshtastic.core.resources.traceroute_return_hops
import org.meshtastic.core.resources.traceroute_round_trip
import org.meshtastic.core.resources.traceroute_route_back_to_us
import org.meshtastic.core.resources.traceroute_route_towards_dest
import org.meshtastic.core.ui.icon.Group
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PersonOff
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Route
import org.meshtastic.core.ui.theme.GraphColors
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.annotateTraceroute
import org.meshtastic.feature.node.component.CooldownIconButton
import org.meshtastic.proto.RouteDiscovery

/**
 * Full-screen traceroute log with chart and card list, built on [BaseMetricScreen]. Shows forward/return hops and
 * round-trip duration over time. Supports time-frame filtering, chart expand/collapse, and card-to-chart
 * synchronisation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod", "UnusedParameter")
@Composable
fun TracerouteLogScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricsViewModel,
    onNavigateUp: () -> Unit,
    onViewOnMap: (requestId: Int, responseLogUuid: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val lastTracerouteTime by viewModel.lastTraceRouteTime.collectAsStateWithLifecycle()

    fun getUsername(nodeNum: Int): String = with(viewModel.getUser(nodeNum)) { "$long_name ($short_name)" }

    val statusGreen = MaterialTheme.colorScheme.StatusGreen
    val statusYellow = MaterialTheme.colorScheme.StatusYellow
    val statusOrange = MaterialTheme.colorScheme.StatusOrange

    val headerTowardsStr = stringResource(Res.string.traceroute_route_towards_dest)
    val headerBackStr = stringResource(Res.string.traceroute_route_back_to_us)
    val durationFormatStr = stringResource(Res.string.traceroute_duration)

    val threshold = timeFrame.timeThreshold()
    val filteredRequests =
        remember(state.tracerouteRequests, threshold) {
            state.tracerouteRequests.filter { (it.received_date / MS_PER_SEC) >= threshold }
        }

    val points =
        remember(filteredRequests, state.tracerouteResults) {
            resolveTraceroutePoints(filteredRequests, state.tracerouteResults)
        }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = null,
        titleRes = Res.string.traceroute_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = points,
        timeProvider = { it.timeSeconds },
        infoData = TRACEROUTE_INFO_DATA,
        extraActions = {
            if (!state.isLocal) {
                CooldownIconButton(
                    onClick = { viewModel.requestTraceroute() },
                    cooldownTimestamp = lastTracerouteTime,
                ) {
                    Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                }
            }
        },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        chartPart = { chartModifier, selectedX, vicoScrollState, onPointSelected ->
            TracerouteMetricsChart(
                modifier = chartModifier,
                points = points.reversed(),
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { listModifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = listModifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(points, key = { _, point -> point.request.uuid }) { _, point ->
                    TracerouteCard(
                        point = point,
                        isSelected = point.timeSeconds == selectedX,
                        onClick = { onCardClick(point.timeSeconds) },
                        onLongClick = { viewModel.deleteLog(point.request.uuid) },
                        onShowDetail = {
                            showTracerouteDetail(
                                point = point,
                                viewModel = viewModel,
                                getUsername = ::getUsername,
                                headerTowards = headerTowardsStr,
                                headerBack = headerBackStr,
                                durationTemplate = durationFormatStr,
                                statusGreen = statusGreen,
                                statusYellow = statusYellow,
                                statusOrange = statusOrange,
                                onViewOnMap = onViewOnMap,
                            )
                        },
                    )
                }
            }
        },
    )
}

/** A selectable card summarising a single traceroute request/response pair. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TracerouteCard(
    point: TraceroutePoint,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowDetail: () -> Unit,
) {
    val route = point.result?.fromRadio?.packet?.fullRouteDiscovery
    val time = DateFormatter.formatDateTime(point.request.received_date)
    val (summaryText, icon) = route.getTextAndIcon()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .combinedClickable(
                    onLongClick = { expanded = true },
                    onClick = {
                        onClick()
                        onShowDetail()
                    },
                ),
            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            colors =
            CardDefaults.cardColors(
                containerColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            TracerouteCardContent(time = time, summaryText = summaryText, icon = icon, point = point)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeleteItem {
                onLongClick()
                expanded = false
            }
        }
    }
}

/** Card body showing timestamp, route summary text/icon, and metric indicators. */
@Composable
private fun TracerouteCardContent(time: String, summaryText: String, icon: ImageVector, point: TraceroutePoint) {
    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = time, style = MaterialTheme.typography.titleMediumEmphasized, fontWeight = FontWeight.Bold)
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TracerouteCardMetrics(point)
    }
}

/** Compact coloured metric indicators (forward hops / return hops / RTT) shown at the bottom of a card. */
@Composable
private fun TracerouteCardMetrics(point: TraceroutePoint) {
    if (point.forwardHops == null && point.returnHops == null && point.roundTripSeconds == null) return
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        point.forwardHops?.let { hops ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricIndicator(GraphColors.Blue)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatString("%s: %d", stringResource(Res.string.traceroute_forward_hops), hops),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        point.returnHops?.let { hops ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricIndicator(GraphColors.Green)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatString("%s: %d", stringResource(Res.string.traceroute_return_hops), hops),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        point.roundTripSeconds?.let { rtt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricIndicator(GraphColors.Orange)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatString("%s: %.1f s", stringResource(Res.string.traceroute_round_trip), rtt),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Builds annotated route text and opens the traceroute detail dialog via the ViewModel. */
@Suppress("LongParameterList")
private fun showTracerouteDetail(
    point: TraceroutePoint,
    viewModel: MetricsViewModel,
    getUsername: (Int) -> String,
    headerTowards: String,
    headerBack: String,
    durationTemplate: String,
    statusGreen: Color,
    statusYellow: Color,
    statusOrange: Color,
    onViewOnMap: (requestId: Int, responseLogUuid: String) -> Unit,
) {
    val result = point.result ?: return
    val route = result.fromRadio.packet?.fullRouteDiscovery

    val annotated: AnnotatedString =
        if (route != null && route.route.isNotEmpty() && route.route_back.isNotEmpty()) {
            val seconds = point.roundTripSeconds ?: 0.0
            val annotatedBase =
                annotateTraceroute(
                    result.fromRadio.packet?.getTracerouteResponse(
                        getUsername,
                        headerTowards = headerTowards,
                        headerBack = headerBack,
                    ),
                    statusGreen = statusGreen,
                    statusYellow = statusYellow,
                    statusOrange = statusOrange,
                )
            val durationText = formatString(durationTemplate, NumberFormatter.format(seconds, 1))
            buildAnnotatedString {
                append(annotatedBase)
                append("\n\n$durationText")
            }
        } else {
            result.fromRadio.packet
                ?.getTracerouteResponse(getUsername, headerTowards = headerTowards, headerBack = headerBack)
                ?.let { AnnotatedString(it) } ?: return
        }

    val overlay =
        route?.let {
            TracerouteOverlay(
                requestId = point.request.fromRadio.packet?.id ?: 0,
                forwardRoute = it.route,
                returnRoute = it.route_back,
            )
        }

    viewModel.showTracerouteDetail(
        annotatedMessage = annotated,
        requestId = point.request.fromRadio.packet?.id ?: 0,
        responseLogUuid = result.uuid,
        overlay = overlay,
        onViewOnMap = onViewOnMap,
    )
}

/** Generates a display string and icon based on the route discovery information. */
@Composable
private fun RouteDiscovery?.getTextAndIcon(): Pair<String, ImageVector> = when {
    this == null -> {
        stringResource(Res.string.traceroute_no_response) to MeshtasticIcons.PersonOff
    }

    route.size <= 2 && route_back.size <= 2 -> {
        stringResource(Res.string.traceroute_direct) to MeshtasticIcons.Group
    }

    route.size == route_back.size -> {
        val hops = route.size - 2
        pluralStringResource(Res.plurals.traceroute_hops, hops, hops) to MeshtasticIcons.Route
    }

    else -> {
        val towards = maxOf(0, route.size - 2)
        val back = maxOf(0, route_back.size - 2)
        stringResource(Res.string.traceroute_diff, towards, back) to MeshtasticIcons.Route
    }
}
