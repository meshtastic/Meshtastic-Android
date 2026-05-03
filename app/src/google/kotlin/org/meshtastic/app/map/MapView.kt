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

package org.meshtastic.app.map

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.SphericalUtil
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerInfoWindowComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.widgets.ScaleBar
import com.google.maps.android.data.Layer
import com.google.maps.android.data.geojson.GeoJsonLayer
import com.google.maps.android.data.kml.KmlLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.component.ClusterItemsListDialog
import org.meshtastic.app.map.component.CustomMapLayersSheet
import org.meshtastic.app.map.component.CustomTileProviderManagerSheet
import org.meshtastic.app.map.component.EditWaypointDialog
import org.meshtastic.app.map.component.MapFilterDropdown
import org.meshtastic.app.map.component.MapTypeDropdown
import org.meshtastic.app.map.component.NodeClusterMarkers
import org.meshtastic.app.map.component.NodeMapFilterDropdown
import org.meshtastic.app.map.component.WaypointMarkers
import org.meshtastic.app.map.model.NodeClusterItem
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.model.util.GeoConstants.HEADING_DEG
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.mpsToKmph
import org.meshtastic.core.model.util.mpsToMph
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.alt
import org.meshtastic.core.resources.heading
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.core.resources.manage_map_layers
import org.meshtastic.core.resources.map_tile_source
import org.meshtastic.core.resources.position
import org.meshtastic.core.resources.sats
import org.meshtastic.core.resources.speed
import org.meshtastic.core.resources.timestamp
import org.meshtastic.core.resources.track_point
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.icon.Layers
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.TripOrigin
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.core.ui.util.formatPositionTime
import org.meshtastic.feature.map.BaseMapViewModel.MapFilterState
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.tracerouteNodeSelection
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint
import kotlin.math.abs
import kotlin.math.max

// region --- Map Mode ---

/**
 * Discriminated mode for [MapView] — replaces the original pile of nullable parameters with a type-safe sealed
 * hierarchy. Each mode carries only the data it needs; the shared infrastructure (location tracking, tile providers,
 * controls overlay) is available in every mode.
 */
sealed interface GoogleMapMode {
    /** Standard map: node clusters, waypoints, custom layers, waypoint editing. */
    data object Main : GoogleMapMode

    /** Focused node position track: polyline + gradient markers for historical positions. */
    data class NodeTrack(
        val focusedNode: Node?,
        val positions: List<Position>,
        val selectedPositionTime: Int? = null,
        val onPositionSelected: ((Int) -> Unit)? = null,
    ) : GoogleMapMode

    /** Traceroute visualization: offset forward/return polylines + hop markers. */
    data class Traceroute(
        val overlay: TracerouteOverlay?,
        val nodePositions: Map<Int, Position>,
        val onMappableCountChanged: (shown: Int, total: Int) -> Unit,
    ) : GoogleMapMode
}

// endregion

private const val TRACEROUTE_OFFSET_METERS = 100.0
private const val TRACEROUTE_BOUNDS_PADDING_PX = 120

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = koinViewModel(),
    navigateToNodeDetails: (Int) -> Unit = {},
    mode: GoogleMapMode = GoogleMapMode.Main,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()

    // --- Location permissions ---
    val locationPermissionsState =
        rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    var triggerLocationToggleAfterPermission by remember { mutableStateOf(false) }

    // --- Location tracking ---
    var isLocationTrackingEnabled by remember { mutableStateOf(false) }
    var followPhoneBearing by remember { mutableStateOf(false) }

    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted && triggerLocationToggleAfterPermission) {
            isLocationTrackingEnabled = true
            triggerLocationToggleAfterPermission = false
        }
    }

    // --- File picker for map layers (Main mode) ---
    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val fileName = uri.getFileName(context)
                    mapViewModel.addMapLayer(uri, fileName)
                }
            }
        }

    // --- UI state ---
    var mapFilterMenuExpanded by remember { mutableStateOf(false) }
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val ourNodeInfo by mapViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }

    val selectedGoogleMapType by mapViewModel.selectedGoogleMapType.collectAsStateWithLifecycle()
    val currentCustomTileProviderUrl by mapViewModel.selectedCustomTileProviderUrl.collectAsStateWithLifecycle()

    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    var showCustomTileManagerSheet by remember { mutableStateOf(false) }

    // --- Camera ---
    // Main mode persists camera; NodeTrack/Traceroute use ephemeral state with auto-centering.
    val cameraPositionState =
        if (mode is GoogleMapMode.Main) mapViewModel.cameraPositionState else rememberCameraPositionState()

    if (mode is GoogleMapMode.Main) {
        LaunchedEffect(cameraPositionState.isMoving) {
            if (!cameraPositionState.isMoving) {
                mapViewModel.saveCameraPosition(cameraPositionState.position)
            }
        }
    }

    // --- FusedLocation ---
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isLocationTrackingEnabled) {
                    locationResult.lastLocation?.let { location ->
                        val latLng = LatLng(location.latitude, location.longitude)
                        val cameraUpdate =
                            if (followPhoneBearing) {
                                val bearing =
                                    if (location.hasBearing()) {
                                        location.bearing
                                    } else {
                                        cameraPositionState.position.bearing
                                    }
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(latLng)
                                        .zoom(cameraPositionState.position.zoom)
                                        .bearing(bearing)
                                        .build(),
                                )
                            } else {
                                CameraUpdateFactory.newLatLngZoom(latLng, cameraPositionState.position.zoom)
                            }
                        coroutineScope.launch {
                            try {
                                cameraPositionState.animate(cameraUpdate)
                            } catch (e: IllegalStateException) {
                                Logger.d { "Error animating camera to location: ${e.message}" }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isLocationTrackingEnabled, locationPermissionsState.allPermissionsGranted) {
        if (isLocationTrackingEnabled && locationPermissionsState.allPermissionsGranted) {
            val locationRequest =
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                    .setMinUpdateIntervalMillis(2000L)
                    .build()
            try {
                @Suppress("MissingPermission")
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                Logger.d { "Started location tracking" }
            } catch (e: SecurityException) {
                Logger.d { "Location permission not available: ${e.message}" }
                isLocationTrackingEnabled = false
            }
        } else {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Logger.d { "Stopped location tracking" }
        }
    }

    DisposableEffect(Unit) { onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) } }

    // --- Node & waypoint data ---
    val allNodes by mapViewModel.nodesWithPosition.collectAsStateWithLifecycle(listOf())
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())
    val displayableWaypoints = waypoints.values.mapNotNull { it.waypoint }
    val selectedWaypointId by mapViewModel.selectedWaypointId.collectAsStateWithLifecycle()

    val filteredNodes =
        allNodes
            .filter { node -> !mapFilterState.onlyFavorites || node.isFavorite || node.num == ourNodeInfo?.num }
            .filter { node ->
                mapFilterState.lastHeardFilter.seconds == 0L ||
                    (nowSeconds - node.lastHeard) <= mapFilterState.lastHeardFilter.seconds ||
                    node.num == ourNodeInfo?.num
            }

    val myNodeNum = mapViewModel.myNodeNum
    val isConnected by mapViewModel.isConnected.collectAsStateWithLifecycle()
    val theme by mapViewModel.theme.collectAsStateWithLifecycle()
    val dark =
        when (theme) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> isSystemInDarkTheme()
            else -> isSystemInDarkTheme()
        }
    val mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT

    // --- Mode-specific data ---
    // Node track: apply time filter
    val sortedTrackPositions =
        if (mode is GoogleMapMode.NodeTrack) {
            val lastHeardTrackFilter = mapFilterState.lastHeardTrackFilter
            remember(mode.positions, lastHeardTrackFilter) {
                mode.positions
                    .filter {
                        lastHeardTrackFilter == LastHeardFilter.Any ||
                            it.time > nowSeconds - lastHeardTrackFilter.seconds
                    }
                    .sortedBy { it.time }
            }
        } else {
            emptyList()
        }

    // Traceroute: resolve node selection + polylines. Collected unconditionally per Compose rules
    // (composable calls cannot be conditional), but only consumed in Traceroute mode. Uses all
    // nodes, not just those with positions, so getNodeOrFallback can resolve metadata for hops
    // whose positions come from snapshots.
    val allNodesForTraceroute by mapViewModel.nodes.collectAsStateWithLifecycle(listOf())
    val tracerouteSelection =
        if (mode is GoogleMapMode.Traceroute) {
            remember(mode.overlay, mode.nodePositions, allNodesForTraceroute) {
                mapViewModel.tracerouteNodeSelection(
                    tracerouteOverlay = mode.overlay,
                    tracerouteNodePositions = mode.nodePositions,
                    nodes = allNodesForTraceroute,
                )
            }
        } else {
            null
        }
    val tracerouteDisplayNodes = tracerouteSelection?.nodesForMarkers ?: emptyList()

    if (mode is GoogleMapMode.Traceroute) {
        LaunchedEffect(mode.overlay, tracerouteDisplayNodes) {
            if (mode.overlay != null) {
                mode.onMappableCountChanged(tracerouteDisplayNodes.size, mode.overlay.relatedNodeNums.size)
            }
        }
    }

    val tracerouteForwardPoints: List<LatLng> =
        if (mode is GoogleMapMode.Traceroute && tracerouteSelection != null) {
            val nodeLookup = tracerouteSelection.nodeLookup
            remember(mode.overlay, nodeLookup) {
                mode.overlay?.forwardRoute?.mapNotNull { nodeLookup[it]?.position?.toLatLng() } ?: emptyList()
            }
        } else {
            emptyList()
        }
    val tracerouteReturnPoints: List<LatLng> =
        if (mode is GoogleMapMode.Traceroute && tracerouteSelection != null) {
            val nodeLookup = tracerouteSelection.nodeLookup
            remember(mode.overlay, nodeLookup) {
                mode.overlay?.returnRoute?.mapNotNull { nodeLookup[it]?.position?.toLatLng() } ?: emptyList()
            }
        } else {
            emptyList()
        }
    val tracerouteHeadingReferencePoints =
        remember(tracerouteForwardPoints, tracerouteReturnPoints) {
            when {
                tracerouteForwardPoints.size >= 2 -> tracerouteForwardPoints
                tracerouteReturnPoints.size >= 2 -> tracerouteReturnPoints
                else -> emptyList()
            }
        }
    val tracerouteForwardOffsetPoints =
        remember(tracerouteForwardPoints, tracerouteHeadingReferencePoints) {
            offsetPolyline(tracerouteForwardPoints, TRACEROUTE_OFFSET_METERS, tracerouteHeadingReferencePoints, 1.0)
        }
    val tracerouteReturnOffsetPoints =
        remember(tracerouteReturnPoints, tracerouteHeadingReferencePoints) {
            offsetPolyline(tracerouteReturnPoints, TRACEROUTE_OFFSET_METERS, tracerouteHeadingReferencePoints, -1.0)
        }

    // Auto-centering for NodeTrack / Traceroute modes
    var hasCentered by remember(mode) { mutableStateOf(false) }

    if (mode is GoogleMapMode.NodeTrack) {
        LaunchedEffect(sortedTrackPositions, hasCentered) {
            if (hasCentered || sortedTrackPositions.isEmpty()) return@LaunchedEffect
            val points = sortedTrackPositions.map { it.toLatLng() }
            val cameraUpdate =
                if (points.size == 1) {
                    CameraUpdateFactory.newLatLngZoom(points.first(), max(cameraPositionState.position.zoom, 12f))
                } else {
                    val bounds = LatLngBounds.builder()
                    points.forEach { bounds.include(it) }
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)
                }
            try {
                cameraPositionState.animate(cameraUpdate)
                hasCentered = true
            } catch (e: IllegalStateException) {
                Logger.d { "Error centering track map: ${e.message}" }
            }
        }

        // Animate to selected position marker when card is tapped in the list
        LaunchedEffect(mode.selectedPositionTime) {
            val selectedTime = mode.selectedPositionTime ?: return@LaunchedEffect
            val selectedPos = sortedTrackPositions.find { it.time == selectedTime } ?: return@LaunchedEffect
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLng(selectedPos.toLatLng()))
            } catch (e: IllegalStateException) {
                Logger.d { "Error animating to selected position: ${e.message}" }
            }
        }
    }

    if (mode is GoogleMapMode.Traceroute) {
        LaunchedEffect(mode.overlay, tracerouteForwardPoints, tracerouteReturnPoints) {
            if (mode.overlay == null || hasCentered) return@LaunchedEffect
            val allPoints = (tracerouteForwardPoints + tracerouteReturnPoints).distinct()
            if (allPoints.isNotEmpty()) {
                val cameraUpdate =
                    if (allPoints.size == 1) {
                        CameraUpdateFactory.newLatLngZoom(
                            allPoints.first(),
                            max(cameraPositionState.position.zoom, 12f),
                        )
                    } else {
                        val bounds = LatLngBounds.builder()
                        allPoints.forEach { bounds.include(it) }
                        CameraUpdateFactory.newLatLngBounds(bounds.build(), TRACEROUTE_BOUNDS_PADDING_PX)
                    }
                try {
                    cameraPositionState.animate(cameraUpdate)
                    hasCentered = true
                } catch (e: IllegalStateException) {
                    Logger.d { "Error centering traceroute overlay: ${e.message}" }
                }
            }
        }
    }

    // --- Tile & layers state ---
    var showLayersBottomSheet by remember { mutableStateOf(false) }

    val onAddLayerClicked = {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                val mimeTypes =
                    arrayOf(
                        "application/vnd.google-earth.kml+xml",
                        "application/vnd.google-earth.kmz",
                        "application/vnd.geo+json",
                        "application/geo+json",
                        "application/json",
                    )
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
        filePickerLauncher.launch(intent)
    }
    val onRemoveLayer = { layerId: String -> mapViewModel.removeMapLayer(layerId) }
    val onToggleVisibility = { layerId: String -> mapViewModel.toggleLayerVisibility(layerId) }

    val effectiveGoogleMapType = if (currentCustomTileProviderUrl != null) MapType.NONE else selectedGoogleMapType

    var showClusterItemsDialog by remember { mutableStateOf<List<NodeClusterItem>?>(null) }

    // --- Keep screen on while location tracking ---
    LaunchedEffect(isLocationTrackingEnabled) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window
        if (isLocationTrackingEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // --- Main UI ---
    val isMainMode = mode is GoogleMapMode.Main

    Box(modifier = modifier) {
        GoogleMap(
            mapColorScheme = mapColorScheme,
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings =
            MapUiSettings(
                zoomControlsEnabled = true,
                mapToolbarEnabled = isMainMode,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                rotationGesturesEnabled = true,
                scrollGesturesEnabled = true,
                tiltGesturesEnabled = isMainMode,
                zoomGesturesEnabled = true,
            ),
            properties =
            MapProperties(
                mapType = effectiveGoogleMapType,
                isMyLocationEnabled = isLocationTrackingEnabled && locationPermissionsState.allPermissionsGranted,
            ),
            onMapLongClick = { latLng ->
                if (isMainMode && isConnected) {
                    editingWaypoint =
                        Waypoint(
                            latitude_i = (latLng.latitude / DEG_D).toInt(),
                            longitude_i = (latLng.longitude / DEG_D).toInt(),
                        )
                }
            },
        ) {
            // Custom tile overlay (all modes)
            key(currentCustomTileProviderUrl) {
                currentCustomTileProviderUrl?.let { url ->
                    val config =
                        mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle().value.find {
                            it.urlTemplate == url || it.localUri == url
                        }
                    mapViewModel.getTileProvider(config)?.let { tileProvider ->
                        TileOverlay(tileProvider = tileProvider, fadeIn = true, transparency = 0f, zIndex = -1f)
                    }
                }
            }

            when (mode) {
                is GoogleMapMode.Main ->
                    MainMapContent(
                        nodeClusterItems =
                        filteredNodes.map { node ->
                            val latLng =
                                LatLng(
                                    (node.position.latitude_i ?: 0) * DEG_D,
                                    (node.position.longitude_i ?: 0) * DEG_D,
                                )
                            NodeClusterItem(
                                node = node,
                                nodePosition = latLng,
                                nodeTitle = "${node.user.short_name} ${formatAgo(node.position.time)}",
                                nodeSnippet = "${node.user.long_name}",
                                myNodeNum = myNodeNum,
                            )
                        },
                        mapFilterState = mapFilterState,
                        navigateToNodeDetails = navigateToNodeDetails,
                        displayableWaypoints = displayableWaypoints,
                        myNodeNum = myNodeNum,
                        isConnected = isConnected,
                        onEditWaypointRequest = { editingWaypoint = it },
                        selectedWaypointId = selectedWaypointId,
                        mapLayers = mapLayers,
                        mapViewModel = mapViewModel,
                        cameraPositionState = cameraPositionState,
                        coroutineScope = coroutineScope,
                        onShowClusterItemsDialog = { showClusterItemsDialog = it },
                    )

                is GoogleMapMode.NodeTrack -> {
                    val displayUnits by mapViewModel.displayUnits.collectAsStateWithLifecycle()
                    if (mode.focusedNode != null && sortedTrackPositions.isNotEmpty()) {
                        NodeTrackOverlay(
                            focusedNode = mode.focusedNode,
                            sortedPositions = sortedTrackPositions,
                            displayUnits = displayUnits,
                            myNodeNum = myNodeNum,
                            selectedPositionTime = mode.selectedPositionTime,
                            onPositionSelected = mode.onPositionSelected,
                        )
                    }
                }

                is GoogleMapMode.Traceroute ->
                    TracerouteMapContent(
                        forwardOffsetPoints = tracerouteForwardOffsetPoints,
                        returnOffsetPoints = tracerouteReturnOffsetPoints,
                        forwardPointCount = tracerouteForwardPoints.size,
                        returnPointCount = tracerouteReturnPoints.size,
                        displayNodes = tracerouteDisplayNodes,
                    )
            }
        }

        // Scale bar
        ScaleBar(
            cameraPositionState = cameraPositionState,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = if (isMainMode) 48.dp else 16.dp),
        )

        // Waypoint edit dialog (Main mode only)
        if (isMainMode) {
            editingWaypoint?.let { waypointToEdit ->
                EditWaypointDialog(
                    waypoint = waypointToEdit,
                    onSendClicked = { updatedWp ->
                        var finalWp = updatedWp
                        if (updatedWp.id == 0) {
                            finalWp = finalWp.copy(id = mapViewModel.generatePacketId())
                        }
                        if (updatedWp.icon == 0) {
                            finalWp = finalWp.copy(icon = 0x1F4CD)
                        }
                        mapViewModel.sendWaypoint(finalWp)
                        editingWaypoint = null
                    },
                    onDeleteClicked = { wpToDelete ->
                        if (wpToDelete.locked_to == 0 && isConnected && wpToDelete.id != 0) {
                            mapViewModel.sendWaypoint(wpToDelete.copy(expire = 1))
                        }
                        mapViewModel.deleteWaypoint(wpToDelete.id)
                        editingWaypoint = null
                    },
                    onDismissRequest = { editingWaypoint = null },
                )
            }
        }

        // Controls overlay
        val visibleNetworkLayers = mapLayers.filter { it.isNetwork && it.isVisible }
        val showRefresh = visibleNetworkLayers.isNotEmpty()
        val isRefreshingLayers = visibleNetworkLayers.any { it.isRefreshing }

        MapControlsOverlay(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            onToggleFilterMenu = { mapFilterMenuExpanded = true },
            filterDropdownContent = {
                if (mode is GoogleMapMode.NodeTrack) {
                    NodeMapFilterDropdown(
                        expanded = mapFilterMenuExpanded,
                        onDismissRequest = { mapFilterMenuExpanded = false },
                        mapViewModel = mapViewModel,
                    )
                } else {
                    MapFilterDropdown(
                        expanded = mapFilterMenuExpanded,
                        onDismissRequest = { mapFilterMenuExpanded = false },
                        mapViewModel = mapViewModel,
                    )
                }
            },
            mapTypeContent = {
                Box {
                    MapButton(
                        icon = MeshtasticIcons.Map,
                        contentDescription = stringResource(Res.string.map_tile_source),
                        onClick = { mapTypeMenuExpanded = true },
                    )
                    MapTypeDropdown(
                        expanded = mapTypeMenuExpanded,
                        onDismissRequest = { mapTypeMenuExpanded = false },
                        mapViewModel = mapViewModel,
                        onManageCustomTileProvidersClicked = {
                            mapTypeMenuExpanded = false
                            showCustomTileManagerSheet = true
                        },
                    )
                }
            },
            layersContent = {
                MapButton(
                    icon = MeshtasticIcons.Layers,
                    contentDescription = stringResource(Res.string.manage_map_layers),
                    onClick = { showLayersBottomSheet = true },
                )
            },
            isLocationTrackingEnabled = isLocationTrackingEnabled,
            onToggleLocationTracking = {
                if (locationPermissionsState.allPermissionsGranted) {
                    isLocationTrackingEnabled = !isLocationTrackingEnabled
                    if (!isLocationTrackingEnabled) {
                        followPhoneBearing = false
                    }
                } else {
                    triggerLocationToggleAfterPermission = true
                    locationPermissionsState.launchMultiplePermissionRequest()
                }
            },
            bearing = cameraPositionState.position.bearing,
            onCompassClick = {
                if (isLocationTrackingEnabled) {
                    followPhoneBearing = !followPhoneBearing
                } else {
                    coroutineScope.launch {
                        try {
                            val currentPosition = cameraPositionState.position
                            val newCameraPosition = CameraPosition.Builder(currentPosition).bearing(0f).build()
                            cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(newCameraPosition))
                            Logger.d { "Oriented map to north" }
                        } catch (e: IllegalStateException) {
                            Logger.d { "Error orienting map to north: ${e.message}" }
                        }
                    }
                }
            },
            followPhoneBearing = followPhoneBearing,
            showRefresh = showRefresh,
            isRefreshing = isRefreshingLayers,
            onRefresh = { mapViewModel.refreshAllVisibleNetworkLayers() },
        )
    }

    // --- Bottom sheets & dialogs ---
    if (showLayersBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showLayersBottomSheet = false }) {
            CustomMapLayersSheet(
                mapLayers = mapLayers,
                onToggleVisibility = onToggleVisibility,
                onRemoveLayer = onRemoveLayer,
                onAddLayerClicked = onAddLayerClicked,
                onRefreshLayer = { mapViewModel.refreshMapLayer(it) },
                onAddNetworkLayer = { name, url -> mapViewModel.addNetworkMapLayer(name, url) },
            )
        }
    }
    showClusterItemsDialog?.let {
        ClusterItemsListDialog(
            items = it,
            onDismiss = { showClusterItemsDialog = null },
            onItemClick = { item ->
                navigateToNodeDetails(item.node.num)
                showClusterItemsDialog = null
            },
        )
    }
    if (showCustomTileManagerSheet) {
        ModalBottomSheet(onDismissRequest = { showCustomTileManagerSheet = false }) {
            CustomTileProviderManagerSheet(mapViewModel = mapViewModel)
        }
    }
}

// region --- Main Map Content ---

@Suppress("LongParameterList")
@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun MainMapContent(
    nodeClusterItems: List<NodeClusterItem>,
    mapFilterState: MapFilterState,
    navigateToNodeDetails: (Int) -> Unit,
    displayableWaypoints: List<Waypoint>,
    myNodeNum: Int?,
    isConnected: Boolean,
    onEditWaypointRequest: (Waypoint) -> Unit,
    selectedWaypointId: Int?,
    mapLayers: List<MapLayerItem>,
    mapViewModel: MapViewModel,
    cameraPositionState: CameraPositionState,
    coroutineScope: CoroutineScope,
    onShowClusterItemsDialog: (List<NodeClusterItem>?) -> Unit,
) {
    NodeClusterMarkers(
        nodeClusterItems = nodeClusterItems,
        mapFilterState = mapFilterState,
        navigateToNodeDetails = navigateToNodeDetails,
        onClusterClick = { cluster ->
            val items = cluster.items.toList()
            val allSameLocation = items.size > 1 && items.all { it.position == items.first().position }
            if (allSameLocation) {
                onShowClusterItemsDialog(items)
            } else {
                val bounds = LatLngBounds.builder()
                cluster.items.forEach { bounds.include(it.position) }
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(bounds.build().center)
                                .zoom(cameraPositionState.position.zoom + 1)
                                .build(),
                        ),
                    )
                }
                Logger.d { "Cluster clicked! $cluster" }
            }
            true
        },
    )

    WaypointMarkers(
        displayableWaypoints = displayableWaypoints,
        mapFilterState = mapFilterState,
        myNodeNum = myNodeNum ?: 0,
        isConnected = isConnected,
        onEditWaypointRequest = onEditWaypointRequest,
        selectedWaypointId = selectedWaypointId,
    )

    mapLayers.forEach { layerItem -> key(layerItem.id) { MapLayerOverlay(layerItem, mapViewModel) } }
}

// endregion

// region --- Node Track Overlay ---

/**
 * Renders the position track polyline segments and markers inside a [GoogleMap] content scope. Each marker fades from
 * transparent (oldest) to opaque (newest). The newest position shows the node's [NodeChip]; older positions show a
 * [TripOrigin] dot with an info-window on tap.
 *
 * When [selectedPositionTime] matches a marker's `Position.time`, that marker is highlighted with the primary color and
 * elevated z-index. Tapping a marker invokes [onPositionSelected] for list synchronization.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
@Suppress("LongMethod")
private fun NodeTrackOverlay(
    focusedNode: Node,
    sortedPositions: List<Position>,
    displayUnits: DisplayUnits,
    myNodeNum: Int?,
    selectedPositionTime: Int? = null,
    onPositionSelected: ((Int) -> Unit)? = null,
) {
    val isHighPriority = focusedNode.num == myNodeNum || focusedNode.isFavorite
    val activeNodeZIndex = if (isHighPriority) 5f else 4f
    val selectedColor = MaterialTheme.colorScheme.primary

    sortedPositions.forEachIndexed { index, position ->
        key(position.time) {
            val markerState = rememberUpdatedMarkerState(position = position.toLatLng())
            val alpha =
                if (sortedPositions.size > 1) {
                    index.toFloat() / (sortedPositions.size.toFloat() - 1)
                } else {
                    1f
                }
            val isSelected = position.time == selectedPositionTime
            val color =
                if (isSelected) {
                    selectedColor
                } else {
                    Color(focusedNode.colors.second).copy(alpha = alpha)
                }

            if (index == sortedPositions.lastIndex) {
                MarkerComposable(
                    state = markerState,
                    zIndex = activeNodeZIndex,
                    alpha = if (isHighPriority) 1.0f else 0.9f,
                    onClick = {
                        onPositionSelected?.invoke(position.time)
                        false // Allow default info window behavior
                    },
                ) {
                    NodeChip(node = focusedNode)
                }
            } else {
                MarkerInfoWindowComposable(
                    state = markerState,
                    title = stringResource(Res.string.position),
                    snippet = formatAgo(position.time),
                    zIndex = if (isSelected) activeNodeZIndex - 0.5f else 1f + alpha,
                    onClick = {
                        onPositionSelected?.invoke(position.time)
                        false // Allow default info window behavior
                    },
                    infoContent = { PositionInfoWindowContent(position = position, displayUnits = displayUnits) },
                ) {
                    Icon(
                        imageVector = MeshtasticIcons.TripOrigin,
                        contentDescription = stringResource(Res.string.track_point),
                        tint = color,
                        modifier = if (isSelected) Modifier.size(32.dp) else Modifier,
                    )
                }
            }
        }
    }

    // Gradient polyline segments
    if (sortedPositions.size > 1) {
        val segments = sortedPositions.windowed(size = 2, step = 1, partialWindows = false)
        segments.forEachIndexed { index, segmentPoints ->
            val alpha = index.toFloat() / (segments.size.toFloat() - 1)
            Polyline(
                points = segmentPoints.map { it.toLatLng() },
                jointType = JointType.ROUND,
                color = Color(focusedNode.colors.second).copy(alpha = alpha),
                width = 8f,
                zIndex = 0.6f,
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun PositionInfoWindowContent(position: Position, displayUnits: DisplayUnits = DisplayUnits.METRIC) {
    @Composable
    fun PositionRow(label: String, value: String) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
    }

    Card {
        Column(modifier = Modifier.padding(8.dp)) {
            PositionRow(
                label = stringResource(Res.string.latitude),
                value = "%.5f".format((position.latitude_i ?: 0) * DEG_D),
            )
            PositionRow(
                label = stringResource(Res.string.longitude),
                value = "%.5f".format((position.longitude_i ?: 0) * DEG_D),
            )
            PositionRow(label = stringResource(Res.string.sats), value = position.sats_in_view.toString())
            PositionRow(
                label = stringResource(Res.string.alt),
                value = (position.altitude ?: 0).metersIn(displayUnits).toString(displayUnits),
            )
            PositionRow(label = stringResource(Res.string.speed), value = speedFromPosition(position, displayUnits))
            PositionRow(
                label = stringResource(Res.string.heading),
                value = "%.0f°".format((position.ground_track ?: 0) * HEADING_DEG),
            )
            PositionRow(label = stringResource(Res.string.timestamp), value = position.formatPositionTime())
        }
    }
}

@Composable
private fun speedFromPosition(position: Position, displayUnits: DisplayUnits): String {
    val speedInMps = position.ground_speed ?: 0
    val mpsText = "%d m/s".format(speedInMps)
    return if (speedInMps > 10) {
        when (displayUnits) {
            DisplayUnits.METRIC -> "%.1f Km/h".format(speedInMps.mpsToKmph())
            DisplayUnits.IMPERIAL -> "%.1f mph".format(speedInMps.mpsToMph())
        }
    } else {
        mpsText
    }
}

// endregion

// region --- Traceroute Map Content ---

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun TracerouteMapContent(
    forwardOffsetPoints: List<LatLng>,
    returnOffsetPoints: List<LatLng>,
    forwardPointCount: Int,
    returnPointCount: Int,
    displayNodes: List<Node>,
) {
    if (forwardPointCount >= 2) {
        Polyline(
            points = forwardOffsetPoints,
            jointType = JointType.ROUND,
            color = TracerouteColors.OutgoingRoute,
            width = 9f,
            zIndex = 3.0f,
        )
    }
    if (returnPointCount >= 2) {
        Polyline(
            points = returnOffsetPoints,
            jointType = JointType.ROUND,
            color = TracerouteColors.ReturnRoute,
            width = 7f,
            zIndex = 2.5f,
        )
    }
    displayNodes.forEach { node ->
        val markerState = rememberUpdatedMarkerState(position = node.position.toLatLng())
        MarkerComposable(state = markerState, zIndex = 4f) { NodeChip(node = node) }
    }
}

private fun offsetPolyline(
    points: List<LatLng>,
    offsetMeters: Double,
    headingReferencePoints: List<LatLng> = points,
    sideMultiplier: Double = 1.0,
): List<LatLng> {
    val headingPoints = headingReferencePoints.takeIf { it.size >= 2 } ?: points
    if (points.size < 2 || headingPoints.size < 2 || offsetMeters == 0.0) return points

    val headings =
        headingPoints.mapIndexed { index, _ ->
            when (index) {
                0 -> SphericalUtil.computeHeading(headingPoints[0], headingPoints[1])

                headingPoints.lastIndex ->
                    SphericalUtil.computeHeading(
                        headingPoints[headingPoints.lastIndex - 1],
                        headingPoints[headingPoints.lastIndex],
                    )

                else -> SphericalUtil.computeHeading(headingPoints[index - 1], headingPoints[index + 1])
            }
        }

    return points.mapIndexed { index, point ->
        val heading = headings[index.coerceIn(0, headings.lastIndex)]
        val perpendicularHeading = heading + (90.0 * sideMultiplier)
        SphericalUtil.computeOffset(point, abs(offsetMeters), perpendicularHeading)
    }
}

// endregion

// region --- Map Layers ---

@Composable
private fun MapLayerOverlay(layerItem: MapLayerItem, mapViewModel: MapViewModel) {
    val context = LocalContext.current
    var currentLayer by remember { mutableStateOf<Layer?>(null) }

    MapEffect(layerItem.id, layerItem.isRefreshing) { map ->
        currentLayer?.safeRemoveLayerFromMap()
        currentLayer = null
        val inputStream = mapViewModel.getInputStreamFromUri(layerItem) ?: return@MapEffect
        val layer =
            try {
                when (layerItem.layerType) {
                    LayerType.KML -> KmlLayer(map, inputStream, context)

                    LayerType.GEOJSON ->
                        GeoJsonLayer(map, JSONObject(inputStream.bufferedReader().use { it.readText() }))
                }
            } catch (e: Exception) {
                Logger.withTag("MapView").e(e) { "Error loading map layer: ${layerItem.name}" }
                null
            }
        layer?.let {
            if (layerItem.isVisible) it.safeAddLayerToMap()
            currentLayer = it
        }
    }

    DisposableEffect(layerItem.id) {
        onDispose {
            currentLayer?.safeRemoveLayerFromMap()
            currentLayer = null
        }
    }

    LaunchedEffect(layerItem.isVisible) {
        val layer = currentLayer ?: return@LaunchedEffect
        if (layerItem.isVisible) layer.safeAddLayerToMap() else layer.safeRemoveLayerFromMap()
    }
}

private fun Layer.safeRemoveLayerFromMap() {
    try {
        removeLayerFromMap()
    } catch (e: Exception) {
        Logger.withTag("MapView").e(e) { "Error removing map layer" }
    }
}

private fun Layer.safeAddLayerToMap() {
    try {
        if (!isLayerOnMap) addLayerToMap()
    } catch (e: Exception) {
        Logger.withTag("MapView").e(e) { "Error adding map layer" }
    }
}

// endregion

// region --- Utilities ---

internal fun convertIntToEmoji(unicodeCodePoint: Int): String = try {
    String(Character.toChars(unicodeCodePoint))
} catch (e: IllegalArgumentException) {
    Logger.w(e) { "Invalid unicode code point: $unicodeCodePoint" }
    "\uD83D\uDCCD"
}

@Suppress("NestedBlockDepth")
fun Uri.getFileName(context: android.content.Context): String {
    var name = this.lastPathSegment ?: "layer_$nowMillis"
    if (this.scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = cursor.getString(displayNameIndex)
                }
            }
        }
    }
    return name
}

/** Converts protobuf [Position] integer coordinates to a Google Maps [LatLng]. */
internal fun Position.toLatLng(): LatLng = LatLng((this.latitude_i ?: 0) * DEG_D, (this.longitude_i ?: 0) * DEG_D)

// endregion
