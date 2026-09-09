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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.location.BearingUpdate
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.updateCamera
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.map.LocalViewport
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.material3.LocationPuckDefaults
import org.maplibre.compose.overlay.include
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.BoundingBox
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
import org.meshtastic.feature.map.maplibre.layers.TerrainLayers
import org.meshtastic.feature.map.maplibre.layers.WaypointLayers
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.feature.map.maplibre.style.MapColors
import org.meshtastic.feature.map.maplibre.style.MapOverlay
import org.meshtastic.feature.map.maplibre.style.toBaseStyle
import org.meshtastic.feature.map.maplibre.style.zoomRange
import kotlin.math.floor

/**
 * Everything the mesh map draws, as one [MapState].
 *
 * Since maplibre-compose 0.16.0 the base style and the sources and layers over it belong to the state rather than to a
 * trailing block on `MaplibreMap`, so this is where the mesh data is read and turned into layers. [MeshMap] presents
 * the result. The state is returned because the caller also needs it: the toolbar reads the bearing off it, the zoom
 * buttons move it, and the Site Planner asks it where the map is pointed.
 *
 * The camera effects stay out of the style block, even though that is where upstream puts `LocationTrackingEffect`. The
 * library hosts style content in a subcomposition keyed on the loaded style and disposes it on every base-style switch,
 * so a `remember` in there does not survive the user changing basemap.
 *
 * Shared by the F-Droid Android flavor and the desktop app — the two differ only in what they hand in, not in what gets
 * drawn.
 */
@Composable
@Suppress("LongParameterList")
fun rememberMeshMapState(
    viewModel: BaseMapViewModel,
    navigateToNodeDetails: (Int) -> Unit,
    basemap: Basemap = Basemaps.default,
    /** Where the map opens. [rememberRestoredCamera] supplies the position the user left it on. */
    initialCameraPosition: CameraPosition = CameraPosition(),
    overlays: List<MapOverlay> = emptyList(),
    layerOpacity: Map<String, Float> = emptyMap(),
    customLayers: List<CustomLayer> = emptyList(),
    /** Called with the nodes of a tapped cluster that cannot be zoomed apart any further. */
    onClusterMembers: (List<ClusterMember>) -> Unit = {},
    onWaypointClick: (Int) -> Unit = {},
    /** The first corner of a box being authored, drawn so the tap reads as registered. */
    boxCorner: Position? = null,
    /** Location and heading state to draw a puck for and optionally follow. Null disables both. */
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
): MapState {
    val nodes by viewModel.nodesWithPosition.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()
    val displayUnits by viewModel.displayUnits.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    // The style block recomposes on every camera frame (it reads the viewport below); the mesh-wide filter should not.
    // `nowSeconds` is deliberately not a key: a packet arriving already changes the node list.
    val visibleNodes =
        remember(nodes, filterState, myNodeInfo) {
            MapNodePolicy.visibleNodes(nodes, filterState, nowSeconds, myNodeInfo?.myNodeNum)
        }

    val mapState =
        rememberMapState(baseStyle = basemap.toBaseStyle(), initialCameraPosition = initialCameraPosition) {
            val state = checkNotNull(LocalMapState.current)
            // The viewport local, rather than the state's own property: it is what the library scopes viewport
            // recomposition to, and these reads are the whole reason this block runs per frame.
            val viewportBounds = LocalViewport.current?.visibleBounds?.toBoundingBox()

            if (basemap is Basemap.Raster) {
                RasterBasemapLayer(basemap)
            }
            MapOverlayLayers(overlays, layerOpacity)
            TerrainLayers(
                viewportBounds = viewportBounds,
                zoom = state.cameraPosition.zoom,
                displayUnits = displayUnits,
            )
            CustomLayers(customLayers, layerOpacity)

            if (filterState.showWaypoints) {
                WaypointLayers(waypoints = waypoints.values, onWaypointClick = onWaypointClick)
            }

            MeshMapNodeLayers(
                visibleNodes = visibleNodes,
                mapState = state,
                viewportBounds = viewportBounds,
                filterState = filterState,
                myNodeNum = myNodeInfo?.myNodeNum,
                navigateToNodeDetails = navigateToNodeDetails,
                onClusterMembers = onClusterMembers,
                scope = scope,
            )

            // Declared last so the user's own position and an in-progress box corner draw above the mesh.
            UserLocationPuck(locationState = locationState, visible = followLocation)
            BoxCornerMarker(boxCorner)
        }

    // Both effects stay out here, in this composition rather than the style block's. The library hosts the style
    // content in a subcomposition keyed on the loaded style and disposes it on every base-style switch, so a
    // `remember` in there is lost whenever the user changes basemap — which would reset FrameOnce's latch and
    // re-frame the mesh over wherever they had panned to.
    FrameOnce(enabled = frameOnNodes, nodes = visibleNodes, mapState = mapState)

    // Follows position whenever tracking is on; touches bearing only when the caller asks for it, so a user who
    // has rotated the map is not straightened out behind their back.
    FollowUserLocation(
        locationState = locationState,
        mapState = mapState,
        followLocation = followLocation,
        bearingUpdate = bearingUpdate,
    )

    return mapState
}

/**
 * The mesh map, rendered by MapLibre.
 *
 * Presents [mapState] and nothing else; what gets drawn is declared by [rememberMeshMapState].
 *
 * [basemap] must be the same one that state was built with: it supplies the zoom range the camera is held to, and a
 * different value here would clamp the camera to a range the loaded style cannot serve.
 */
@Composable
fun MeshMap(
    mapState: MapState,
    modifier: Modifier = Modifier,
    basemap: Basemap = Basemaps.default,
    /** Called with the pressed position on a long press, which is how a new waypoint gets placed. */
    onMapLongClick: (Position) -> Unit = {},
    /** Called with the tapped position. Used to collect the two corners of a waypoint's geofence bounding box. */
    onMapClick: (Position) -> Unit = {},
) {
    // No engine on this device means presenting a map is the UnsatisfiedLinkError crash in #7001. Guarded here and
    // not at the state, which is pure Kotlin: the native library is loaded by the map *view*.
    if (!LocalMapLibreRuntimeProbe.current()) return MapEngineUnavailable(modifier)

    val zoomRange = basemap.zoomRange()
    MaplibreMap(
        modifier = modifier,
        state = mapState,
        // Honour what the source can actually serve, as the OSMdroid map did.
        cameraConstraints =
        CameraConstraints(minZoom = zoomRange.start.toDouble(), maxZoom = zoomRange.endInclusive.toDouble()),
        interactions =
        MapInteractions {
            callbacks {
                longClick {
                    onEvent { event ->
                        event.position?.let(onMapLongClick)
                        ClickResult.Consume
                    }
                }
                // Pass, not Consume: a tap has to keep reaching the layers underneath or nothing is selectable.
                click {
                    onEvent { event ->
                        event.position?.let(onMapClick)
                        ClickResult.Pass
                    }
                }
            }
        },
        overlay = { include(MeshMapOrnaments) },
    )
}

/** The node chips, clusters and precision circles — split out of [MeshMap] itself only to keep that function short. */
@Composable
@MaplibreComposable
private fun MeshMapNodeLayers(
    visibleNodes: List<Node>,
    mapState: MapState,
    viewportBounds: BoundingBox?,
    filterState: BaseMapViewModel.MapFilterState,
    myNodeNum: Int?,
    navigateToNodeDetails: (Int) -> Unit,
    onClusterMembers: (List<ClusterMember>) -> Unit,
    scope: CoroutineScope,
) {
    NodeLayers(
        nodes = visibleNodes,
        // Padded so a modest pan keeps the same nodes in view, and the chips are not redrawn for every frame of a
        // drag. Without a viewport at all — the first composition — every node is a candidate, as before.
        visibleBounds = viewportBounds?.padded(CHIP_VIEW_PADDING),
        // Floored: clustering indexes per whole zoom level, so the set only changes when the level does — and a
        // pinch does not recompute it for every fractional step in between.
        zoom = floor(mapState.cameraPosition.zoom).toInt(),
        myNodeNum = myNodeNum,
        showPrecisionCircles = filterState.showPrecisionCircle,
        onNodeClick = navigateToNodeDetails,
        onClusterMembers = onClusterMembers,
        onClusterZoom = { centre, expansionZoom ->
            scope.launch {
                val current = mapState.cameraPosition
                // A cluster that cannot report an expansion zoom answers with a sentinel (0 on
                // Android and desktop, -1 on iOS), so clamp — never zoom out on a tap.
                mapState.animateCameraPosition(current.copy(target = centre, zoom = maxOf(expansionZoom, current.zoom)))
            }
        },
    )
}

/**
 * Frames the mesh once, the first time positions arrive.
 *
 * Once only: re-fitting on every node update would yank the camera away from wherever the user had panned to, and on a
 * mesh that is still filling in that would be continuous.
 */
@Composable
private fun FrameOnce(enabled: Boolean, nodes: List<Node>, mapState: MapState) {
    if (!enabled) return

    var hasFramed by remember { mutableStateOf(false) }
    val hasViewport = mapState.viewport != null
    // The node list is read through a snapshot rather than keyed on, so an arriving packet cannot cancel this.
    // Keying on it meant the effect restarted mid-fit: `fitCameraToBounds` suspends, and whether the latch was
    // set before the call (fit lost, latch kept, mesh never framed) or after it (latch lost to a user pan, so a
    // later packet re-frames over them) one of the two failure modes was always reachable. Nothing here restarts
    // on node changes now, so the latch and the fit cannot come apart.
    val currentNodes by rememberUpdatedState(nodes)
    // An effect, not composition-body work: a launch from composition fires even if the composition is
    // abandoned, while its state write is rolled back — a camera jump with no framing recorded. Fitting before
    // the map reports a viewport silently lands on a default, hence the gate.
    LaunchedEffect(hasViewport) {
        if (hasFramed || !hasViewport) return@LaunchedEffect
        // Waits for the first node set that has anything to frame; a mesh still filling in reports none.
        val box = snapshotFlow { nodesBoundingBox(currentNodes) }.filterNotNull().first()
        hasFramed = true
        mapState.fitCameraToBounds(box)
    }
}

/** Keeps the camera on the user while tracking is on. No-op without a location source. */
@Composable
private fun FollowUserLocation(
    locationState: LocationState?,
    mapState: MapState,
    followLocation: Boolean,
    bearingUpdate: BearingUpdate,
) {
    if (locationState == null) return

    LocationTrackingEffect(
        locationState = locationState,
        enabled = followLocation,
        trackBearing = bearingUpdate != BearingUpdate.IGNORE,
    ) {
        updateCamera(mapState = mapState, updateBearing = bearingUpdate)
    }
}

/** The user's position, accuracy and heading. Drawn only while tracking, so switching it off leaves no stale dot. */
@Composable
@MaplibreComposable
private fun UserLocationPuck(locationState: LocationState?, visible: Boolean) {
    if (locationState == null || !visible) return

    // The state overload, which resolves the latest measurement and its most accurate bearing itself.
    LocationPuck(idPrefix = "user-location", locationState = locationState, colors = LocationPuckDefaults.colors())
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
