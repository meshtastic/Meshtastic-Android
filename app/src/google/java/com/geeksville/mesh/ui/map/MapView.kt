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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.components.EditWaypointDialog
import com.geeksville.mesh.ui.map.components.MapButton
import com.geeksville.mesh.ui.node.DegD
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.waypoint
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.widgets.DisappearingScaleBar

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapView(
    uiViewModel: UIViewModel,
    mapViewModel: MapViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
) {
    val context = LocalContext.current
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()
    var showLayerManagementDialog by remember { mutableStateOf(false) }

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
    val mapFilterState by uiViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val ourNodeInfo by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var editingWaypoint by remember { mutableStateOf<MeshProtos.Waypoint?>(null) }

    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    val defaultLatLng = LatLng(40.7871508066057, -119.2041344866371)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 7f)
    }

    val allNodes by uiViewModel.filteredNodeList.collectAsStateWithLifecycle()
    val waypoints by uiViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())

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

    LaunchedEffect(filteredNodes, waypoints, mapFilterState.showWaypoints) {
        val boundsBuilder = LatLngBounds.Builder()
        var includedPoints = 0

        filteredNodes.forEach { node ->
            node.position.let {
                boundsBuilder.include(LatLng(it.latitudeI * DegD, it.longitudeI * DegD))
                includedPoints++
            }
        }
        if (mapFilterState.showWaypoints) {
            waypoints.mapNotNull { waypoint ->
                val pt = waypoint.value.data.waypoint
                if (pt == null) {
                    return@mapNotNull null
                }
                boundsBuilder.include(LatLng(pt.latitudeI * DegD, pt.longitudeI * DegD))
                includedPoints++
            }
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            mapColorScheme = mapColorScheme,
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(),
            properties = MapProperties(
                mapType = selectedMapType,
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
            Clustering(
                items = nodeClusterItems,
                onClusterItemInfoWindowClick = { item ->
                    navigateToNodeDetails(item.node.num)
                    false
                },
                clusterItemContent = {
                    NodeChip(
                        node = it.node,
                        enabled = false,
                        isThisNode = false,
                        isConnected = false
                    ) { }
                },
                onClusterManager = { clusterManager ->
                    (clusterManager.renderer as DefaultClusterRenderer).minClusterSize = 7
                }
            )

            if (mapFilterState.showPrecisionCircle) {
                nodeClusterItems.forEach { clusterItem ->
                    clusterItem.node.position.precisionBits.let { accuracy ->
                        if (accuracy > 0) {
                            Circle(
                                center = clusterItem.position,
                                radius = accuracy.toDouble(), // In meters
                                fillColor = Color(clusterItem.node.colors.second).copy(alpha = 0.2f),
                                strokeColor = Color(clusterItem.node.colors.second),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }

            waypoints.map { it.value.data }.forEach { data ->
                val waypoint = data.waypoint ?: return@forEach
                Marker(
                    state = rememberUpdatedMarkerState(
                        position = LatLng(
                            waypoint.latitudeI * DegD,
                            waypoint.longitudeI * DegD
                        )
                    ),
                    icon = if (waypoint.icon == 0) {
                        unicodeEmojiToBitmap(0x1F4CD)
                    } else {
                        unicodeEmojiToBitmap(waypoint.icon)
                    },
                    title = waypoint.name,
                    snippet = waypoint.description,
                    visible = mapFilterState.showWaypoints,
                    onInfoWindowClick = { marker ->
                        val wpToEdit = waypoint
                        val myNodeNum = uiViewModel.myNodeNum ?: 0
                        // Check if editable
                        if (
                            wpToEdit.lockedTo == 0 ||
                            wpToEdit.lockedTo == myNodeNum ||
                            !isConnected
                        ) {
                            editingWaypoint = waypoint
                        } else {
                            // Optionally show a toast that it's locked by someone else
                        }
                    }
                )
            }
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
                        finalWp = finalWp.copy { id = uiViewModel.generatePacketId() ?: 0 }
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
                            wpToDelete.copy { expire = 1 } // Set expire to 1 to indicate deletion
                        uiViewModel.sendWaypoint(deleteMarkerWp)
                    }
                    uiViewModel.deleteWaypoint(wpToDelete.id) // Delete from local DB
                    editingWaypoint = null
                },
                onDismissRequest = { editingWaypoint = null }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                MapButton(
                    icon = Icons.Outlined.Tune,
                    contentDescription = stringResource(id = R.string.map_filter),
                    onClick = { mapFilterMenuExpanded = true }
                )
                MapFilterDropdown(
                    expanded = mapFilterMenuExpanded,
                    onDismissRequest = { mapFilterMenuExpanded = false },
                    mapFilterState = mapFilterState,
                    uiViewModel = uiViewModel
                )
            }

            Box {
                MapButton(
                    icon = Icons.Outlined.Map,
                    contentDescription = stringResource(id = R.string.map_tile_source),
                    onClick = { mapTypeMenuExpanded = true }
                )
                MapTypeDropdown(
                    expanded = mapTypeMenuExpanded,
                    onDismissRequest = { mapTypeMenuExpanded = false },
                    onMapTypeSelected = {
                        selectedMapType = it
                        mapTypeMenuExpanded = false
                    }
                )
            }

            MapButton( // Add KML Layer Button
                icon = Icons.Outlined.Layers,
                contentDescription = stringResource(id = R.string.manage_map_layers),
                onClick = { showLayerManagementDialog = true }
            )
        }
        if (showLayerManagementDialog) {
            LayerManagementDialog(
                mapLayers = mapLayers,
                onDismissRequest = { showLayerManagementDialog = false },
                onAddLayerClicked = {
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
                    // showLayerManagementDialog = false // Optionally dismiss after clicking add
                },
                onToggleVisibility = { layerId ->
                    mapViewModel.toggleLayerVisibility(
                        layerId
                    )
                },
                onRemoveLayer = { layerId -> mapViewModel.removeMapLayer(layerId) }
            )
        }
    }
}

@Composable
private fun MapFilterDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    mapFilterState: UIViewModel.MapFilterState,
    uiViewModel: UIViewModel
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.only_favorites)) },
            onClick = { uiViewModel.setOnlyFavorites(!mapFilterState.onlyFavorites) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(id = R.string.only_favorites)
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.onlyFavorites,
                    onCheckedChange = { uiViewModel.setOnlyFavorites(it) }
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.show_waypoints)) },
            onClick = { uiViewModel.setShowWaypointsOnMap(!mapFilterState.showWaypoints) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = stringResource(id = R.string.show_waypoints)
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showWaypoints,
                    onCheckedChange = { uiViewModel.setShowWaypointsOnMap(it) }
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.show_precision_circle)) },
            onClick = { uiViewModel.setShowPrecisionCircleOnMap(!mapFilterState.showPrecisionCircle) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked, // Placeholder icon
                    contentDescription = stringResource(id = R.string.show_precision_circle)
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showPrecisionCircle,
                    onCheckedChange = { uiViewModel.setShowPrecisionCircleOnMap(it) }
                )
            }
        )
    }
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
}

@Composable
private fun MapTypeDropdown(
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

private fun convertIntToEmoji(unicodeCodePoint: Int): String {
    return try {
        String(Character.toChars(unicodeCodePoint))
    } catch (e: IllegalArgumentException) {
        // Handle cases where the integer is not a valid Unicode code point
        // For example, return a placeholder or an empty string
        Log.e("Emoji_Conversion", "Invalid Unicode code point: $unicodeCodePoint", e)
        "\uD83D\uDCCD" // Placeholder for invalid code point
    }
}

private fun unicodeEmojiToBitmap(icon: Int): BitmapDescriptor {
    val unicodeEmoji = convertIntToEmoji(icon)
    // Create a Paint object for drawing text
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 90f // Adjust size as needed
        textAlign = Paint.Align.CENTER
    }

    // Measure text bounds to create a bitmap of the correct size
    val bounds = android.graphics.Rect()
    paint.getTextBounds(unicodeEmoji, 0, unicodeEmoji.length, bounds)

    // Create a bitmap and canvas
    val bitmap = createBitmap(bounds.width() + 20, bounds.height() + 20) // Add some padding
    val canvas = Canvas(bitmap)

    // Draw the emoji onto the canvas
    canvas.drawText(
        unicodeEmoji,
        canvas.width / 2f,
        canvas.height / 2f - bounds.exactCenterY(),
        paint
    )
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Suppress("LongMethod")
@Composable
fun LayerManagementDialog(
    mapLayers: List<MapLayerItem>,
    onDismissRequest: () -> Unit,
    onAddLayerClicked: () -> Unit,
    onToggleVisibility: (String) -> Unit,
    onRemoveLayer: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_layers_title)) },
        text = {
            if (mapLayers.isEmpty()) {
                Text(stringResource(R.string.no_map_layers_loaded))
            } else {
                LazyColumn {
                    items(mapLayers, key = { it.id }) { layer ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    layer.name
                                )
                            },
                            supportingContent = {
                                Text(
                                    layer.uri?.lastPathSegment ?: "Unknown source", maxLines = 1
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (layer.isVisible) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = if (layer.isVisible) "Visible" else "Hidden",
                                    modifier = Modifier.clickable { onToggleVisibility(layer.id) }
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemoveLayer(layer.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove Layer"
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (layer.isVisible) {
                                    Color.Transparent
                                } else {
                                    Color.Gray.copy(
                                        alpha = 0.2f
                                    )
                                }
                            )
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddLayerClicked) {
                Text(stringResource(R.string.add_layer_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Suppress("NestedBlockDepth")
fun Uri.getFileName(context: android.content.Context): String? {
    var result: String? = null
    if (scheme == "content") {
        val cursor = context.contentResolver.query(this, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val displayNameIndex =
                    cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    result = cursor.getString(displayNameIndex)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = path
        val cut = result?.lastIndexOf('/')
        if (cut != -1 && cut != null) {
            result = result.substring(cut + 1)
        }
    }
    return result
}
