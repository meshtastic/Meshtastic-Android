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
package org.meshtastic.feature.map.mapcompose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.model.geofence.toGeofence
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.WaypointInfoDialog
import org.meshtastic.feature.map.mapcompose.component.ClusterItemsListDialog
import org.meshtastic.feature.map.mapcompose.component.DiscoveryLayer
import org.meshtastic.feature.map.mapcompose.component.InlineLayer
import org.meshtastic.feature.map.mapcompose.component.MapAttributionBar
import org.meshtastic.feature.map.mapcompose.component.MapComposeFilterDropdown
import org.meshtastic.feature.map.mapcompose.component.MapComposeTrackFilterDropdown
import org.meshtastic.feature.map.mapcompose.component.MapScaleBar
import org.meshtastic.feature.map.mapcompose.component.NodeClusterMarkers
import org.meshtastic.feature.map.mapcompose.component.NodeTrackLayer
import org.meshtastic.feature.map.mapcompose.component.PUSHPIN_CODEPOINT
import org.meshtastic.feature.map.mapcompose.component.TileSourceButton
import org.meshtastic.feature.map.mapcompose.component.TracerouteLayer
import org.meshtastic.feature.map.mapcompose.component.WaypointMarkers
import org.meshtastic.feature.map.mapcompose.component.nodeNumFromMarkerId
import org.meshtastic.feature.map.mapcompose.component.toGeoPoint
import org.meshtastic.feature.map.mapcompose.component.toNormalized
import org.meshtastic.feature.map.mapcompose.component.trackTimeFromMarkerId
import org.meshtastic.feature.map.mapcompose.component.waypointIdFromMarkerId
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import org.meshtastic.feature.map.mapcompose.geo.toNormalized
import org.meshtastic.feature.map.mapcompose.tile.TileSourceCatalog
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.feature.map.tracerouteNodeSelection
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint
import ovh.plrapps.mapcompose.api.onLongPress
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.rotateTo
import ovh.plrapps.mapcompose.api.rotation
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.state.MapState

/** The mode sealed for the shared renderer — mirrors the google flavor's `GoogleMapMode` (plus Discovery/Inline). */
sealed interface MapComposeMode {
    /** Standard map: clustered nodes, waypoints with editing, geofence overlays. */
    data class Main(val waypointId: Int? = null) : MapComposeMode

    /** Focused node position track: fading polyline + selectable historical positions. */
    data class NodeTrack(
        val focusedNode: Node?,
        val positions: List<Position>,
        val selectedPositionTime: Int? = null,
        val onPositionSelected: ((Int) -> Unit)? = null,
    ) : MapComposeMode

    /** Traceroute visualization: offset forward/return polylines + hop markers. */
    data class Traceroute(
        val overlay: TracerouteOverlay?,
        val nodePositions: Map<Int, Position>,
        val onMappableCountChanged: (shown: Int, total: Int) -> Unit,
    ) : MapComposeMode

    /** Discovery results around a scanned position. */
    data class Discovery(val userLatitude: Double, val userLongitude: Double, val nodes: List<DiscoveryMapNode>) :
        MapComposeMode

    /** Small non-interactive single-node map embedded in detail screens. */
    data class Inline(val node: Node) : MapComposeMode
}

/**
 * The shared MapCompose renderer behind every map seam — the multiplatform twin of the google flavor's `MapView`.
 * One slippy-map scaffold ([MapUI] over [rememberMeshMapState]) hosts mode-specific content layers.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MapComposeMapView(
    mode: MapComposeMode,
    navigateToNodeDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SharedMapViewModel>()
    val mapPrefs = koinInject<MapPrefs>()
    val coroutineScope = rememberCoroutineScope()

    val selectedTileSourceId by mapPrefs.selectedTileSourceId.collectAsStateWithLifecycle()
    val tileSource = TileSourceCatalog.byId(selectedTileSourceId)

    val isMainMode = mode is MapComposeMode.Main
    val savedCamera = remember(isMainMode) { if (isMainMode) MapCamera.decode(mapPrefs.mapCameraPosition.value) else null }
    val inlineCamera =
        (mode as? MapComposeMode.Inline)?.node?.let { MapCamera(it.latitude, it.longitude, INLINE_ZOOM) }

    val mapState =
        rememberMeshMapState(
            tileSource = tileSource,
            interactive = mode !is MapComposeMode.Inline,
            initialCamera = savedCamera ?: inlineCamera,
        )

    if (isMainMode) {
        PersistCameraEffect(mapState, tileSource, mapPrefs)
    }

    // --- Shared VM state ---
    val mapFilterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val ourNodeInfo by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val displayUnits by viewModel.displayUnits.collectAsStateWithLifecycle()
    val allNodes by viewModel.nodesWithPosition.collectAsStateWithLifecycle()
    val waypointPackets by viewModel.waypoints.collectAsStateWithLifecycle()
    val myNodeNum = viewModel.myNodeNum

    val displayableWaypoints = remember(waypointPackets) { waypointPackets.values.mapNotNull { it.waypoint } }
    val filteredNodes =
        remember(allNodes, mapFilterState, ourNodeInfo) {
            allNodes
                .filter { !mapFilterState.onlyFavorites || it.isFavorite || it.num == ourNodeInfo?.num }
                .filter {
                    mapFilterState.lastHeardFilter.seconds == 0L ||
                        (nowSeconds - it.lastHeard) <= mapFilterState.lastHeardFilter.seconds ||
                        it.num == ourNodeInfo?.num
                }
        }

    // --- Dialog state ---
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var geofenceInfoWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var sameLocationNodes by remember { mutableStateOf<List<Node>?>(null) }
    var mapFilterMenuExpanded by remember { mutableStateOf(false) }

    val currentWaypoints by rememberUpdatedState(displayableWaypoints)
    val currentNav by rememberUpdatedState(navigateToNodeDetails)
    val currentMode by rememberUpdatedState(mode)

    // --- Input dispatch (one registration per MapState) ---
    remember(mapState) {
        mapState.onMarkerClick { id, _, _ ->
            nodeNumFromMarkerId(id)?.let { num -> currentNav(num) }
            waypointIdFromMarkerId(id)?.let { wptId ->
                val waypoint = currentWaypoints.firstOrNull { it.id == wptId } ?: return@onMarkerClick
                when {
                    waypoint.toGeofence() != null && !viewModel.isMyWaypoint(waypoint.id) ->
                        geofenceInfoWaypoint = waypoint

                    waypoint.locked_to == 0 || waypoint.locked_to == viewModel.myNodeNum || !isConnected ->
                        editingWaypoint = waypoint

                    // Locked by another node: show the read-only info view (google shows a toast here).
                    else -> geofenceInfoWaypoint = waypoint
                }
            }
            trackTimeFromMarkerId(id)?.let { time ->
                (currentMode as? MapComposeMode.NodeTrack)?.onPositionSelected?.invoke(time)
            }
        }
        mapState.onLongPress { x, y ->
            if (currentMode is MapComposeMode.Main && isConnected) {
                editingWaypoint =
                    Waypoint(
                        latitude_i = (WebMercator.yToLatitude(y) / DEG_D).toInt(),
                        longitude_i = (WebMercator.xToLongitude(x) / DEG_D).toInt(),
                    )
            }
        }
        true
    }

    // --- First-open camera ---
    var hasCentered by rememberSaveable(mode::class.simpleName) { mutableStateOf(false) }
    LaunchedEffect(mapState, filteredNodes, mode) {
        if (hasCentered) return@LaunchedEffect
        val points =
            when (val m = mode) {
                is MapComposeMode.Main ->
                    if (savedCamera == null) filteredNodes.map { it.toNormalized() } else emptyList()

                is MapComposeMode.NodeTrack -> m.positions.map { it.toNormalized() }
                is MapComposeMode.Traceroute -> m.nodePositions.values.map { it.toNormalized() }
                is MapComposeMode.Discovery ->
                    (m.nodes.map { WebMercator.toNormalized(it.latitude, it.longitude) } +
                        listOfNotNull(
                            WebMercator.toNormalized(m.userLatitude, m.userLongitude)
                                .takeIf { m.userLatitude != 0.0 || m.userLongitude != 0.0 },
                        ))

                is MapComposeMode.Inline -> emptyList()
            }
        when {
            points.size == 1 -> {
                mapState.scrollTo(points.first().x, points.first().y, zoomToScale(SINGLE_POINT_ZOOM, tileSource.maxZoom))
                hasCentered = true
            }

            points.size > 1 -> {
                WebMercator.boundingBox(points)?.let { mapState.scrollToBox(it) }
                hasCentered = true
            }
        }
    }

    // Waypoint deep-link: center on it and open the editor/info flow.
    if (mode is MapComposeMode.Main && mode.waypointId != null) {
        LaunchedEffect(mode.waypointId, displayableWaypoints) {
            val waypoint = displayableWaypoints.firstOrNull { it.id == mode.waypointId } ?: return@LaunchedEffect
            val p = waypoint.toNormalized()
            mapState.scrollTo(p.x, p.y, zoomToScale(SINGLE_POINT_ZOOM, tileSource.maxZoom))
            if (waypoint.toGeofence() != null && !viewModel.isMyWaypoint(waypoint.id)) {
                geofenceInfoWaypoint = waypoint
            } else {
                editingWaypoint = waypoint
            }
        }
    }

    // Traceroute node resolution + count callback (mirrors the google renderer).
    val allNodesUnfiltered by viewModel.nodes.collectAsStateWithLifecycle()
    val tracerouteSelection =
        if (mode is MapComposeMode.Traceroute) {
            remember(mode.overlay, mode.nodePositions, allNodesUnfiltered) {
                viewModel.tracerouteNodeSelection(
                    tracerouteOverlay = mode.overlay,
                    tracerouteNodePositions = mode.nodePositions,
                    nodes = allNodesUnfiltered,
                )
            }
        } else {
            null
        }
    if (mode is MapComposeMode.Traceroute) {
        val shownNodes = tracerouteSelection?.nodesForMarkers ?: emptyList()
        LaunchedEffect(mode.overlay, shownNodes) {
            if (mode.overlay != null) {
                mode.onMappableCountChanged(shownNodes.size, mode.overlay.relatedNodeNums.size)
            }
        }
    }

    // NodeTrack: filter + sort positions; recenter when the list selects a position.
    val sortedTrackPositions =
        if (mode is MapComposeMode.NodeTrack) {
            remember(mode.positions, mapFilterState.lastHeardTrackFilter) {
                mode.positions
                    .filter {
                        mapFilterState.lastHeardTrackFilter == LastHeardFilter.Any ||
                            it.time > nowSeconds - mapFilterState.lastHeardTrackFilter.seconds
                    }
                    .sortedBy { it.time }
            }
        } else {
            emptyList()
        }
    if (mode is MapComposeMode.NodeTrack) {
        LaunchedEffect(mode.selectedPositionTime) {
            val selected = sortedTrackPositions.find { it.time == mode.selectedPositionTime } ?: return@LaunchedEffect
            val p = selected.toGeoPoint().let { WebMercator.toNormalized(it.latitude, it.longitude) }
            mapState.scrollTo(p.x, p.y)
        }
    }

    // --- Render ---
    Box(modifier = modifier) {
        MapUI(modifier = Modifier.fillMaxSize(), state = mapState)

        when (val m = mode) {
            is MapComposeMode.Main -> {
                NodeClusterMarkers(
                    mapState = mapState,
                    nodes = filteredNodes,
                    myNodeNum = myNodeNum,
                    showPrecisionCircles = mapFilterState.showPrecisionCircle,
                    scope = coroutineScope,
                    onSameLocationCluster = { sameLocationNodes = it },
                )
                WaypointMarkers(
                    mapState = mapState,
                    waypoints = displayableWaypoints,
                    showWaypoints = mapFilterState.showWaypoints,
                )
            }

            is MapComposeMode.NodeTrack ->
                NodeTrackLayer(
                    mapState = mapState,
                    focusedNode = m.focusedNode,
                    sortedPositions = sortedTrackPositions,
                    selectedPositionTime = m.selectedPositionTime,
                )

            is MapComposeMode.Traceroute ->
                TracerouteLayer(
                    mapState = mapState,
                    displayNodes = tracerouteSelection?.nodesForMarkers ?: emptyList(),
                    forwardPoints =
                    m.overlay?.forwardRoute?.mapNotNull {
                        tracerouteSelection?.nodeLookup?.get(it)?.validPosition?.toGeoPoint()
                    } ?: emptyList(),
                    returnPoints =
                    m.overlay?.returnRoute?.mapNotNull {
                        tracerouteSelection?.nodeLookup?.get(it)?.validPosition?.toGeoPoint()
                    } ?: emptyList(),
                )

            is MapComposeMode.Discovery -> DiscoveryLayer(mapState = mapState, mode = m)

            is MapComposeMode.Inline -> InlineLayer(mapState = mapState, node = m.node, showPrecision = true)
        }

        // Chrome: controls for the interactive full-screen modes, attribution + scale everywhere except inline.
        if (isMainMode || mode is MapComposeMode.NodeTrack) {
            MapControlsOverlay(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                onToggleFilterMenu = { mapFilterMenuExpanded = true },
                filterDropdownContent = {
                    if (mode is MapComposeMode.NodeTrack) {
                        MapComposeTrackFilterDropdown(
                            expanded = mapFilterMenuExpanded,
                            onDismissRequest = { mapFilterMenuExpanded = false },
                            viewModel = viewModel,
                        )
                    } else {
                        MapComposeFilterDropdown(
                            expanded = mapFilterMenuExpanded,
                            onDismissRequest = { mapFilterMenuExpanded = false },
                            viewModel = viewModel,
                        )
                    }
                },
                mapTypeContent = {
                    TileSourceButton(selected = tileSource, onSelect = { mapPrefs.setSelectedTileSourceId(it.id) })
                },
                bearing = mapState.rotation,
                onCompassClick = { coroutineScope.launch { mapState.rotateTo(0f) } },
                // No location source on desktop (R7): the my-location button is not composed at all.
                showLocationTracking = false,
            )
        }

        if (mode !is MapComposeMode.Inline) {
            MapScaleBar(
                mapState = mapState,
                tileSource = tileSource,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
            MapAttributionBar(tileSource = tileSource, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }

        // --- Dialogs (Main mode) ---
        if (isMainMode) {
            editingWaypoint?.let { waypointToEdit ->
                EditWaypointDialog(
                    waypoint = waypointToEdit,
                    displayUnits = displayUnits,
                    onSend = { updated ->
                        var finalWp = updated
                        if (updated.id == 0) finalWp = finalWp.copy(id = viewModel.generatePacketId())
                        if (updated.icon == 0) finalWp = finalWp.copy(icon = PUSHPIN_CODEPOINT)
                        viewModel.sendWaypoint(finalWp)
                        editingWaypoint = null
                    },
                    onDelete = { toDelete ->
                        if (toDelete.locked_to == 0 && isConnected && toDelete.id != 0) {
                            viewModel.sendWaypoint(toDelete.copy(expire = 1))
                        }
                        viewModel.deleteWaypoint(toDelete.id)
                        editingWaypoint = null
                    },
                    onDismissRequest = { editingWaypoint = null },
                    // Two-tap box authoring is not implemented in the shared renderer yet (deferred with the rest of
                    // the geofence-authoring flow); radius geofences remain fully editable.
                )
            }

            geofenceInfoWaypoint?.let { waypoint ->
                val optIns by viewModel.geofenceAlertOptIns.collectAsStateWithLifecycle()
                WaypointInfoDialog(
                    waypoint = waypoint,
                    displayUnits = displayUnits,
                    alertsEnabled = waypoint.id in optIns,
                    onToggleAlerts = { viewModel.setGeofenceAlertOptIn(waypoint.id, it) },
                    onDismissRequest = { geofenceInfoWaypoint = null },
                    onEdit =
                    if (waypoint.locked_to == 0 && isConnected) {
                        {
                            geofenceInfoWaypoint = null
                            editingWaypoint = waypoint
                        }
                    } else {
                        null
                    },
                )
            }

            sameLocationNodes?.let { nodes ->
                ClusterItemsListDialog(
                    nodes = nodes,
                    onDismiss = { sameLocationNodes = null },
                    onNodeClick = { node ->
                        sameLocationNodes = null
                        navigateToNodeDetails(node.num)
                    },
                )
            }
        }
    }
}

private const val SINGLE_POINT_ZOOM = 12.0
private const val INLINE_ZOOM = 15.0
