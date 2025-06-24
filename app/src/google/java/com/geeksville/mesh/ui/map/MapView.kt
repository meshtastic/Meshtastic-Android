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

package com.geeksville.mesh.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
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
    navigateToNodeDetails: (Int) -> Unit,
) {
    var mapFilterMenuExpanded by remember { mutableStateOf(false) }
    val mapFilterState by uiViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val ourNodeInfo by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var editingWaypoint by remember { mutableStateOf<MeshProtos.Waypoint?>(null) }

    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    val defaultLatLng = LatLng(39.8283, -98.5795)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 3f)
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
                .align(Alignment.CenterEnd)
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
                    icon = Icons.Outlined.Layers,
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
