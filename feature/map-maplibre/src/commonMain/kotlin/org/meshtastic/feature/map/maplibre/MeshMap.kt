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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.location.BearingUpdate
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.mostAccurateBearing
import org.maplibre.compose.location.updateCamera
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.material3.LocationPuckDefaults
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.MapNodePolicy
import org.meshtastic.feature.map.component.MapEngineUnavailable
import org.meshtastic.feature.map.maplibre.component.MeshMapOrnaments
import org.meshtastic.feature.map.maplibre.geojson.ClusterMember
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.maplibre.layers.CustomLayer
import org.meshtastic.feature.map.maplibre.layers.CustomLayers
import org.meshtastic.feature.map.maplibre.layers.MapOverlayLayers
import org.meshtastic.feature.map.maplibre.layers.NodeLayers
import org.meshtastic.feature.map.maplibre.layers.RasterBasemapLayer
import org.meshtastic.feature.map.maplibre.layers.WaypointLayers
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.feature.map.maplibre.style.MapColors
import org.meshtastic.feature.map.maplibre.style.MapOverlay
import org.meshtastic.feature.map.maplibre.style.toBaseStyle
import org.meshtastic.feature.map.maplibre.style.zoomRange
import kotlin.math.floor

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
    layerOpacity: Map<String, Float> = emptyMap(),
    customLayers: List<CustomLayer> = emptyList(),
    onWaypointClick: (Int) -> Unit = {},
    /** Called with the nodes of a tapped cluster that cannot be zoomed apart any further. */
    onClusterMembers: (List<ClusterMember>) -> Unit = {},
    /** Called with the pressed position on a long press, which is how a new waypoint gets placed. */
    onMapLongClick: (Position) -> Unit = {},
    /** Called with the tapped position. Used to collect the two corners of a waypoint's geofence bounding box. */
    onMapClick: (Position) -> Unit = {},
    /** The first corner of a box being authored, drawn so the tap reads as registered. */
    boxCorner: Position? = null,
    /**
     * Hoisted so the host can read the bearing for a compass and steer the camera itself. Defaults to a map-owned state
     * for callers that only want the map to frame itself.
     */
    cameraState: CameraState = rememberCameraState(),
    /** Location and orientation state to draw a puck for and optionally follow. Null disables both. */
    locationState: LocationState? = null,
    /**
     * Whether the user has asked to be followed. Gates both the camera and the puck, so switching tracking off leaves
     * no stale dot behind — and matches the Google flavor, which shows the dot only while tracking.
     */
    followLocation: Boolean = false,
    /** How a location update should affect camera bearing. [BearingUpdate.IGNORE] follows position only. */
    bearingUpdate: BearingUpdate = BearingUpdate.IGNORE,
    /**
     * Whether to frame the mesh once positions arrive. False when the caller has restored a remembered camera, so the
     * user's own view is not yanked away from them.
     */
    frameOnNodes: Boolean = true,
) {
    // No engine on this device means composing a map is the UnsatisfiedLinkError crash in #7001.
    if (!isMapLibreRuntimeAvailable()) return MapEngineUnavailable(modifier)

    val nodes by viewModel.nodesWithPosition.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    // This body recomposes on every camera frame (it reads the viewport below); the mesh-wide filter should not.
    // `nowSeconds` is deliberately not a key: a packet arriving already changes the node list.
    val visibleNodes =
        remember(nodes, filterState, myNodeInfo) {
            MapNodePolicy.visibleNodes(nodes, filterState, nowSeconds, myNodeInfo?.myNodeNum)
        }

    FrameOnce(enabled = frameOnNodes, nodes = visibleNodes, cameraState = cameraState)

    // Follows position whenever tracking is on; touches bearing only when the caller asks for it, so a user who
    // has rotated the map is not straightened out behind their back.
    FollowUserLocation(
        locationState = locationState,
        cameraState = cameraState,
        followLocation = followLocation,
        bearingUpdate = bearingUpdate,
    )

    MaplibreMap(
        baseStyle = basemap.toBaseStyle(),
        cameraState = cameraState,
        modifier = modifier,
        // Honour what the source can actually serve, as the OSMdroid map did.
        zoomRange = basemap.zoomRange(),
        onMapLongClick = { position, _ ->
            onMapLongClick(position)
            ClickResult.Consume
        },
        // Pass, not Consume: a tap has to keep reaching the layers underneath or nothing on the map is selectable.
        onMapClick = { position, _ ->
            onMapClick(position)
            ClickResult.Pass
        },
        overlay = MeshMapOrnaments,
    ) {
        if (basemap is Basemap.Raster) {
            RasterBasemapLayer(basemap)
        }
        MapOverlayLayers(overlays, layerOpacity)
        CustomLayers(customLayers, layerOpacity)

        if (filterState.showWaypoints) {
            WaypointLayers(waypoints = waypoints.values, onWaypointClick = onWaypointClick)
        }

        NodeLayers(
            nodes = visibleNodes,
            // Padded so a modest pan keeps the same nodes in view, and the chips are not redrawn for every frame of a
            // drag. Without a viewport at all — the first composition — every node is a candidate, as before.
            visibleBounds = cameraState.viewport?.visibleBoundingBox?.padded(CHIP_VIEW_PADDING),
            // Floored: clustering indexes per whole zoom level, so the set only changes when the level does — and a
            // pinch does not recompute it for every fractional step in between.
            zoom = floor(cameraState.position.zoom).toInt(),
            myNodeNum = myNodeInfo?.myNodeNum,
            showPrecisionCircles = filterState.showPrecisionCircle,
            onNodeClick = navigateToNodeDetails,
            onClusterMembers = onClusterMembers,
            onClusterZoom = { centre, expansionZoom ->
                scope.launch {
                    val current = cameraState.position
                    // A cluster that cannot report an expansion zoom answers with a sentinel (0 on
                    // Android and desktop, -1 on iOS), so clamp — never zoom out on a tap.
                    cameraState.animateTo(current.copy(target = centre, zoom = maxOf(expansionZoom, current.zoom)))
                }
            },
        )

        // Declared last so the user's own position and an in-progress box corner draw above the mesh.
        UserLocationPuck(locationState = locationState, cameraState = cameraState, visible = followLocation)
        BoxCornerMarker(boxCorner)
    }
}

/**
 * Frames the mesh once, the first time positions arrive.
 *
 * Once only: re-fitting on every node update would yank the camera away from wherever the user had panned to, and on a
 * mesh that is still filling in that would be continuous.
 */
@Composable
private fun FrameOnce(enabled: Boolean, nodes: List<Node>, cameraState: CameraState) {
    if (!enabled) return

    var hasFramed by remember { mutableStateOf(false) }
    val hasViewport = cameraState.viewport != null
    // An effect, not composition-body work: a launch from composition fires even if the composition is
    // abandoned, while its state write is rolled back — a camera jump with no framing recorded. The viewport
    // gate is FitBoundsOnceVisible's: fitting before the map reports a viewport lands on a default.
    LaunchedEffect(nodes, hasViewport) {
        if (hasFramed || !hasViewport) return@LaunchedEffect
        nodesBoundingBox(nodes)?.let { box ->
            hasFramed = true
            cameraState.jumpTo(boundingBox = box)
        }
    }
}

/** Keeps the camera on the user while tracking is on. No-op without a location source. */
@Composable
private fun FollowUserLocation(
    locationState: LocationState?,
    cameraState: CameraState,
    followLocation: Boolean,
    bearingUpdate: BearingUpdate,
) {
    if (locationState == null) return

    LocationTrackingEffect(
        locationState = locationState,
        enabled = followLocation,
        trackBearing = bearingUpdate != BearingUpdate.IGNORE,
    ) {
        updateCamera(camera = cameraState, updateBearing = bearingUpdate)
    }
}

/** The user's position, accuracy and heading. Drawn only while tracking, so switching it off leaves no stale dot. */
@Composable
@MaplibreComposable
private fun UserLocationPuck(locationState: LocationState?, cameraState: CameraState, visible: Boolean) {
    if (locationState == null || !visible) return

    LocationPuck(
        idPrefix = "user-location",
        location = locationState.location,
        cameraState = cameraState,
        bearing = locationState.mostAccurateBearing(),
        colors = LocationPuckDefaults.colors(),
    )
}

/**
 * The first corner tapped while authoring a geofence box.
 *
 * The flow commits on the second tap, so there is never a both-corners-uncommitted state to preview a rectangle from —
 * the Google flavor marks the single corner for the same reason.
 */
@Composable
@MaplibreComposable
private fun BoxCornerMarker(corner: Position?) {
    if (corner == null) return

    val source =
        rememberFeatureSource(corner) {
            FeatureCollection(listOf(Feature<Point, JsonObject?>(geometry = Point(corner), properties = null)))
        }
    CircleLayer(
        id = "box-corner",
        source = source,
        color = const(MapColors.Highlight),
        radius = const(8.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )
}

/**
 * How far past the screen edge a node still counts as "in view" for chip drawing, as a fraction of the visible span.
 *
 * Half a screen in each direction: enough that a flick does not immediately invalidate the chip set, small enough that
 * a dense mesh does not blow the image budget on nodes well off screen.
 */
private const val CHIP_VIEW_PADDING = 0.5
