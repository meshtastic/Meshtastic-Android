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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.map

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.core.graphics.createBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.SphericalUtil
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
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.widgets.ScaleBar
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.mpsToKmph
import org.meshtastic.core.model.util.mpsToMph
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.alt
import org.meshtastic.core.strings.heading
import org.meshtastic.core.strings.latitude
import org.meshtastic.core.strings.longitude
import org.meshtastic.core.strings.position
import org.meshtastic.core.strings.sats
import org.meshtastic.core.strings.speed
import org.meshtastic.core.strings.timestamp
import org.meshtastic.core.strings.track_point
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.core.ui.util.formatPositionTime
import org.meshtastic.feature.map.component.ClusterItemsListDialog
import org.meshtastic.feature.map.component.CustomMapLayersSheet
import org.meshtastic.feature.map.component.CustomTileProviderManagerSheet
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.NodeClusterMarkers
import org.meshtastic.feature.map.component.WaypointMarkers
import org.meshtastic.feature.map.model.NodeClusterItem
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint
import kotlin.math.abs
import kotlin.math.max

private const val MIN_TRACK_POINT_DISTANCE_METERS = 20f
private const val DEG_D = 1e-7
private const val HEADING_DEG = 1e-5
private const val TRACEROUTE_OFFSET_METERS = 100.0
private const val TRACEROUTE_BOUNDS_PADDING_PX = 120

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapView(
    mapViewModel: MapViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
    focusedNodeNum: Int? = null,
    nodeTracks: List<Position>? = null,
    tracerouteOverlay: TracerouteOverlay? = null,
    tracerouteNodePositions: Map<Int, Position> = emptyMap(),
    onTracerouteMappableCountChanged: (shown: Int, total: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()
    var hasLocationPermission by remember { mutableStateOf(false) }
    val displayUnits by mapViewModel.displayUnits.collectAsStateWithLifecycle()

    // Location tracking state
    var isLocationTrackingEnabled by remember { mutableStateOf(false) }
    var followPhoneBearing by remember { mutableStateOf(false) }

    LocationPermissionsHandler { isGranted -> hasLocationPermission = isGranted }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val fileName = uri.getFileName(context)
                    mapViewModel.addMapLayer(uri, fileName)
                }
            }
        }

    var mapFilterMenuExpanded by remember { mutableStateOf(false) }
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val ourNodeInfo by mapViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }

    val selectedGoogleMapType by mapViewModel.selectedGoogleMapType.collectAsStateWithLifecycle()
    val currentCustomTileProviderUrl by mapViewModel.selectedCustomTileProviderUrl.collectAsStateWithLifecycle()

    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    var showCustomTileManagerSheet by remember { mutableStateOf(false) }

    val cameraPositionState = mapViewModel.cameraPositionState

    // Save camera position when it stops moving
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            mapViewModel.saveCameraPosition(cameraPositionState.position)
        }
    }

    // Location tracking functionality
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

    // Start/stop location tracking based on state
    LaunchedEffect(isLocationTrackingEnabled, hasLocationPermission) {
        if (isLocationTrackingEnabled && hasLocationPermission) {
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

    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            mapViewModel.clearLoadedLayerData()
        }
    }

    val allNodes by
        mapViewModel.nodes
            .map { nodes -> nodes.filter { node -> node.validPosition != null } }
            .collectAsStateWithLifecycle(listOf())
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())
    val displayableWaypoints = waypoints.values.mapNotNull { it.data.waypoint }
    val selectedWaypointId by mapViewModel.selectedWaypointId.collectAsStateWithLifecycle()

    val tracerouteSelection =
        remember(tracerouteOverlay, tracerouteNodePositions, allNodes) {
            mapViewModel.tracerouteNodeSelection(
                tracerouteOverlay = tracerouteOverlay,
                tracerouteNodePositions = tracerouteNodePositions,
                nodes = allNodes,
            )
        }

    val filteredNodes =
        allNodes
            .filter { node -> !mapFilterState.onlyFavorites || node.isFavorite || node.num == ourNodeInfo?.num }
            .filter { node ->
                mapFilterState.lastHeardFilter.seconds == 0L ||
                    (System.currentTimeMillis() / 1000 - node.lastHeard) <= mapFilterState.lastHeardFilter.seconds ||
                    node.num == ourNodeInfo?.num
            }

    val displayNodes =
        if (tracerouteOverlay != null) {
            tracerouteSelection.nodesForMarkers
        } else {
            filteredNodes
        }
    LaunchedEffect(tracerouteOverlay, displayNodes) {
        if (tracerouteOverlay != null) {
            onTracerouteMappableCountChanged(displayNodes.size, tracerouteOverlay.relatedNodeNums.size)
        }
    }

    val nodeClusterItems =
        displayNodes.map { node ->
            val latLng = LatLng((node.position.latitude_i ?: 0) * DEG_D, (node.position.longitude_i ?: 0) * DEG_D)
            NodeClusterItem(
                node = node,
                nodePosition = latLng,
                nodeTitle = "${node.user.short_name} ${formatAgo(node.position.time)}",
                nodeSnippet = "${node.user.long_name}",
            )
        }
    val isConnected by mapViewModel.isConnected.collectAsStateWithLifecycle()
    val theme by mapViewModel.theme.collectAsStateWithLifecycle()
    val dark =
        when (theme) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> isSystemInDarkTheme()
            else -> isSystemInDarkTheme()
        }
    val mapColorScheme =
        when (dark) {
            true -> ComposeMapColorScheme.DARK
            else -> ComposeMapColorScheme.LIGHT
        }
    val tracerouteForwardPoints =
        remember(tracerouteOverlay, displayNodes) {
            val nodeLookup = displayNodes.associateBy { it.num }
            tracerouteOverlay?.forwardRoute?.mapNotNull { nodeLookup[it]?.toLatLng() } ?: emptyList()
        }
    val tracerouteReturnPoints =
        remember(tracerouteOverlay, displayNodes) {
            val nodeLookup = displayNodes.associateBy { it.num }
            tracerouteOverlay?.returnRoute?.mapNotNull { nodeLookup[it]?.toLatLng() } ?: emptyList()
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
            offsetPolyline(
                points = tracerouteForwardPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = tracerouteHeadingReferencePoints,
                sideMultiplier = 1.0,
            )
        }
    val tracerouteReturnOffsetPoints =
        remember(tracerouteReturnPoints, tracerouteHeadingReferencePoints) {
            offsetPolyline(
                points = tracerouteReturnPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = tracerouteHeadingReferencePoints,
                sideMultiplier = -1.0,
            )
        }
    var hasCenteredTraceroute by remember(tracerouteOverlay) { mutableStateOf(false) }

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

    val effectiveGoogleMapType =
        if (currentCustomTileProviderUrl != null) {
            MapType.NONE
        } else {
            selectedGoogleMapType
        }

    var showClusterItemsDialog by remember { mutableStateOf<List<NodeClusterItem>?>(null) }

    LaunchedEffect(isLocationTrackingEnabled) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window

        if (isLocationTrackingEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(tracerouteOverlay, tracerouteForwardPoints, tracerouteReturnPoints) {
        if (tracerouteOverlay == null || hasCenteredTraceroute) return@LaunchedEffect
        val allPoints = (tracerouteForwardPoints + tracerouteReturnPoints).distinct()
        if (allPoints.isNotEmpty()) {
            val cameraUpdate =
                if (allPoints.size == 1) {
                    CameraUpdateFactory.newLatLngZoom(allPoints.first(), max(cameraPositionState.position.zoom, 12f))
                } else {
                    val bounds = LatLngBounds.builder()
                    allPoints.forEach { bounds.include(it) }
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), TRACEROUTE_BOUNDS_PADDING_PX)
                }
            try {
                cameraPositionState.animate(cameraUpdate)
                hasCenteredTraceroute = true
            } catch (e: IllegalStateException) {
                Logger.d { "Error centering traceroute overlay: ${e.message}" }
            }
        }
    }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            GoogleMap(
                mapColorScheme = mapColorScheme,
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings =
                MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = true,
                    compassEnabled = false,
                    myLocationButtonEnabled = false,
                    rotationGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = true,
                    zoomGesturesEnabled = true,
                ),
                properties =
                MapProperties(mapType = effectiveGoogleMapType, isMyLocationEnabled = hasLocationPermission),
                onMapLongClick = { latLng ->
                    if (isConnected) {
                        val newWaypoint =
                            Waypoint(
                                latitude_i = (latLng.latitude / DEG_D).toInt(),
                                longitude_i = (latLng.longitude / DEG_D).toInt(),
                            )
                        editingWaypoint = newWaypoint
                    }
                },
            ) {
                key(currentCustomTileProviderUrl) {
                    currentCustomTileProviderUrl?.let { url ->
                        mapViewModel.createUrlTileProvider(url)?.let { tileProvider ->
                            TileOverlay(tileProvider = tileProvider, fadeIn = true, transparency = 0f, zIndex = -1f)
                        }
                    }
                }

                if (tracerouteForwardPoints.size >= 2) {
                    Polyline(
                        points = tracerouteForwardOffsetPoints,
                        jointType = JointType.ROUND,
                        color = TracerouteColors.OutgoingRoute,
                        width = 9f,
                        zIndex = 1.5f,
                    )
                }
                if (tracerouteReturnPoints.size >= 2) {
                    Polyline(
                        points = tracerouteReturnOffsetPoints,
                        jointType = JointType.ROUND,
                        color = TracerouteColors.ReturnRoute,
                        width = 7f,
                        zIndex = 1.4f,
                    )
                }

                if (nodeTracks != null && focusedNodeNum != null) {
                    val lastHeardTrackFilter = mapFilterState.lastHeardTrackFilter
                    val timeFilteredPositions =
                        nodeTracks.filter {
                            lastHeardTrackFilter == LastHeardFilter.Any ||
                                it.time > System.currentTimeMillis() / 1000 - lastHeardTrackFilter.seconds
                        }
                    val sortedPositions = timeFilteredPositions.sortedBy { it.time }
                    allNodes
                        .find { it.num == focusedNodeNum }
                        ?.let { focusedNode ->
                            sortedPositions.forEachIndexed { index, position ->
                                val markerState = rememberUpdatedMarkerState(position = position.toLatLng())
                                val alpha = (index.toFloat() / (sortedPositions.size.toFloat() - 1))
                                val color = Color(focusedNode.colors.second).copy(alpha = alpha)
                                if (index == sortedPositions.lastIndex) {
                                    MarkerComposable(state = markerState, zIndex = 1f) { NodeChip(node = focusedNode) }
                                } else {
                                    MarkerInfoWindowComposable(
                                        state = markerState,
                                        title = stringResource(Res.string.position),
                                        snippet = formatAgo(position.time),
                                        zIndex = alpha,
                                        infoContent = {
                                            PositionInfoWindowContent(position = position, displayUnits = displayUnits)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Rounded.TripOrigin,
                                            contentDescription = stringResource(Res.string.track_point),
                                            tint = color,
                                        )
                                    }
                                }
                            }

                            if (sortedPositions.size > 1) {
                                val segments = sortedPositions.windowed(size = 2, step = 1, partialWindows = false)
                                segments.forEachIndexed { index, segmentPoints ->
                                    val alpha = (index.toFloat() / (segments.size.toFloat() - 1))
                                    Polyline(
                                        points = segmentPoints.map { it.toLatLng() },
                                        jointType = JointType.ROUND,
                                        color = Color(focusedNode.colors.second).copy(alpha = alpha),
                                        width = 8f,
                                    )
                                }
                            }
                        }
                } else {
                    NodeClusterMarkers(
                        nodeClusterItems = nodeClusterItems,
                        mapFilterState = mapFilterState,
                        navigateToNodeDetails = navigateToNodeDetails,
                        onClusterClick = { cluster ->
                            val items = cluster.items.toList()
                            val allSameLocation = items.size > 1 && items.all { it.position == items.first().position }

                            if (allSameLocation) {
                                showClusterItemsDialog = items
                            } else {
                                val bounds = LatLngBounds.builder()
                                cluster.items.forEach { bounds.include(it.position) }
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngBounds(bounds.build(), 100),
                                    )
                                }
                                Logger.d { "Cluster clicked! $cluster" }
                            }
                            true
                        },
                    )
                }

                if (tracerouteForwardPoints.size >= 2) {
                    Polyline(
                        points = tracerouteForwardOffsetPoints,
                        jointType = JointType.ROUND,
                        color = TracerouteColors.OutgoingRoute,
                        width = 9f,
                        zIndex = 2f,
                    )
                }
                if (tracerouteReturnPoints.size >= 2) {
                    Polyline(
                        points = tracerouteReturnOffsetPoints,
                        jointType = JointType.ROUND,
                        color = TracerouteColors.ReturnRoute,
                        width = 7f,
                        zIndex = 1.5f,
                    )
                }

                WaypointMarkers(
                    displayableWaypoints = displayableWaypoints,
                    mapFilterState = mapFilterState,
                    myNodeNum = mapViewModel.myNodeNum ?: 0,
                    isConnected = isConnected,
                    unicodeEmojiToBitmapProvider = ::unicodeEmojiToBitmap,
                    onEditWaypointRequest = { waypointToEdit -> editingWaypoint = waypointToEdit },
                    selectedWaypointId = selectedWaypointId,
                )

                MapEffect(mapLayers) { map ->
                    mapLayers.forEach { layerItem ->
                        coroutineScope.launch {
                            mapViewModel.loadMapLayerIfNeeded(map, layerItem)
                            when (layerItem.layerType) {
                                LayerType.KML -> {
                                    layerItem.kmlLayerData?.let { kmlLayer ->
                                        if (layerItem.isVisible && !kmlLayer.isLayerOnMap) {
                                            kmlLayer.addLayerToMap()
                                        } else if (!layerItem.isVisible && kmlLayer.isLayerOnMap) {
                                            kmlLayer.removeLayerFromMap()
                                        }
                                    }
                                }

                                LayerType.GEOJSON -> {
                                    layerItem.geoJsonLayerData?.let { geoJsonLayer ->
                                        if (layerItem.isVisible && !geoJsonLayer.isLayerOnMap) {
                                            geoJsonLayer.addLayerToMap()
                                        } else if (!layerItem.isVisible && geoJsonLayer.isLayerOnMap) {
                                            geoJsonLayer.removeLayerFromMap()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ScaleBar(
                cameraPositionState = cameraPositionState,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 48.dp),
            )
            editingWaypoint?.let { waypointToEdit ->
                EditWaypointDialog(
                    waypoint = waypointToEdit,
                    onSendClicked = { updatedWp ->
                        var finalWp = updatedWp
                        if (updatedWp.id == 0) {
                            finalWp = finalWp.copy(id = mapViewModel.generatePacketId() ?: 0)
                        }
                        if ((updatedWp.icon ?: 0) == 0) {
                            finalWp = finalWp.copy(icon = 0x1F4CD)
                        }

                        mapViewModel.sendWaypoint(finalWp)
                        editingWaypoint = null
                    },
                    onDeleteClicked = { wpToDelete ->
                        if ((wpToDelete.locked_to ?: 0) == 0 && isConnected && wpToDelete.id != 0) {
                            val deleteMarkerWp = wpToDelete.copy(expire = 1)
                            mapViewModel.sendWaypoint(deleteMarkerWp)
                        }
                        mapViewModel.deleteWaypoint(wpToDelete.id)
                        editingWaypoint = null
                    },
                    onDismissRequest = { editingWaypoint = null },
                )
            }

            MapControlsOverlay(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                mapFilterMenuExpanded = mapFilterMenuExpanded,
                onMapFilterMenuDismissRequest = { mapFilterMenuExpanded = false },
                onToggleMapFilterMenu = { mapFilterMenuExpanded = true },
                mapViewModel = mapViewModel,
                mapTypeMenuExpanded = mapTypeMenuExpanded,
                onMapTypeMenuDismissRequest = { mapTypeMenuExpanded = false },
                onToggleMapTypeMenu = { mapTypeMenuExpanded = true },
                onManageLayersClicked = { showLayersBottomSheet = true },
                onManageCustomTileProvidersClicked = {
                    mapTypeMenuExpanded = false
                    showCustomTileManagerSheet = true
                },
                isNodeMap = focusedNodeNum != null,
                hasLocationPermission = hasLocationPermission,
                isLocationTrackingEnabled = isLocationTrackingEnabled,
                onToggleLocationTracking = {
                    if (hasLocationPermission) {
                        isLocationTrackingEnabled = !isLocationTrackingEnabled
                        if (!isLocationTrackingEnabled) {
                            followPhoneBearing = false
                        }
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
            )
        }
    }
    if (showLayersBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showLayersBottomSheet = false }) {
            CustomMapLayersSheet(mapLayers, onToggleVisibility, onRemoveLayer, onAddLayerClicked)
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

internal fun convertIntToEmoji(unicodeCodePoint: Int): String = try {
    String(Character.toChars(unicodeCodePoint))
} catch (e: IllegalArgumentException) {
    Logger.w(e) { "Invalid unicode code point: $unicodeCodePoint" }
    "\uD83D\uDCCD"
}

internal fun unicodeEmojiToBitmap(icon: Int): BitmapDescriptor {
    val unicodeEmoji = convertIntToEmoji(icon)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
        }

    val baseline = -paint.ascent()
    val width = (paint.measureText(unicodeEmoji) + 0.5f).toInt()
    val height = (baseline + paint.descent() + 0.5f).toInt()
    val image = createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawText(unicodeEmoji, width / 2f, baseline, paint)

    return BitmapDescriptorFactory.fromBitmap(image)
}

@Suppress("NestedBlockDepth")
fun Uri.getFileName(context: android.content.Context): String {
    var name = this.lastPathSegment ?: "layer_${System.currentTimeMillis()}"
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongMethod")
private fun PositionInfoWindowContent(position: Position, displayUnits: DisplayUnits = DisplayUnits.METRIC) {
    @Composable
    fun PositionRow(label: String, value: String) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.labelMediumEmphasized)
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

            PositionRow(label = stringResource(Res.string.sats), value = position.sats_in_view?.toString() ?: "")

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
    val speedText =
        if (speedInMps > 10) {
            when (displayUnits) {
                DisplayUnits.METRIC -> "%.1f Km/h".format(speedInMps.mpsToKmph())
                DisplayUnits.IMPERIAL -> "%.1f mph".format(speedInMps.mpsToMph())
                else -> mpsText // Fallback or handle UNRECOGNIZED
            }
        } else {
            mpsText
        }
    return speedText
}

internal fun Position.toLatLng(): LatLng = LatLng((this.latitude_i ?: 0) * DEG_D, (this.longitude_i ?: 0) * DEG_D)

private fun Node.toLatLng(): LatLng? = this.position.toLatLng()

private fun Waypoint.toLatLng(): LatLng = LatLng((this.latitude_i ?: 0) * DEG_D, (this.longitude_i ?: 0) * DEG_D)

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
