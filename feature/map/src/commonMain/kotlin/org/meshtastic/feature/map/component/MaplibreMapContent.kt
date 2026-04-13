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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.convertToNumber
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.exponential
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.times
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.HillshadeLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.UserLocationState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.RasterDemEncoding
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterDemSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Point
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

/**
 * Ground resolution at the equator: meters per pixel = 156543.03 / 2^zoom. We use an exponential(2) interpolation with
 * two stops to compute the conversion factor from meters to pixels at each zoom level. The result is multiplied by the
 * per-feature `precision_meters` property to produce a screen-pixel radius.
 */
private const val EQUATORIAL_METERS_PER_PIXEL_ZOOM0 = 156543.03f
private const val PRECISION_ZOOM_MIN = 0
private const val PRECISION_ZOOM_MAX = 24
private const val PRECISION_SCALE_MIN = 1f / EQUATORIAL_METERS_PER_PIXEL_ZOOM0

@Suppress("MagicNumber")
private const val PRECISION_SCALE_MAX = 16_777_216f / EQUATORIAL_METERS_PER_PIXEL_ZOOM0 // 2^24
private const val CLUSTER_OPACITY = 0.85f
private const val LABEL_OFFSET_EM = 1.5f
private const val CLUSTER_ZOOM_INCREMENT = 2.0
private const val HILLSHADE_EXAGGERATION = 0.5f

/** Free Terrain Tiles (Terrarium encoding) hosted on AWS. No API key required. */
private val TERRAIN_TILES = listOf("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png")

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
    showHillshade: Boolean,
    onNodeClick: (Int) -> Unit,
    onMapLongClick: (GeoPosition) -> Unit,
    modifier: Modifier = Modifier,
    gestureOptions: GestureOptions = GestureOptions.Standard,
    onCameraMoved: (CameraPosition) -> Unit = {},
    onWaypointClick: (Int) -> Unit = {},
    onMapLoadFinished: () -> Unit = {},
    onMapLoadFailed: (String?) -> Unit = {},
    locationState: UserLocationState? = null,
) {
    MaplibreMap(
        modifier = modifier,
        baseStyle = baseStyle,
        cameraState = cameraState,
        options = MapOptions(gestureOptions = gestureOptions, ornamentOptions = OrnamentOptions.AllEnabled),
        onMapLongClick = { position, _ ->
            onMapLongClick(position)
            ClickResult.Consume
        },
        onMapLoadFinished = onMapLoadFinished,
        onMapLoadFailed = onMapLoadFailed,
        onFrame = {},
    ) {
        // --- Terrain hillshade overlay ---
        if (showHillshade) {
            val demSource = rememberRasterDemSource(tiles = TERRAIN_TILES, encoding = RasterDemEncoding.Terrarium)
            HillshadeLayer(id = "terrain-hillshade", source = demSource, exaggeration = const(HILLSHADE_EXAGGERATION))
        }

        // --- Node markers with clustering ---
        NodeMarkerLayers(
            nodes = nodes,
            myNodeNum = myNodeNum,
            showPrecisionCircle = showPrecisionCircle,
            cameraState = cameraState,
            onNodeClick = onNodeClick,
        )

        // --- Waypoint markers ---
        if (showWaypoints) {
            WaypointMarkerLayers(waypoints = waypoints, onWaypointClick = onWaypointClick)
        }

        // --- User location puck ---
        if (locationState != null) {
            LocationPuck(idPrefix = "user-location", locationState = locationState, cameraState = cameraState)
        }
    }

    // Persist camera position when it stops moving
    LaunchedEffect(cameraState.isCameraMoving) {
        if (!cameraState.isCameraMoving) {
            onCameraMoved(cameraState.position)
        }
    }
}

/** Node markers rendered as clustered circles with per-node colors and short name labels. */
@Suppress("LongMethod")
@Composable
private fun NodeMarkerLayers(
    nodes: List<Node>,
    myNodeNum: Int?,
    showPrecisionCircle: Boolean,
    cameraState: CameraState,
    onNodeClick: (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val featureCollection = remember(nodes, myNodeNum) { nodesToFeatureCollection(nodes, myNodeNum) }

    val nodesSource =
        rememberGeoJsonSource(
            data = GeoJsonData.Features(featureCollection),
            options =
            GeoJsonOptions(cluster = true, clusterRadius = CLUSTER_RADIUS, clusterMinPoints = CLUSTER_MIN_POINTS),
        )

    // Cluster circles — tap to zoom in toward expansion
    CircleLayer(
        id = "node-clusters",
        source = nodesSource,
        filter = feature.has("cluster"),
        radius = const(20.dp),
        color = const(NodeMarkerColor), // Material primary
        opacity = const(CLUSTER_OPACITY),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
        onClick = { features ->
            val cluster = features.firstOrNull() ?: return@CircleLayer ClickResult.Pass
            val target = (cluster.geometry as? Point)?.coordinates ?: return@CircleLayer ClickResult.Pass
            coroutineScope.launch {
                cameraState.animateTo(
                    cameraState.position.copy(
                        target = target,
                        zoom = cameraState.position.zoom + CLUSTER_ZOOM_INCREMENT,
                    ),
                )
            }
            ClickResult.Consume
        },
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

    // Individual node markers with per-node background color
    CircleLayer(
        id = "node-markers",
        source = nodesSource,
        filter = !feature.has("cluster"),
        radius = const(8.dp),
        color = feature["background_color"].convertToColor(const(NodeMarkerColor)),
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

    // Short name labels below node markers
    SymbolLayer(
        id = "node-labels",
        source = nodesSource,
        filter = !feature.has("cluster"),
        textField = feature["short_name"].asString(),
        textSize = const(0.9f.em),
        textOffset = offset(0f.em, LABEL_OFFSET_EM.em),
        textColor = const(Color.DarkGray),
        textAllowOverlap = const(true),
        iconAllowOverlap = const(true),
    )

    // Precision circles — sized by precision_meters property converted to screen pixels via zoom interpolation
    if (showPrecisionCircle) {
        // Meters-to-pixels factor doubles with each zoom level (equatorial approximation)
        val metersToPixels =
            interpolate(
                exponential(2f),
                zoom(),
                PRECISION_ZOOM_MIN to const(PRECISION_SCALE_MIN),
                PRECISION_ZOOM_MAX to const(PRECISION_SCALE_MAX),
            )
        CircleLayer(
            id = "node-precision",
            source = nodesSource,
            filter = !feature.has("cluster"),
            radius = (feature["precision_meters"].convertToNumber(const(0f)) * metersToPixels).dp,
            color =
            feature["background_color"].convertToColor(
                const(NodeMarkerColor.copy(alpha = PRECISION_CIRCLE_FILL_ALPHA)),
            ),
            opacity = const(PRECISION_CIRCLE_FILL_ALPHA),
            strokeWidth = const(1.dp),
            strokeColor =
            feature["background_color"].convertToColor(
                const(NodeMarkerColor.copy(alpha = PRECISION_CIRCLE_STROKE_ALPHA)),
            ),
            strokeOpacity = const(PRECISION_CIRCLE_STROKE_ALPHA),
        )
    }
}

/** Waypoint markers rendered as symbol layer with emoji icons and click handling. */
@Composable
private fun WaypointMarkerLayers(waypoints: Map<Int, DataPacket>, onWaypointClick: (Int) -> Unit) {
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
        onClick = { features ->
            val waypointId = features.firstOrNull()?.properties?.get("waypoint_id")?.toString()?.toIntOrNull()
            if (waypointId != null) {
                onWaypointClick(waypointId)
                ClickResult.Consume
            } else {
                ClickResult.Pass
            }
        },
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
