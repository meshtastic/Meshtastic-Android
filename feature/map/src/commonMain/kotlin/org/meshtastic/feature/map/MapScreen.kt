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
package org.meshtastic.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.BearingUpdate
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.GestureOptions
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.MapFilterDropdown
import org.meshtastic.feature.map.component.MapStyleSelector
import org.meshtastic.feature.map.component.MaplibreMapContent
import org.meshtastic.feature.map.model.MapStyle
import org.meshtastic.feature.map.util.COORDINATE_SCALE
import org.maplibre.spatialk.geojson.Position as GeoPosition

private const val WAYPOINT_ZOOM = 15.0

/**
 * Main map screen composable. Uses MapLibre Compose Multiplatform to render an interactive map with mesh node markers,
 * waypoints, and overlays.
 *
 * This replaces the previous flavor-specific Google Maps and OSMDroid implementations with a single cross-platform
 * composable.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MapScreen(
    onClickNodeChip: (Int) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    waypointId: Int? = null,
) {
    val ourNodeInfo by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val filteredNodes by viewModel.filteredNodes.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val baseStyle by viewModel.baseStyle.collectAsStateWithLifecycle()
    val selectedMapStyle by viewModel.selectedMapStyle.collectAsStateWithLifecycle()

    LaunchedEffect(waypointId) { viewModel.setWaypointId(waypointId) }

    val cameraState = rememberCameraState(firstPosition = viewModel.initialCameraPosition)

    var filterMenuExpanded by remember { mutableStateOf(false) }

    // Waypoint dialog state
    var showWaypointDialog by remember { mutableStateOf(false) }
    var longPressPosition by remember { mutableStateOf<GeoPosition?>(null) }
    var editingWaypointId by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()

    // Location tracking state: 3-mode cycling (Off → Track → TrackBearing → Off)
    var isLocationTrackingEnabled by remember { mutableStateOf(false) }
    var bearingUpdate by remember { mutableStateOf(BearingUpdate.TRACK_LOCATION) }
    val locationProvider = rememberLocationProviderOrNull()
    val locationState = rememberUserLocationState(locationProvider ?: rememberNullLocationProvider())
    val locationAvailable = locationProvider != null

    // Derive gesture options from location tracking state
    val gestureOptions =
        remember(isLocationTrackingEnabled, bearingUpdate) {
            if (isLocationTrackingEnabled) {
                when (bearingUpdate) {
                    BearingUpdate.IGNORE -> GestureOptions.PositionLocked
                    BearingUpdate.ALWAYS_NORTH -> GestureOptions.ZoomOnly
                    BearingUpdate.TRACK_LOCATION -> GestureOptions.ZoomOnly
                }
            } else {
                GestureOptions.Standard
            }
        }

    // Animate to waypoint when waypointId is provided (deep-link)
    val selectedWaypointId by viewModel.selectedWaypointId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedWaypointId, waypoints) {
        val wpId = selectedWaypointId ?: return@LaunchedEffect
        val packet = waypoints[wpId] ?: return@LaunchedEffect
        val wpt = packet.waypoint ?: return@LaunchedEffect
        val lat = (wpt.latitude_i ?: 0) * COORDINATE_SCALE
        val lng = (wpt.longitude_i ?: 0) * COORDINATE_SCALE
        if (lat != 0.0 || lng != 0.0) {
            cameraState.animateTo(
                CameraPosition(target = GeoPosition(longitude = lng, latitude = lat), zoom = WAYPOINT_ZOOM),
            )
        }
    }

    @Suppress("ViewModelForwarding")
    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.map),
                ourNode = ourNodeInfo,
                showNodeChip = ourNodeInfo != null && isConnected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            MaplibreMapContent(
                nodes = filteredNodes,
                waypoints = waypoints,
                baseStyle = baseStyle,
                cameraState = cameraState,
                myNodeNum = viewModel.myNodeNum,
                showWaypoints = filterState.showWaypoints,
                showPrecisionCircle = filterState.showPrecisionCircle,
                showHillshade = selectedMapStyle == MapStyle.Terrain,
                onNodeClick = { nodeNum -> navigateToNodeDetails(nodeNum) },
                onMapLongClick = { position ->
                    longPressPosition = position
                    editingWaypointId = null
                    showWaypointDialog = true
                },
                modifier = Modifier.fillMaxSize(),
                gestureOptions = gestureOptions,
                onCameraMoved = { position -> viewModel.saveCameraPosition(position) },
                onWaypointClick = { wpId ->
                    editingWaypointId = wpId
                    longPressPosition = null
                    showWaypointDialog = true
                },
                locationState = if (isLocationTrackingEnabled && locationAvailable) locationState else null,
            )

            // Auto-pan camera when location tracking is enabled
            if (locationAvailable) {
                LocationTrackingEffect(
                    locationState = locationState,
                    enabled = isLocationTrackingEnabled,
                    trackBearing = bearingUpdate == BearingUpdate.TRACK_LOCATION,
                ) {
                    cameraState.updateFromLocation(updateBearing = bearingUpdate)
                }

                // Cancel tracking when user manually pans the map
                LaunchedEffect(cameraState.moveReason) {
                    if (cameraState.moveReason == CameraMoveReason.GESTURE && isLocationTrackingEnabled) {
                        isLocationTrackingEnabled = false
                        bearingUpdate = BearingUpdate.IGNORE
                    }
                }
            }

            MapControlsOverlay(
                onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                modifier = Modifier.align(Alignment.TopEnd).padding(paddingValues),
                bearing = cameraState.position.bearing.toFloat(),
                onCompassClick = { scope.launch { cameraState.animateTo(cameraState.position.copy(bearing = 0.0)) } },
                followPhoneBearing = isLocationTrackingEnabled && bearingUpdate == BearingUpdate.TRACK_LOCATION,
                filterDropdownContent = {
                    MapFilterDropdown(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                        filterState = filterState,
                        onToggleFavorites = viewModel::toggleOnlyFavorites,
                        onToggleWaypoints = viewModel::toggleShowWaypointsOnMap,
                        onTogglePrecisionCircle = viewModel::toggleShowPrecisionCircleOnMap,
                        onSetLastHeardFilter = viewModel::setLastHeardFilter,
                    )
                },
                mapTypeContent = {
                    MapStyleSelector(selectedStyle = selectedMapStyle, onSelectStyle = viewModel::selectMapStyle)
                },
                layersContent = { OfflineMapContent(styleUri = selectedMapStyle.styleUri, cameraState = cameraState) },
                isLocationTrackingEnabled = isLocationTrackingEnabled,
                isTrackingBearing = bearingUpdate == BearingUpdate.TRACK_LOCATION,
                onToggleLocationTracking = {
                    if (!isLocationTrackingEnabled) {
                        // Off → Track with bearing
                        bearingUpdate = BearingUpdate.TRACK_LOCATION
                        isLocationTrackingEnabled = true
                    } else {
                        when (bearingUpdate) {
                            BearingUpdate.TRACK_LOCATION -> {
                                // TrackBearing → TrackNorth
                                bearingUpdate = BearingUpdate.ALWAYS_NORTH
                            }
                            BearingUpdate.ALWAYS_NORTH -> {
                                // TrackNorth → Off
                                isLocationTrackingEnabled = false
                            }
                            BearingUpdate.IGNORE -> {
                                isLocationTrackingEnabled = false
                            }
                        }
                    }
                },
            )
        }
    }

    // Waypoint creation/edit dialog
    if (showWaypointDialog) {
        val editingPacket = editingWaypointId?.let { waypoints[it] }
        val editingWaypoint = editingPacket?.waypoint

        EditWaypointDialog(
            onDismiss = {
                showWaypointDialog = false
                editingWaypointId = null
                longPressPosition = null
            },
            onSend = { name, description, icon, locked, expire ->
                viewModel.createAndSendWaypoint(
                    name = name,
                    description = description,
                    icon = icon,
                    locked = locked,
                    expire = expire,
                    existingWaypoint = editingWaypoint,
                    position = longPressPosition,
                )
            },
            onDelete = editingWaypoint?.let { wpt -> { viewModel.deleteWaypoint(wpt.id) } },
            initialName = editingWaypoint?.name ?: "",
            initialDescription = editingWaypoint?.description ?: "",
            initialIcon = editingWaypoint?.icon ?: 0,
            initialLocked = (editingWaypoint?.locked_to ?: 0) != 0,
            isEditing = editingWaypoint != null,
            position = longPressPosition,
        )
    }
}
