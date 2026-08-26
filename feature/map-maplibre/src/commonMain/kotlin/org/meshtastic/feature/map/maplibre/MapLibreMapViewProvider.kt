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
package org.meshtastic.feature.map.maplibre

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.BearingUpdate
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberDefaultOrientationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.meshtastic.core.ui.util.KeepScreenOn
import org.meshtastic.core.ui.util.MapViewProvider
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.maplibre.component.BasemapMenu
import org.meshtastic.feature.map.maplibre.component.BasemapSelection
import org.meshtastic.feature.map.maplibre.component.ClusterMembersDialog
import org.meshtastic.feature.map.maplibre.component.FilterMenu
import org.meshtastic.feature.map.maplibre.component.MapLayersButton
import org.meshtastic.feature.map.maplibre.component.OfflineMapTarget
import org.meshtastic.feature.map.maplibre.component.WaypointDialogs
import org.meshtastic.feature.map.maplibre.component.rememberBasemapSelection
import org.meshtastic.feature.map.maplibre.component.rememberWaypointEditing
import org.meshtastic.feature.map.maplibre.geojson.ClusterMember
import org.meshtastic.feature.map.maplibre.layers.CustomLayer
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.MapOverlay
import org.meshtastic.feature.map.maplibre.style.zoomRange

/**
 * MapLibre implementation of [MapViewProvider], shared by the F-Droid flavor and the desktop app.
 *
 * Not a Koin `@Single`: the two call sites construct it directly, which keeps this module free of any assumption about
 * how the host app wires its graph.
 */
class MapLibreMapViewProvider(
    /**
     * Supplies the user's imported overlays. A composable supplier rather than a value so the host can collect its own
     * state; desktop has no importer yet and uses the default.
     */
    private val customLayers: @Composable () -> List<CustomLayer> = { emptyList() },
    /**
     * Supplies the user's own raster tile sources. Composable for the same reason as [customLayers]; the F-Droid app
     * reads them from its tile-provider store, and desktop has no editor for them yet.
     */
    private val customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
    /**
     * Extra content for the foot of the basemap menu — the F-Droid app puts its tile-source editor there. A slot rather
     * than a callback so the host owns whatever UI it opens; desktop leaves it empty.
     */
    private val basemapMenuExtra: @Composable () -> Unit = {},
    /**
     * Presents an editor for the waypoint the map hands over. Host-supplied because the only editor that exists is
     * Android-only; a host that leaves this empty simply cannot create or edit waypoints. See [WaypointEditRequest].
     */
    private val waypointEditor: @Composable (WaypointEditRequest) -> Unit = {},
    /**
     * Runs a Site Planner session. Null hides the button entirely — the planner lives in the app and has no desktop
     * host, so desktop leaves it out. See [SitePlannerSession].
     */
    private val sitePlanner: (@Composable (SitePlannerSession) -> Unit)? = null,
    /**
     * Extra content for the foot of the layers sheet. The F-Droid app puts its imported-layer manager there, which the
     * Google flavor reaches through the same button; adding a layer needs a file picker, so it cannot live here.
     */
    private val layersSheetExtra: @Composable () -> Unit = {},
) : MapViewProvider {

    @Composable
    override fun MapView(
        modifier: Modifier,
        navigateToNodeDetails: (Int) -> Unit,
        waypointId: Int?,
        sitePlannerNodeNum: Int?,
    ) {
        val viewModel: SharedMapViewModel = koinViewModel()
        val basemaps = rememberBasemapSelection(customBasemaps())

        val cameraState = rememberCameraState()
        val location = rememberLocationControls(cameraState)

        val waypoints = rememberWaypointEditing()
        var infoWaypointId by remember { mutableStateOf<Int?>(null) }
        var clusterMembers by remember { mutableStateOf(emptyList<ClusterMember>()) }
        var plannerOpen by remember { mutableStateOf(false) }

        // Deep links, both of which this provider used to drop on the floor.
        LaunchedEffect(waypointId) { waypointId?.let { infoWaypointId = it } }
        LaunchedEffect(sitePlannerNodeNum) { if (sitePlannerNodeNum != null) plannerOpen = true }
        var overlays by remember { mutableStateOf(emptyList<MapOverlay>()) }

        // Following the user means the screen is the thing being watched — the Google flavor holds it awake for the
        // same reason, and a map that sleeps mid-walk is the one complaint a location-follow feature always draws.
        KeepScreenOn(location.following)

        Box(modifier = modifier.fillMaxSize()) {
            MeshMap(
                viewModel = viewModel,
                navigateToNodeDetails = navigateToNodeDetails,
                modifier = Modifier.fillMaxSize(),
                basemap = basemaps.current,
                overlays = overlays,
                customLayers = customLayers(),
                cameraState = cameraState,
                locationState = location.state,
                followLocation = location.following,
                bearingUpdate = location.bearingUpdate,
                frameOnNodes = rememberRestoredCamera(cameraState) == false,
                onWaypointClick = { infoWaypointId = it },
                onClusterMembers = { clusterMembers = it },
                onMapLongClick = waypoints.onLongPress,
            )

            MapToolbar(
                basemaps = basemaps,
                location = location,
                cameraState = cameraState,
                overlays = overlays,
                onOverlaysChange = { overlays = it },
                basemapMenuExtra = basemapMenuExtra,
                layersSheetExtra = layersSheetExtra,
                onSitePlannerClick = sitePlanner?.let { { plannerOpen = true } },
            )

            ClusterMembersSlot(clusterMembers, navigateToNodeDetails) { clusterMembers = emptyList() }

            SitePlannerSlot(
                open = plannerOpen,
                nodeNum = sitePlannerNodeNum,
                cameraState = cameraState,
                planner = sitePlanner,
                onDismiss = { plannerOpen = false },
            )

            WaypointDialogs(
                viewModel = viewModel,
                selectedId = infoWaypointId,
                onSelectedIdChange = { infoWaypointId = it },
                editing = waypoints,
                editor = waypointEditor,
            )
        }
    }
}

/** The cluster list, which clears the selection whichever way it is dismissed. */
@Composable
private fun ClusterMembersSlot(members: List<ClusterMember>, onPick: (Int) -> Unit, onClear: () -> Unit) {
    ClusterMembersDialog(
        members = members,
        onMemberClick = { nodeNum ->
            onClear()
            onPick(nodeNum)
        },
        onDismissRequest = onClear,
    )
}

/** Runs the host's Site Planner while [open], handing it the map centre and a way to move the map. */
@Composable
private fun SitePlannerSlot(
    open: Boolean,
    nodeNum: Int?,
    cameraState: CameraState,
    planner: (@Composable (SitePlannerSession) -> Unit)?,
    onDismiss: () -> Unit,
) {
    if (!open || planner == null) return

    val scope = rememberCoroutineScope()

    planner(
        SitePlannerSession(
            nodeNum = nodeNum,
            mapCenter = { cameraState.position.target },
            moveTo = { target -> scope.launch { cameraState.animateTo(cameraState.position.copy(target = target)) } },
            onDismiss = onDismiss,
        ),
    )
}

/**
 * The floating map toolbar.
 *
 * Owns the filter menu's open state, which the shared overlay cannot: the button that opens it and the dropdown it
 * opens are separate parameters, so something has to hold the flag between them.
 */
@Composable
private fun MapToolbar(
    basemaps: BasemapSelection,
    location: LocationControls,
    cameraState: CameraState,
    overlays: List<MapOverlay>,
    onOverlaysChange: (List<MapOverlay>) -> Unit,
    basemapMenuExtra: @Composable () -> Unit,
    layersSheetExtra: @Composable () -> Unit,
    onSitePlannerClick: (() -> Unit)?,
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MapControlsOverlay(
        onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
        onZoomIn = { scope.launch { cameraState.zoomBy(ZOOM_STEP, basemaps.current.zoomRange()) } },
        onZoomOut = { scope.launch { cameraState.zoomBy(-ZOOM_STEP, basemaps.current.zoomRange()) } },
        bearing = cameraState.position.bearing.toFloat(),
        followPhoneBearing = location.followingBearing,
        onCompassClick = location.onCompassClick,
        filterDropdownContent = {
            FilterMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false })
        },
        mapTypeContent = { BasemapMenu(selection = basemaps, extra = basemapMenuExtra) },
        layersContent = {
            MapLayersButton(
                overlays = overlays,
                onOverlaysChange = onOverlaysChange,
                offlineTarget =
                OfflineMapTarget(
                    styleUrl = (basemaps.current as? Basemap.Vector)?.styleUri,
                    bounds = { cameraState.viewport?.visibleBoundingBox },
                    zoom = { cameraState.position.zoom },
                    showRegion = { box -> scope.launch { cameraState.animateTo(boundingBox = box) } },
                ),
                extra = layersSheetExtra,
            )
        },
        onSitePlannerClick = onSitePlannerClick,
        isLocationTrackingEnabled = location.following,
        onToggleLocationTracking = location.onToggleFollow,
    )
}

/** State and callbacks backing the locate and compass buttons. */
@Stable
private class LocationControls(
    val state: LocationState,
    val following: Boolean,
    val followingBearing: Boolean,
    val onToggleFollow: () -> Unit,
    val onCompassClick: () -> Unit,
) {
    val bearingUpdate: BearingUpdate
        get() = if (followingBearing) BearingUpdate.TRACK_ORIENTATION else BearingUpdate.IGNORE
}

/**
 * Location tracking for the map, driven entirely by the two toolbar buttons.
 *
 * Starts off and stays off until pressed: `rememberLocationState` never requests permission on its own, which is what
 * lets the map open without a permission prompt. The provider is `rememberDefaultLocationProvider`, the GMS-free one —
 * fused location lives behind the separate `location-runtime-gms` artifact and must never enter an F-Droid build.
 */
@Composable
private fun rememberLocationControls(cameraState: CameraState): LocationControls {
    val scope = rememberCoroutineScope()

    var following by remember { mutableStateOf(false) }
    var followingBearing by remember { mutableStateOf(false) }
    var enableAfterPermission by remember { mutableStateOf(false) }

    val state =
        rememberLocationState(
            enabled = following,
            provider = rememberDefaultLocationProvider(),
            orientationProvider = rememberDefaultOrientationProvider(),
        )
    val settingsLauncher = rememberSystemSettingsLauncher()

    // The prompt is asynchronous, so a press that only asked for permission has to finish the job when the answer
    // arrives — otherwise the button appears to do nothing and has to be pressed twice.
    LaunchedEffect(state.permission) {
        if (enableAfterPermission && state.permission is LocationPermission.Granted) {
            enableAfterPermission = false
            following = true
        }
    }

    return LocationControls(
        state = state,
        following = following,
        followingBearing = followingBearing,
        onToggleFollow = {
            val permission = state.permission
            when {
                permission is LocationPermission.Granted -> {
                    following = !following
                    if (!following) followingBearing = false
                }

                // Denied for good: the system will not prompt again, so hand the user to app settings instead.
                permission is LocationPermission.NotGranted && permission.canRequest == false -> {
                    settingsLauncher.openApplicationSettings()
                }

                else -> {
                    enableAfterPermission = true
                    state.requestPermission()
                }
            }
        },
        // Matches the Google flavor: while following, the compass toggles heading-lock; otherwise it straightens the
        // map back to north.
        onCompassClick = {
            if (following) {
                followingBearing = !followingBearing
            } else {
                scope.launch { cameraState.animateTo(cameraState.position.copy(bearing = 0.0)) }
            }
        },
    )
}
