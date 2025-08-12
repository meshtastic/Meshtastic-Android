/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.map

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarExitDirection.Companion.End
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberFloatingToolbarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.components.ClusterItemsListDialog
import com.geeksville.mesh.ui.map.components.CustomMapLayersSheet
import com.geeksville.mesh.ui.map.components.CustomTileProviderManagerSheet
import com.geeksville.mesh.ui.map.components.EditWaypointDialog
import com.geeksville.mesh.ui.map.components.MapControlsOverlay
import com.geeksville.mesh.ui.map.components.NodeClusterMarkers
import com.geeksville.mesh.ui.map.components.WaypointMarkers
import com.geeksville.mesh.ui.node.DEG_D
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.waypoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.widgets.DisappearingScaleBar
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapView(
    uiViewModel: UIViewModel,
    mapViewModel: MapViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
    focusedNodeNum: Int? = null,
    nodeTrack: List<MeshProtos.Position>? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()
    var hasLocationPermission by remember { mutableStateOf(false) }

    LocationPermissionsHandler { isGranted -> hasLocationPermission = isGranted }

    val kmlFilePickerLauncher =
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
    val ourNodeInfo by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var editingWaypoint by remember { mutableStateOf<MeshProtos.Waypoint?>(null) }

    // Selected Google Map type from ViewModel
    val selectedGoogleMapType by mapViewModel.selectedGoogleMapType.collectAsStateWithLifecycle()
    // Selected custom tile provider URL from ViewModel
    val currentCustomTileProviderUrl by mapViewModel.selectedCustomTileProviderUrl.collectAsStateWithLifecycle()

    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    var showCustomTileManagerSheet by remember { mutableStateOf(false) } // State for bottom sheet

    val defaultLatLng = LatLng(40.7871508066057, -119.2041344866371)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 7f)
    }

    val floatingToolbarState = rememberFloatingToolbarState()
    val exitAlwaysScrollBehavior =
        FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = End, state = floatingToolbarState)

    LaunchedEffect(cameraPositionState.isMoving, floatingToolbarState.offsetLimit) {
        val targetOffset =
            if (cameraPositionState.isMoving) {
                // Hide: Use the offsetLimit (which is typically negative or zero).
                // If heightOffsetLimit is 0f (toolbar not measured yet or has no height),
                // this will effectively mean "don't move yet" if current offset is also 0.
                floatingToolbarState.offsetLimit
            } else {
                // Show: Offset is 0f
                0f
            }
        if (floatingToolbarState.offset != targetOffset) {
            if (targetOffset == 0f || floatingToolbarState.offsetLimit != 0f) {
                launch {
                    animate(initialValue = floatingToolbarState.offset, targetValue = targetOffset) { value, _ ->
                        floatingToolbarState.offset = value
                    }
                }
            }
        }
    }

    val allNodes by
        mapViewModel.nodes
            .map { nodes -> nodes.filter { node -> node.validPosition != null } }
            .collectAsStateWithLifecycle(listOf())
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())
    val displayableWaypoints = waypoints.values.mapNotNull { it.data.waypoint }

    // State to track if the initial camera zoom has happened
    var hasZoomed by rememberSaveable { mutableStateOf(false) }

    // Effect to zoom to bounds of all items when map is first loaded
    LaunchedEffect(allNodes, displayableWaypoints, nodeTrack) {
        if (!hasZoomed) {
            if (nodeTrack != null && nodeTrack.isNotEmpty()) {
                val latLngBounds =
                    LatLngBounds.builder()
                        .apply { nodeTrack.forEach { include(LatLng(it.latitudeI * DEG_D, it.longitudeI * DEG_D)) } }
                        .build()
                coroutineScope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(latLngBounds, 100))
                }
                hasZoomed = true
            } else if (allNodes.isNotEmpty() || displayableWaypoints.isNotEmpty()) {
                val boundsBuilder = LatLngBounds.builder()
                allNodes.forEach { node ->
                    boundsBuilder.include(LatLng(node.position.latitudeI * DEG_D, node.position.longitudeI * DEG_D))
                }
                displayableWaypoints.forEach { waypoint ->
                    boundsBuilder.include(LatLng(waypoint.latitudeI * DEG_D, waypoint.longitudeI * DEG_D))
                }
                try {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                    hasZoomed = true // Ensure this runs only once
                } catch (e: IllegalStateException) {
                    // Ignore cases where the bounds are empty or otherwise invalid
                    Log.w("MapView", "Could not animate to bounds: ${e.message}")
                }
            }
        }
    }

    val filteredNodes =
        if (mapFilterState.onlyFavorites) {
            allNodes.filter { it.isFavorite || it.num == ourNodeInfo?.num }
        } else {
            allNodes
        }

    val nodeClusterItems =
        filteredNodes.map { node ->
            val latLng = LatLng(node.position.latitudeI * DEG_D, node.position.longitudeI * DEG_D)
            NodeClusterItem(
                node = node,
                nodePosition = latLng,
                nodeTitle = "${node.user.shortName} ${formatAgo(node.position.time)}",
                nodeSnippet = "${node.user.longName}",
            )
        }
    val isConnected by uiViewModel.isConnectedStateFlow.collectAsStateWithLifecycle()
    val theme by uiViewModel.theme.collectAsStateWithLifecycle()
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
                val mimeTypes = arrayOf("application/vnd.google-earth.kml+xml", "application/vnd.google-earth.kmz")
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
        kmlFilePickerLauncher.launch(intent)
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

    Scaffold(modifier = Modifier.nestedScroll(exitAlwaysScrollBehavior)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            GoogleMap(
                mapColorScheme = mapColorScheme,
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings =
                MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = true,
                    compassEnabled = true,
                    myLocationButtonEnabled = hasLocationPermission,
                    rotationGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = true,
                    zoomGesturesEnabled = true,
                ),
                properties =
                MapProperties(mapType = effectiveGoogleMapType, isMyLocationEnabled = hasLocationPermission),
                onMapLongClick = { latLng ->
                    if (isConnected) {
                        val newWaypoint = waypoint {
                            latitudeI = (latLng.latitude / DEG_D).toInt()
                            longitudeI = (latLng.longitude / DEG_D).toInt()
                        }
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

                if (nodeTrack != null && focusedNodeNum != null) {
                    val latLngs = nodeTrack.map { LatLng(it.latitudeI * DEG_D, it.longitudeI * DEG_D) }
                    val focusedNode = allNodes.find { it.num == focusedNodeNum }
                    val polylineColor = focusedNode?.colors?.let { Color(it.first) } ?: Color.Blue

                    latLngs.forEachIndexed { index, latLng ->
                        if (index == latLngs.lastIndex) {
                            focusedNode?.let {
                                MarkerComposable(state = rememberUpdatedMarkerState(position = latLng)) {
                                    NodeChip(node = it, isThisNode = false, isConnected = false, onAction = {})
                                }
                            }
                        } else {
                            Marker(
                                state = rememberUpdatedMarkerState(position = latLng),
                                icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_map_location_dot_24),
                                anchor = Offset(0.5f, 0.5f),
                            )
                        }
                    }
                    Polyline(points = latLngs, jointType = JointType.ROUND, color = polylineColor, width = 8f)
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
                                debug("Cluster clicked! $cluster")
                            }
                            true
                        },
                    )
                }

                WaypointMarkers(
                    displayableWaypoints = displayableWaypoints,
                    mapFilterState = mapFilterState,
                    myNodeNum = uiViewModel.myNodeNum ?: 0,
                    isConnected = isConnected,
                    unicodeEmojiToBitmapProvider = ::unicodeEmojiToBitmap,
                    onEditWaypointRequest = { waypointToEdit -> editingWaypoint = waypointToEdit },
                )

                MapEffect(mapLayers) { map ->
                    mapLayers.forEach { layerItem ->
                        mapViewModel.loadKmlLayerIfNeeded(map, layerItem)?.let { kmlLayer ->
                            if (layerItem.isVisible && !kmlLayer.isLayerOnMap) {
                                kmlLayer.addLayerToMap()
                            } else if (!layerItem.isVisible && kmlLayer.isLayerOnMap) {
                                kmlLayer.removeLayerFromMap()
                            }
                        }
                    }
                }
            }

            DisappearingScaleBar(cameraPositionState = cameraPositionState)

            editingWaypoint?.let { waypointToEdit ->
                EditWaypointDialog(
                    waypoint = waypointToEdit,
                    onSendClicked = { updatedWp ->
                        var finalWp = updatedWp
                        if (updatedWp.id == 0) {
                            finalWp = finalWp.copy { id = uiViewModel.generatePacketId() ?: 0 }
                        }
                        if (updatedWp.icon == 0) {
                            finalWp = finalWp.copy { icon = 0x1F4CD } // 📍 Round Pushpin
                        }

                        uiViewModel.sendWaypoint(finalWp)
                        editingWaypoint = null
                    },
                    onDeleteClicked = { wpToDelete ->
                        if (wpToDelete.lockedTo == 0 && isConnected && wpToDelete.id != 0) {
                            val deleteMarkerWp = wpToDelete.copy { expire = 1 }
                            uiViewModel.sendWaypoint(deleteMarkerWp)
                        }
                        uiViewModel.deleteWaypoint(wpToDelete.id)
                        editingWaypoint = null
                    },
                    onDismissRequest = { editingWaypoint = null },
                )
            }

            MapControlsOverlay(
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = -ScreenOffset),
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
                showFilterButton = focusedNodeNum == null,
                scrollBehavior = exitAlwaysScrollBehavior,
            )
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
}

internal fun convertIntToEmoji(unicodeCodePoint: Int): String = try {
    String(Character.toChars(unicodeCodePoint))
} catch (e: IllegalArgumentException) {
    Log.e("Emoji_Conversion", "Invalid Unicode code point: $unicodeCodePoint", e)
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

data class NodeClusterItem(val node: Node, val nodePosition: LatLng, val nodeTitle: String, val nodeSnippet: String) :
    ClusterItem {
    override fun getPosition(): LatLng = nodePosition

    override fun getTitle(): String = nodeTitle

    override fun getSnippet(): String = nodeSnippet

    override fun getZIndex(): Float? = null

    fun getPrecisionMeters(): Double? {
        val precisionMap =
            mapOf(
                10 to 23345.484932,
                11 to 11672.7369,
                12 to 5836.36288,
                13 to 2918.175876,
                14 to 1459.0823719999053,
                15 to 729.53562,
                16 to 364.7622,
                17 to 182.375556,
                18 to 91.182212,
                19 to 45.58554,
            )
        return precisionMap[this.node.position.precisionBits]
    }
}
