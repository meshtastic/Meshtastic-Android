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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.geeksville.mesh.ui.map.components.EditWaypointDialog
import com.geeksville.mesh.ui.map.components.MapControlsOverlay
import com.geeksville.mesh.ui.map.components.NodeClusterMarkers
import com.geeksville.mesh.ui.map.components.WaypointMarkers
import com.geeksville.mesh.ui.node.DegD
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.waypoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
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
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.widgets.DisappearingScaleBar
import kotlinx.coroutines.launch

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(
    MapsComposeExperimentalApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun MapView(
    uiViewModel: UIViewModel,
    mapViewModel: MapViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()
    var hasLocationPermission by remember { mutableStateOf(false) }

    LocationPermissionsHandler { isGranted ->
        hasLocationPermission = isGranted
    }

    val kmlFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
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

    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    val defaultLatLng = LatLng(40.7871508066057, -119.2041344866371)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 7f)
    }

    val allNodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by uiViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())
    val displayableWaypoints = waypoints.values.mapNotNull { it.data.waypoint }

    val filteredNodes = if (mapFilterState.onlyFavorites) {
        allNodes.filter { it.isFavorite || it.num == ourNodeInfo?.num }
    } else {
        allNodes
    }

    val nodeClusterItems = filteredNodes.map { node ->
        val latLng = LatLng(node.position.latitudeI * DegD, node.position.longitudeI * DegD)
        NodeClusterItem(
            node = node,
            nodePosition = latLng,
            nodeTitle = "${node.user.shortName} ${formatAgo(node.position.time)}",
            nodeSnippet = "${node.user.longName}"
        )
    }
    val isConnected by uiViewModel.isConnected.collectAsStateWithLifecycle(false)
    val theme by uiViewModel.theme.collectAsStateWithLifecycle()
    val dark = when (theme) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> isSystemInDarkTheme()
        else -> isSystemInDarkTheme()
    }
    val mapColorScheme = when (dark) {
        true -> ComposeMapColorScheme.DARK
        else -> ComposeMapColorScheme.LIGHT
    }

    var showLayersBottomSheet by remember { mutableStateOf(false) }

    val onAddLayerClicked = {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // Allow all file types initially
            // More specific MIME types for KML/KMZ
            val mimeTypes = arrayOf(
                "application/vnd.google-earth.kml+xml",
                "application/vnd.google-earth.kmz"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        kmlFilePickerLauncher.launch(intent)
    }
    val onRemoveLayer = { layerId: String ->
        mapViewModel.removeMapLayer(layerId)
    }
    val onToggleVisibility = { layerId: String ->
        mapViewModel.toggleLayerVisibility(layerId)
    }
    var showClusterItemsDialog by remember { mutableStateOf<List<NodeClusterItem>?>(null) }
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GoogleMap(
                mapColorScheme = mapColorScheme,
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = true,
                    compassEnabled = true,
                    myLocationButtonEnabled = hasLocationPermission,
                    rotationGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = true,
                    zoomGesturesEnabled = true
                ),
                properties = MapProperties(
                    mapType = selectedMapType,
                    isMyLocationEnabled = hasLocationPermission
                ),
                onMapLongClick = { latLng ->
                    if (isConnected) {
                        val newWaypoint = waypoint {
                            latitudeI = (latLng.latitude / DegD).toInt()
                            longitudeI = (latLng.longitude / DegD).toInt()
                        }
                        editingWaypoint = newWaypoint
                    }
                }
            ) {
                NodeClusterMarkers(
                    nodeClusterItems = nodeClusterItems,
                    mapFilterState = mapFilterState,
                    navigateToNodeDetails = navigateToNodeDetails,
                    onClusterClick = { cluster ->
                        val items = cluster.items.toList()
                        // Check if all items are at the same location (or very, very close)
                        val allSameLocation =
                            items.size > 1 && items.all { it.position == items.first().position }

                        if (allSameLocation) {
                            showClusterItemsDialog = items
                        } else {
                            val bounds = LatLngBounds.builder()
                            cluster.items.forEach { bounds.include(it.position) }
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
                                )
                            }
                            debug("Cluster clicked! $cluster")
                        }
                        true
                    },
                )

                WaypointMarkers(
                    displayableWaypoints = displayableWaypoints,
                    mapFilterState = mapFilterState,
                    myNodeNum = uiViewModel.myNodeNum ?: 0,
                    isConnected = isConnected,
                    unicodeEmojiToBitmapProvider = ::unicodeEmojiToBitmap,
                    onEditWaypointRequest = { waypointToEdit ->
                        editingWaypoint = waypointToEdit
                    }
                )

                MapEffect(mapLayers) { map ->
                    mapLayers.forEach { layerItem ->
                        mapViewModel.loadKmlLayerIfNeeded(map, layerItem)
                            ?.let { kmlLayer -> // Combine let with ?.
                                if (layerItem.isVisible && !kmlLayer.isLayerOnMap) {
                                    kmlLayer.addLayerToMap()
                                } else if (!layerItem.isVisible && kmlLayer.isLayerOnMap) {
                                    kmlLayer.removeLayerFromMap()
                                }
                            }
                    }
                }
            }

            DisappearingScaleBar(
                cameraPositionState = cameraPositionState
            )

            editingWaypoint?.let { waypointToEdit ->
                EditWaypointDialog(
                    waypoint = waypointToEdit,
                    onSendClicked = { updatedWp ->
                        var finalWp = updatedWp
                        if (updatedWp.id == 0) {
                            finalWp =
                                finalWp.copy { id = uiViewModel.generatePacketId() ?: 0 }
                        }
                        if (updatedWp.icon == 0) {
                            finalWp = finalWp.copy { icon = 0x1F4CD } // 📍 Round Pushpin
                        }

                        uiViewModel.sendWaypoint(finalWp)
                        editingWaypoint = null
                    },
                    onDeleteClicked = { wpToDelete ->
                        // If it's a shared waypoint and we are connected, send out a delete message
                        if (wpToDelete.lockedTo == 0 && isConnected && wpToDelete.id != 0) {
                            val deleteMarkerWp =
                                wpToDelete.copy {
                                    expire = 1
                                } // Set expire to 1 to indicate deletion
                            uiViewModel.sendWaypoint(deleteMarkerWp)
                        }
                        uiViewModel.deleteWaypoint(wpToDelete.id) // Delete from local DB
                        editingWaypoint = null
                    },
                    onDismissRequest = { editingWaypoint = null }
                )
            }

            MapControlsOverlay(
                modifier = Modifier.align(Alignment.CenterEnd),
                mapFilterMenuExpanded = mapFilterMenuExpanded,
                onMapFilterMenuDismissRequest = { mapFilterMenuExpanded = false },
                onToggleMapFilterMenu = { mapFilterMenuExpanded = true },
                mapViewModel = mapViewModel,
                mapTypeMenuExpanded = mapTypeMenuExpanded,
                onMapTypeMenuDismissRequest = { mapTypeMenuExpanded = false },
                onToggleMapTypeMenu = { mapTypeMenuExpanded = true },
                onMapTypeSelected = { mapType ->
                    selectedMapType = mapType
                    mapTypeMenuExpanded = false
                },
                onManageLayersClicked = {
                    showLayersBottomSheet = true
                }
            )
        }
        if (showLayersBottomSheet) {
            ModalBottomSheet({ showLayersBottomSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LazyColumn {
                        item {
                            Text(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                text = stringResource(R.string.manage_map_layers)
                            )
                        }
                        stickyHeader {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                onClick = onAddLayerClicked
                            ) {
                                Text(stringResource(R.string.add_layer))
                            }
                        }
                        if (mapLayers.isEmpty()) {
                            item {
                                Text(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    text = stringResource(R.string.no_map_layers_loaded)
                                )
                            }
                        }
                        items(mapLayers, key = { it.id }) { layer ->
                            ListItem(
                                headlineContent = { Text(layer.name) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            onToggleVisibility(layer.id)
                                        }) {
                                            Icon(
                                                imageVector = if (layer.isVisible) {
                                                    Icons.Filled.Visibility
                                                } else {
                                                    Icons.Filled.VisibilityOff
                                                },
                                                contentDescription = stringResource(
                                                    if (layer.isVisible) {
                                                        R.string.hide_layer
                                                    } else {
                                                        R.string.show_layer
                                                    }
                                                )
                                            )
                                        }
                                        IconButton(onClick = { onRemoveLayer(layer.id) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = stringResource(R.string.remove_layer)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
        showClusterItemsDialog?.let {
            ClusterItemsListDialog(
                items = it,
                onDismiss = { showClusterItemsDialog = null },
                onItemClick = { item ->
                    navigateToNodeDetails(item.node.num)
                    showClusterItemsDialog = null
                }
            )
        }
    }
}

@Composable
internal fun MapFilterDropdown(
    // Made internal
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    mapViewModel: MapViewModel,
) {
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.only_favorites)) },
            onClick = { mapViewModel.setOnlyFavorites(!mapFilterState.onlyFavorites) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(id = R.string.only_favorites)
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.onlyFavorites,
                    onCheckedChange = { mapViewModel.setOnlyFavorites(it) }
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.show_waypoints)) },
            onClick = { mapViewModel.setShowWaypointsOnMap(!mapFilterState.showWaypoints) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = stringResource(id = R.string.show_waypoints)
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showWaypoints,
                    onCheckedChange = { mapViewModel.setShowWaypointsOnMap(it) }
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.show_precision_circle)) },
            onClick = { mapViewModel.setShowPrecisionCircleOnMap(!mapFilterState.showPrecisionCircle) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked, // Placeholder icon
                    contentDescription = stringResource(id = R.string.show_precision_circle)
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showPrecisionCircle,
                    onCheckedChange = { mapViewModel.setShowPrecisionCircleOnMap(it) }
                )
            }
        )
    }
}

@Composable
internal fun MapTypeDropdown( // Made internal
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onMapTypeSelected: (MapType) -> Unit
) {
    val mapTypes = listOf(
        stringResource(id = R.string.map_type_normal) to MapType.NORMAL,
        stringResource(id = R.string.map_type_satellite) to MapType.SATELLITE,
        stringResource(id = R.string.map_type_terrain) to MapType.TERRAIN,
        stringResource(id = R.string.map_type_hybrid) to MapType.HYBRID,
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        mapTypes.forEach { (name, type) ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = { onMapTypeSelected(type) }
            )
        }
    }
}

internal fun convertIntToEmoji(unicodeCodePoint: Int): String {
    return try {
        String(Character.toChars(unicodeCodePoint))
    } catch (e: IllegalArgumentException) {
        // Handle cases where the integer is not a valid Unicode code point
        // For example, return a placeholder or an empty string
        Log.e("Emoji_Conversion", "Invalid Unicode code point: $unicodeCodePoint", e)
        "\uD83D\uDCCD" // Placeholder for invalid code point
    }
}

internal fun unicodeEmojiToBitmap(icon: Int): BitmapDescriptor {
    val unicodeEmoji = convertIntToEmoji(icon)
    // Create a Paint object for drawing text
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 64f // Adjust size as needed
        color = android.graphics.Color.BLACK // Adjust color as needed
        textAlign = Paint.Align.CENTER
    }

    // Create a bitmap and draw the emoji onto it
    val baseline = -paint.ascent() // ascent() is negative
    val width = (paint.measureText(unicodeEmoji) + 0.5f).toInt()
    val height = (baseline + paint.descent() + 0.5f).toInt()
    val image = createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawText(unicodeEmoji, width / 2f, baseline, paint)

    return BitmapDescriptorFactory.fromBitmap(image)
}

// Extension function to get file name from URI
@Suppress("NestedBlockDepth")
fun Uri.getFileName(context: android.content.Context): String {
    var name = this.lastPathSegment ?: "layer_${System.currentTimeMillis()}"
    if (this.scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex =
                    cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = cursor.getString(displayNameIndex)
                }
            }
        }
    }
    return name
}

data class NodeClusterItem(
    val node: Node,
    val nodePosition: LatLng,
    val nodeTitle: String,
    val nodeSnippet: String,
) : ClusterItem {
    override fun getPosition(): LatLng = nodePosition
    override fun getTitle(): String = nodeTitle
    override fun getSnippet(): String = nodeSnippet
    override fun getZIndex(): Float? = null // Default behavior

    fun getPrecisionMeters(): Double? {
        val precisionMap = mapOf(
            10 to 23345.484932,
            11 to 11672.7369,
            12 to 5836.36288,
            13 to 2918.175876,
            14 to 1459.0823719999053,
            15 to 729.53562,
            16 to 364.7622,
            17 to 182.375556,
            18 to 91.182212,
            19 to 45.58554
        )
        return precisionMap[this.node.position.precisionBits]
    }
}
