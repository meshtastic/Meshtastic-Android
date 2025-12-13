/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.traceroute
import org.meshtastic.core.strings.traceroute_outgoing_route
import org.meshtastic.core.strings.traceroute_return_route
import org.meshtastic.core.strings.traceroute_showing_nodes
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.map.MapView
import org.meshtastic.feature.map.model.TracerouteOutgoingColor
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.feature.map.model.TracerouteReturnColor

@Composable
fun TracerouteMapScreen(
    metricsViewModel: MetricsViewModel = hiltViewModel(),
    requestId: Int,
    onNavigateUp: () -> Unit,
) {
    val state by metricsViewModel.state.collectAsStateWithLifecycle()
    val nodeTitle = state.node?.user?.longName ?: stringResource(Res.string.traceroute)
    val routeDiscovery =
        state.tracerouteResults
            .find { it.fromRadio.packet.decoded.requestId == requestId }
            ?.fromRadio
            ?.packet
            ?.fullRouteDiscovery
    val overlayFromLogs =
        remember(routeDiscovery) {
            routeDiscovery?.let {
                TracerouteOverlay(requestId = requestId, forwardRoute = it.routeList, returnRoute = it.routeBackList)
            }
        }
    val overlayFromService = remember(requestId) { metricsViewModel.getTracerouteOverlay(requestId) }
    val overlay = overlayFromLogs ?: overlayFromService
    var tracerouteNodesShown by remember { mutableStateOf(0) }
    var tracerouteNodesTotal by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { metricsViewModel.clearTracerouteResponse() }

    Scaffold(
        topBar = {
            MainAppBar(
                title = nodeTitle,
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            MapView(
                navigateToNodeDetails = {},
                tracerouteOverlay = overlay,
                onTracerouteMappableCountChanged = { shown, total ->
                    tracerouteNodesShown = shown
                    tracerouteNodesTotal = total
                },
            )
            Column(
                modifier =
                Modifier.align(TracerouteMapOverlayInsets.overlayAlignment)
                    .padding(TracerouteMapOverlayInsets.overlayPadding),
                horizontalAlignment = TracerouteMapOverlayInsets.contentHorizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TracerouteNodeCount(shown = tracerouteNodesShown, total = tracerouteNodesTotal)
                TracerouteLegend()
            }
        }
    }
}

@Composable
private fun TracerouteLegend(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendRow(color = TracerouteOutgoingColor, label = stringResource(Res.string.traceroute_outgoing_route))
            LegendRow(color = TracerouteReturnColor, label = stringResource(Res.string.traceroute_return_route))
        }
    }
}

@Composable
private fun TracerouteNodeCount(modifier: Modifier = Modifier, shown: Int, total: Int) {
    Card(modifier = modifier) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = stringResource(Res.string.traceroute_showing_nodes, shown, total),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
