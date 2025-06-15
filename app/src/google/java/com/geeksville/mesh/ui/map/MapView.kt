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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.components.EditWaypointDialog
import com.geeksville.mesh.ui.map.components.MapButton
import com.geeksville.mesh.ui.node.DegD
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.waypoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE
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
import kotlinx.coroutines.launch
import java.text.DateFormat

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

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapView(
    uiViewModel: UIViewModel,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var mapFilterMenuExpanded by remember { mutableStateOf(false) }
    val mapFilterState by uiViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val ourNodeInfo by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var editingWaypoint by remember { mutableStateOf<MeshProtos.Waypoint?>(null) }
    val uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = true,
                mapToolbarEnabled = true
            )
        )
    }
    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    // Default to a wide view of the US
    val defaultLatLng = LatLng(39.8283, -98.5795)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 3f)
    }

    @Composable
    fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL) {
        stringResource(R.string.you)
    } else {
        uiViewModel.getUser(id).longName
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
            nodeSnippet = context.getString(
                R.string.map_node_popup_details,
                node.gpsString(uiViewModel.config.display.gpsFormat.number),
                formatAgo(node.lastHeard),
                formatAgo(node.position.time),
                if (node.batteryStr != "") node.batteryStr else "?"
            ),
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
            properties = MapProperties(
                mapType = selectedMapType
            ),
            uiSettings = uiSettings,
            onMapLongClick = { latLng ->
                if (isConnected) {
                    val newWaypoint = waypoint {
                        latitudeI = (latLng.latitude * DegD).toInt()
                        longitudeI = (latLng.longitude * DegD).toInt()
                    }
                    editingWaypoint = newWaypoint
                }
            }
        ) {
            Clustering(
                items = nodeClusterItems,
                onClusterClick = { cluster ->
                    val bounds = LatLngBounds.builder()
                    cluster.items.forEach { bounds.include(it.position) }
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
                        )
                    }
                    debug("Cluster clicked! $cluster")
                    false
                },
                onClusterManager = { clusterManager ->
                    (clusterManager.renderer as DefaultClusterRenderer).minClusterSize =
                        7
                }
            )

            if (mapFilterState.showPrecisionCircle) {
                nodeClusterItems.forEach { clusterItem ->
                    clusterItem.node.position.precisionBits.let { accuracy ->
                        if (accuracy > 0) {
                            Circle(
                                center = clusterItem.position,
                                radius = accuracy.toDouble(), // In meters
                                fillColor = Color(0x44AAAAFF), // Semi-transparent blue
                                strokeColor = Color(0x88AAAAFF), // More opaque blue
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }

            waypoints.forEach { waypoint ->
                waypoint.value.data.waypoint?.let { pt ->
                    val dateFormat =
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    val lock = if (pt.lockedTo != 0) "\uD83D\uDD12" else ""
                    val time = dateFormat.format(waypoint.value.received_time)
                    val label =
                        pt.name + " " + formatAgo((waypoint.value.received_time / 1000).toInt())
                    val emoji = String(Character.toChars(if (pt.icon == 0) 128205 else pt.icon))
                    val timeLeft = pt.expire * 1000L - System.currentTimeMillis()
                    val expireTimeStr = when {
                        pt.expire == 0 || pt.expire == Int.MAX_VALUE -> "Never"
                        timeLeft <= 0 -> "Expired"
                        timeLeft < 60_000 -> "${timeLeft / 1000} seconds"
                        timeLeft < 3_600_000 -> "${timeLeft / 60_000} minute${if (timeLeft / 60_000 != 1L) "s" else ""}"
                        timeLeft < 86_400_000 -> {
                            val hours = (timeLeft / 3_600_000).toInt()
                            val minutes = ((timeLeft % 3_600_000) / 60_000).toInt()
                            if (minutes >= 30) {
                                "${hours + 1} hour${if (hours + 1 != 1) "s" else ""}"
                            } else if (minutes > 0) {
                                "$hours hour${if (hours != 1) "s" else ""}, $minutes minute${if (minutes != 1) "s" else ""}"
                            } else {
                                "$hours hour${if (hours != 1) "s" else ""}"
                            }
                        }

                        else -> "${timeLeft / 86_400_000} day${if (timeLeft / 86_400_000 != 1L) "s" else ""}"
                    }
                    val title = "${pt.name} (${getUsername(waypoint.value.data.from)}$lock)"
                    val snippet =
                        "[$time] ${pt.description}  " + stringResource(R.string.expires) + ": $expireTimeStr"
                    val latLng = LatLng(pt.latitudeI * DegD, pt.longitudeI * DegD)
                    Marker(
                        icon = BitmapDescriptorFactory.defaultMarker(HUE_AZURE),
                        state = rememberUpdatedMarkerState(position = latLng),
                        title = title,
                        snippet = snippet,
                        visible = mapFilterState.showWaypoints,
                        tag = waypoint.value.data.id.toString(), // Store waypoint ID in marker tag
                        onInfoWindowClick = { marker ->
                            val clickedWaypointId = marker.tag?.toString()?.toIntOrNull()
                            if (clickedWaypointId != null) {
                                val wpToEdit =
                                    waypoints.values.find { it.data.id == clickedWaypointId }
                                if (wpToEdit != null) {
                                    val myNodeNum = uiViewModel.myNodeNum ?: 0
                                    // Check if editable
                                    if (wpToEdit.data.waypoint?.lockedTo == 0 || wpToEdit.data.waypoint?.lockedTo == myNodeNum || !isConnected) {
                                        editingWaypoint = wpToEdit.data.waypoint
                                    } else {
                                        // Optionally show a toast that it's locked by someone else
                                    }
                                }
                            }
                        }
                    )
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
                    var finalWp = waypointToEdit.copy {
                        if (id == 0) {
                            id =
                                uiViewModel.generatePacketId() ?: return@EditWaypointDialog
                        }
                        if (name == "") name = "Dropped Pin"
                        if (expire == 0) expire = Int.MAX_VALUE
                        lockedTo =
                            if (waypointToEdit.lockedTo != 0) uiViewModel.myNodeNum ?: 0 else 0
                        if (waypointToEdit.icon == 0) icon = 128205
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
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        androidx.compose.material3.DropdownMenuItem(
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
        androidx.compose.material3.DropdownMenuItem(
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
        androidx.compose.material3.DropdownMenuItem(
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
