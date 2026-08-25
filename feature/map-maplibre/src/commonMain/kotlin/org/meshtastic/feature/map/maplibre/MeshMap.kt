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
package org.meshtastic.feature.map.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.maplibre.layers.MapOverlayLayers
import org.meshtastic.feature.map.maplibre.layers.NodeLayers
import org.meshtastic.feature.map.maplibre.layers.RasterBasemapLayer
import org.meshtastic.feature.map.maplibre.layers.WaypointLayers
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.feature.map.maplibre.style.MapOverlay

/** Vector basemaps arrive as a style document; raster ones draw over an empty one. */
private fun Basemap.toBaseStyle(): BaseStyle = when (this) {
    is Basemap.Vector -> BaseStyle.Uri(styleUri)
    is Basemap.Raster -> BaseStyle.Empty
}

/**
 * The mesh map, rendered by MapLibre.
 *
 * Shared by the F-Droid Android flavor and the desktop app — the two differ only in what they hand in, not in what gets
 * drawn.
 */
@Composable
fun MeshMap(
    viewModel: BaseMapViewModel,
    navigateToNodeDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
    basemap: Basemap = Basemaps.default,
    overlays: List<MapOverlay> = emptyList(),
    onWaypointClick: (Int) -> Unit = {},
) {
    val nodes by viewModel.nodesWithPosition.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()

    val cameraState = rememberCameraState()
    val scope = rememberCoroutineScope()

    val visibleNodes = filterNodesForMap(nodes, filterState, nowSeconds)

    // Frame the mesh once, the first time positions arrive. Re-fitting on every node update would
    // yank the camera away from wherever the user had panned to.
    val hasFramed = remember { mutableStateOf(false) }
    if (!hasFramed.value) {
        nodesBoundingBox(visibleNodes)?.let { box ->
            hasFramed.value = true
            scope.launch { cameraState.jumpTo(boundingBox = box) }
        }
    }

    MaplibreMap(baseStyle = basemap.toBaseStyle(), cameraState = cameraState, modifier = modifier) {
        if (basemap is Basemap.Raster) {
            RasterBasemapLayer(basemap)
        }
        MapOverlayLayers(overlays)

        if (filterState.showWaypoints) {
            WaypointLayers(waypoints = waypoints.values, onWaypointClick = onWaypointClick)
        }

        NodeLayers(
            nodes = visibleNodes,
            myNodeNum = myNodeInfo?.myNodeNum,
            showPrecisionCircles = filterState.showPrecisionCircle,
            onNodeClick = navigateToNodeDetails,
            onClusterZoom = { centre, expansionZoom ->
                scope.launch {
                    val current = cameraState.position
                    // A cluster that cannot report an expansion zoom answers with a sentinel (0 on
                    // Android and desktop, -1 on iOS), so clamp — never zoom out on a tap.
                    cameraState.animateTo(current.copy(target = centre, zoom = maxOf(expansionZoom, current.zoom)))
                }
            },
        )
    }
}
