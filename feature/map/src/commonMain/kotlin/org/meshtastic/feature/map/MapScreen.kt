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
package org.meshtastic.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.rememberCameraState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.MaplibreMapContent

/**
 * Main map screen composable. Uses MapLibre Compose Multiplatform to render an interactive map with mesh node markers,
 * waypoints, and overlays.
 *
 * This replaces the previous flavor-specific Google Maps and OSMDroid implementations with a single cross-platform
 * composable.
 */
@Composable
fun MapScreen(
    onClickNodeChip: (Int) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    waypointId: Int? = null,
) {
    val ourNodeInfo by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val nodesWithPosition by viewModel.nodesWithPosition.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val baseStyle by viewModel.baseStyle.collectAsStateWithLifecycle()

    LaunchedEffect(waypointId) { viewModel.setWaypointId(waypointId) }

    val cameraState = rememberCameraState(firstPosition = viewModel.initialCameraPosition)

    @Suppress("ViewModelForwarding")
    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.map),
                ourNode = ourNodeInfo,
                showNodeChip = ourNodeInfo != null && isConnected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            MaplibreMapContent(
                nodes = nodesWithPosition,
                waypoints = waypoints,
                baseStyle = baseStyle,
                cameraState = cameraState,
                myNodeNum = viewModel.myNodeNum,
                showWaypoints = filterState.showWaypoints,
                showPrecisionCircle = filterState.showPrecisionCircle,
                onNodeClick = { nodeNum -> navigateToNodeDetails(nodeNum) },
                onMapLongClick = { position ->
                    // TODO: open waypoint creation dialog at position
                },
                modifier = Modifier.fillMaxSize(),
                onCameraMoved = { position -> viewModel.saveCameraPosition(position) },
            )

            MapControlsOverlay(
                onToggleFilterMenu = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(paddingValues),
                bearing = cameraState.position.bearing.toFloat(),
                onCompassClick = {},
                isLocationTrackingEnabled = false,
                onToggleLocationTracking = {},
            )
        }
    }
}
