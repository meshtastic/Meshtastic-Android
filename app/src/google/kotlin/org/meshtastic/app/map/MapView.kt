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

package org.meshtastic.app.map

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.widgets.ScaleBar
import com.google.maps.android.data.geojson.GeoJsonLayer
import com.google.maps.android.data.kml.KmlLayer
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.component.ClusterItemsListDialog
import org.meshtastic.app.map.component.CustomMapLayersSheet
import org.meshtastic.app.map.component.CustomTileProviderManagerSheet
import org.meshtastic.app.map.component.EditWaypointDialog
import org.meshtastic.app.map.component.MapControlsOverlay
import org.meshtastic.app.map.component.NodeClusterMarkers
import org.meshtastic.app.map.component.WaypointMarkers
import org.meshtastic.app.map.model.NodeClusterItem
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = koinViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()

    // Location permissions state
    val locationPermissionsState =
        rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    var triggerLocationToggleAfterPermission by remember { mutableStateOf(false) }

    // Location tracking state
    var isLocationTrackingEnabled by remember { mutableStateOf(false) }
    var followPhoneBearing by remember { mutableStateOf(false) }

    // Effect to toggle location tracking after permission is granted
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted && triggerLocationToggleAfterPermission) {
            isLocationTrackingEnabled = true
            triggerLocationToggleAfterPermission = false
        }
    }

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
    val nodeClusterItems =
        filteredNodes.map { node ->
            val latLng = LatLng((node.position.latitude_i ?: 0) * DEG_D, (node.position.longitude_i ?: 0) * DEG_D)
            NodeClusterItem(
                node = node,
                nodePosition = latLng,
                nodeTitle = "${node.user.short_name} ${formatAgo(node.position.time)}",
                nodeSnippet = "${node.user.long_name}",
                myNodeNum = myNodeNum,
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

    Box(modifier = modifier) {
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
            MapProperties(
                mapType = effectiveGoogleMapType,
                isMyLocationEnabled = isLocationTrackingEnabled && locationPermissionsState.allPermissionsGranted,
            ),
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
                    val config =
                        mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle().value.find {
                            it.urlTemplate == url || it.localUri == url
                        }
                    mapViewModel.getTileProvider(config)?.let { tileProvider ->
                        TileOverlay(tileProvider = tileProvider, fadeIn = true, transparency = 0f, zIndex = -1f)
                    }
                }
            }

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
                myNodeNum = mapViewModel.myNodeNum ?: 0,
                isConnected = isConnected,
                unicodeEmojiToBitmapProvider = ::unicodeEmojiToBitmap,
                onEditWaypointRequest = { waypointToEdit -> editingWaypoint = waypointToEdit },
                selectedWaypointId = selectedWaypointId,
            )

            mapLayers.forEach { layerItem -> key(layerItem.id) { MapLayerOverlay(layerItem, mapViewModel) } }
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

        val visibleNetworkLayers = mapLayers.filter { it.isNetwork && it.isVisible }
        val showRefresh = visibleNetworkLayers.isNotEmpty()
        val isRefreshingLayers = visibleNetworkLayers.any { it.isRefreshing }

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

@Composable
private fun MapLayerOverlay(layerItem: MapLayerItem, mapViewModel: MapViewModel) {
    val context = LocalContext.current
    var currentLayer by remember { mutableStateOf<com.google.maps.android.data.Layer?>(null) }

    MapEffect(layerItem.id, layerItem.isRefreshing) { map ->
        // Cleanup old layer if we're reloading
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
            if (layerItem.isVisible) {
                it.safeAddLayerToMap()
            }
            currentLayer = it
        }
    }

    DisposableEffect(layerItem.id) {
        onDispose {
            currentLayer?.safeRemoveLayerFromMap()
            currentLayer = null
        }
    }

    // Handle visibility changes without reloading the whole layer if possible,
    // though KmlLayer.addLayerToMap() / removeLayerFromMap() is what we have.
    LaunchedEffect(layerItem.isVisible) {
        val layer = currentLayer ?: return@LaunchedEffect
        if (layerItem.isVisible) {
            layer.safeAddLayerToMap()
        } else {
            layer.safeRemoveLayerFromMap()
        }
    }
}

private fun com.google.maps.android.data.Layer.safeRemoveLayerFromMap() {
    try {
        removeLayerFromMap()
    } catch (e: Exception) {
        // Log it and ignore. This specifically handles a NullPointerException in
        // KmlRenderer.hasNestedContainers which can occur when disposing layers.
        Logger.withTag("MapView").e(e) { "Error removing map layer" }
    }
}

private fun com.google.maps.android.data.Layer.safeAddLayerToMap() {
    try {
        if (!isLayerOnMap) {
            addLayerToMap()
        }
    } catch (e: Exception) {
        Logger.withTag("MapView").e(e) { "Error adding map layer" }
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

private fun Waypoint.toLatLng(): LatLng = LatLng((this.latitude_i ?: 0) * DEG_D, (this.longitude_i ?: 0) * DEG_D)
