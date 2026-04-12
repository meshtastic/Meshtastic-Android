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
package org.meshtastic.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.util.nodesToFeatureCollection
import org.meshtastic.feature.map.util.waypointsToFeatureCollection
import org.maplibre.spatialk.geojson.Position as GeoPosition

private val NodeMarkerColor = Color(0xFF6750A4)
private const val CLUSTER_RADIUS = 50
private const val CLUSTER_MIN_POINTS = 10
private const val PRECISION_CIRCLE_FILL_ALPHA = 0.1f
private const val PRECISION_CIRCLE_STROKE_ALPHA = 0.3f
private const val CLUSTER_OPACITY = 0.85f

/**
 * Main map content composable using MapLibre Compose Multiplatform.
 *
 * Renders nodes as clustered markers, waypoints, and optional overlays (position tracks, traceroute routes). Replaces
 * both the Google Maps and OSMDroid implementations with a single cross-platform composable.
 */
@Composable
fun MaplibreMapContent(
    nodes: List<Node>,
    waypoints: Map<Int, DataPacket>,
    baseStyle: BaseStyle,
    cameraState: CameraState,
    myNodeNum: Int?,
    showWaypoints: Boolean,
    showPrecisionCircle: Boolean,
    onNodeClick: (Int) -> Unit,
    onMapLongClick: (GeoPosition) -> Unit,
    modifier: Modifier = Modifier,
    onCameraMoved: (CameraPosition) -> Unit = {},
) {
    MaplibreMap(
        modifier = modifier,
        baseStyle = baseStyle,
        cameraState = cameraState,
        onMapLongClick = { position, _ ->
            onMapLongClick(position)
            ClickResult.Consume
        },
        onFrame = {},
    ) {
        // --- Node markers with clustering ---
        NodeMarkerLayers(
            nodes = nodes,
            myNodeNum = myNodeNum,
            showPrecisionCircle = showPrecisionCircle,
            onNodeClick = onNodeClick,
        )

        // --- Waypoint markers ---
        if (showWaypoints) {
            WaypointMarkerLayers(waypoints = waypoints)
        }
    }

    // Persist camera position when it stops moving
    LaunchedEffect(cameraState.isCameraMoving) {
        if (!cameraState.isCameraMoving) {
            onCameraMoved(cameraState.position)
        }
    }
}

/** Node markers rendered as clustered circles and symbols using GeoJSON source. */
@Composable
private fun NodeMarkerLayers(
    nodes: List<Node>,
    myNodeNum: Int?,
    showPrecisionCircle: Boolean,
    onNodeClick: (Int) -> Unit,
) {
    val featureCollection = remember(nodes, myNodeNum) { nodesToFeatureCollection(nodes, myNodeNum) }

    val nodesSource =
        rememberGeoJsonSource(
            data = GeoJsonData.Features(featureCollection),
            options =
            GeoJsonOptions(cluster = true, clusterRadius = CLUSTER_RADIUS, clusterMinPoints = CLUSTER_MIN_POINTS),
        )

    // Cluster circles
    CircleLayer(
        id = "node-clusters",
        source = nodesSource,
        filter = feature.has("cluster"),
        radius = const(20.dp),
        color = const(NodeMarkerColor), // Material primary
        opacity = const(CLUSTER_OPACITY),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
    )

    // Cluster count labels
    SymbolLayer(
        id = "node-cluster-count",
        source = nodesSource,
        filter = feature.has("cluster"),
        textField = feature["point_count"].asString(),
        textColor = const(Color.White),
        textSize = const(1.2f.em),
    )

    // Individual node markers
    CircleLayer(
        id = "node-markers",
        source = nodesSource,
        filter = !feature.has("cluster"),
        radius = const(8.dp),
        color = const(NodeMarkerColor),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
        onClick = { features ->
            val nodeNum = features.firstOrNull()?.properties?.get("node_num")?.toString()?.toIntOrNull()
            if (nodeNum != null) {
                onNodeClick(nodeNum)
                ClickResult.Consume
            } else {
                ClickResult.Pass
            }
        },
    )

    // Precision circles
    if (showPrecisionCircle) {
        CircleLayer(
            id = "node-precision",
            source = nodesSource,
            filter = !feature.has("cluster"),
            radius = const(40.dp), // TODO: scale by precision_meters and zoom
            color = const(NodeMarkerColor.copy(alpha = PRECISION_CIRCLE_FILL_ALPHA)),
            strokeWidth = const(1.dp),
            strokeColor = const(NodeMarkerColor.copy(alpha = PRECISION_CIRCLE_STROKE_ALPHA)),
        )
    }
}

/** Waypoint markers rendered as symbol layer with emoji icons. */
@Composable
private fun WaypointMarkerLayers(waypoints: Map<Int, DataPacket>) {
    val featureCollection = remember(waypoints) { waypointsToFeatureCollection(waypoints) }

    val waypointSource = rememberGeoJsonSource(data = GeoJsonData.Features(featureCollection))

    // Waypoint emoji labels
    SymbolLayer(
        id = "waypoint-markers",
        source = waypointSource,
        textField = feature["emoji"].asString(),
        textSize = const(2f.em),
        textAllowOverlap = const(true),
        iconAllowOverlap = const(true),
    )

    // Waypoint name labels below emoji
    SymbolLayer(
        id = "waypoint-labels",
        source = waypointSource,
        textField = feature["name"].asString(),
        textSize = const(1.em),
        textOffset = offset(0f.em, 2f.em),
        textColor = const(Color.DarkGray),
    )
}
