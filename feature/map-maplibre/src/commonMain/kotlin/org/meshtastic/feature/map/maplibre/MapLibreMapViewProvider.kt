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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
import org.meshtastic.feature.map.component.ClusterMemberEntry
import org.meshtastic.feature.map.component.ClusterMembersDialog
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.MapFilterActions
import org.meshtastic.feature.map.component.MapFilterMenu
import org.meshtastic.feature.map.layers.LayerOpacityStore
import org.meshtastic.feature.map.maplibre.component.BasemapButton
import org.meshtastic.feature.map.maplibre.component.BasemapSelection
import org.meshtastic.feature.map.maplibre.component.BoxAuthoringBar
import org.meshtastic.feature.map.maplibre.component.CustomTileSourcesMenuItem
import org.meshtastic.feature.map.maplibre.component.MapLayersButton
import org.meshtastic.feature.map.maplibre.component.MapZoom
import org.meshtastic.feature.map.maplibre.component.OfflineMapTarget
import org.meshtastic.feature.map.maplibre.component.WaypointDialogs
import org.meshtastic.feature.map.maplibre.component.WaypointEditing
import org.meshtastic.feature.map.maplibre.component.customRasterBasemaps
import org.meshtastic.feature.map.maplibre.component.rememberBasemapSelection
import org.meshtastic.feature.map.maplibre.component.rememberWaypointEditing
import org.meshtastic.feature.map.maplibre.geojson.ClusterMember
import org.meshtastic.feature.map.maplibre.layers.CustomLayer
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.MapOverlay

/**
 * MapLibre implementation of [MapViewProvider], shared by the F-Droid flavor and the desktop app.
 *
 * Not a Koin `@Single`: the two call sites construct it directly, which keeps this module free of any assumption about
 * how the host app wires its graph.
 */
class MapLibreMapViewProvider(
    /**
     * Supplies the user's imported overlays. A composable supplier rather than a value so the host can collect its own
     * state; both hosts read the shared layer store through
     * [rememberRenderableLayers][org.meshtastic.feature.map.maplibre.layers.rememberRenderableLayers].
     */
    private val customLayers: @Composable () -> List<CustomLayer> = { emptyList() },
    /**
     * Supplies the user's own raster tile sources. Composable for the same reason as [customLayers]. The default reads
     * the shared store, so every host gets them; the F-Droid app overrides only to add local MBTiles archives, which
     * need a file picker.
     */
    private val customBasemaps: @Composable () -> List<Basemap.Raster> = { customRasterBasemaps() },
    /**
     * Extra content for the foot of the basemap menu. Defaults to the shared tile-source editor, so desktop offers it
     * too; the F-Droid app overrides only to add the MBTiles file picker.
     */
    private val basemapMenuExtra: @Composable () -> Unit = { CustomTileSourcesMenuItem() },
    /**
     * Whether this host can actually download offline map packs.
     *
     * Off by default. MapLibre's offline API compiles everywhere and on desktop it creates a pack and reports it
     * without ever downloading a tile, so the control has to be opt-in per host rather than shown wherever it builds.
     */
    private val offlineMapsSupported: Boolean = false,
    /**
     * Presents an editor for the waypoint the map hands over. Defaults to the shared [EditWaypointDialog], so every
     * host gets waypoint creation without wiring anything; a host overrides this only to present its own editor. See
     * [WaypointEditRequest].
     */
    private val waypointEditor: @Composable (WaypointEditRequest) -> Unit = { request ->
        DefaultWaypointEditor(request)
    },
    /**
     * Runs a Site Planner session. Null hides the button entirely. The F-Droid app hosts the planner in a WebView;
     * desktop hands the same form to the system browser. See [SitePlannerSession].
     */
    private val sitePlanner: (@Composable (SitePlannerSession) -> Unit)? = null,
    /**
     * Extra content for the foot of the layers sheet. Both hosts mount the shared imported-layer manager there — the
     * picker behind its Add button is the platform-specific part, which is why the slot is the host's to fill.
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
        val screen = rememberMapScreenState(waypointId = waypointId, sitePlannerNodeNum = sitePlannerNodeNum)

        // Following the user means the screen is the thing being watched — the Google flavor holds it awake for the
        // same reason, and a map that sleeps mid-walk is the one complaint a location-follow feature always draws.
        KeepScreenOn(location.following)

        val layerOpacity by koinInject<LayerOpacityStore>().opacity.collectAsState()

        Box(modifier = modifier.fillMaxSize()) {
            MeshMap(
                viewModel = viewModel,
                navigateToNodeDetails = navigateToNodeDetails,
                modifier = Modifier.fillMaxSize(),
                basemap = basemaps.current,
                overlays = screen.overlays,
                layerOpacity = layerOpacity,
                customLayers = customLayers(),
                cameraState = cameraState,
                locationState = location.state,
                followLocation = location.following,
                bearingUpdate = location.bearingUpdate,
                frameOnNodes = rememberRestoredCamera(cameraState) == false,
                onWaypointClick = { screen.infoWaypointId = it },
                onClusterMembers = { screen.clusterMembers = it },
                onMapLongClick = waypoints.onLongPress,
                onMapClick = waypoints.onMapTap,
                boxCorner = waypoints.firstCorner,
            )

            MapZoom(cameraState = cameraState, basemap = basemaps.current)

            MapToolbar(
                basemaps = basemaps,
                location = location,
                cameraState = cameraState,
                overlays = screen.overlays,
                onOverlaysChange = { screen.overlays = it },
                basemapMenuExtra = basemapMenuExtra,
                layersSheetExtra = layersSheetExtra,
                offlineMapsSupported = offlineMapsSupported,
                onSitePlannerClick = sitePlanner?.let { { screen.plannerOpen = true } },
            )

            ClusterMembersSlot(screen.clusterMembers, navigateToNodeDetails) { screen.clusterMembers = emptyList() }

            SitePlannerSlot(
                open = screen.plannerOpen,
                nodeNum = sitePlannerNodeNum,
                cameraState = cameraState,
                planner = sitePlanner,
                onDismiss = { screen.plannerOpen = false },
            )

            BoxAuthoringSlot(editing = waypoints, cameraState = cameraState)

            WaypointDialogs(
                viewModel = viewModel,
                selectedId = screen.infoWaypointId,
                onSelectedIdChange = { screen.infoWaypointId = it },
                editing = waypoints,
                editor = waypointEditor,
            )
        }
    }
}

/** Everything the main map screen holds open over the map: dialogs, sheets and the chosen overlays. */
@Stable
private class MapScreenState {
    var infoWaypointId by mutableStateOf<Int?>(null)
    var clusterMembers by mutableStateOf(emptyList<ClusterMember>())
    var plannerOpen by mutableStateOf(false)
    var overlays by mutableStateOf(emptyList<MapOverlay>())
}

/** Holds the screen's open-thing state, and opens whatever the incoming deep link named. */
@Composable
private fun rememberMapScreenState(waypointId: Int?, sitePlannerNodeNum: Int?): MapScreenState {
    val state = remember { MapScreenState() }

    // Both of these this provider used to drop on the floor.
    LaunchedEffect(waypointId, sitePlannerNodeNum) {
        waypointId?.let { state.infoWaypointId = it }
        if (sitePlannerNodeNum != null) state.plannerOpen = true
    }
    return state
}

/**
 * The instruction bar shown while a geofence box is being drawn.
 *
 * While it is up the map itself is the editor, so this sits at the foot of the map rather than alongside the waypoint
 * dialogs — clear of the zoom pair and the attribution row that share that edge.
 */
@Composable
private fun BoxScope.BoxAuthoringSlot(editing: WaypointEditing, cameraState: CameraState) {
    if (editing.boxDraft == null) return

    BoxAuthoringBar(
        onCancel = editing.onCancelBox,
        onUseVisibleRegion = { cameraState.viewport?.visibleBoundingBox?.let(editing.onUseVisibleRegion) },
        modifier =
        Modifier.align(Alignment.BottomCenter)
            .padding(start = AUTHORING_BAR_SIDE.dp, end = AUTHORING_BAR_SIDE.dp, bottom = AUTHORING_BAR_BOTTOM.dp),
    )
}

/**
 * The cluster list, which clears the selection whichever way it is dismissed.
 *
 * The members arrive as cluster leaf features, carrying their own names; the node is looked up so the row can draw the
 * chip the rest of the app draws, and stays null for a node the database no longer has.
 */
@Composable
private fun ClusterMembersSlot(members: List<ClusterMember>, onPick: (Int) -> Unit, onClear: () -> Unit) {
    val viewModel: SharedMapViewModel = koinViewModel()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val byNum = remember(nodes) { nodes.associateBy { it.num } }

    ClusterMembersDialog(
        members =
        members.map { member ->
            ClusterMemberEntry(
                nodeNum = member.nodeNum,
                title = member.longName.ifBlank { member.shortName },
                subtitle = if (member.longName.isBlank()) "" else member.shortName,
                node = byNum[member.nodeNum],
            )
        },
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
 * The floating map toolbar, centred along the top edge.
 *
 * Centred because that is where the Google flavor and every other MapLibre surface here put it — this one map was flush
 * against the leading edge, which read as a different app on the same phone.
 *
 * Owns the filter menu's open state, which the shared overlay cannot: the button that opens it and the dropdown it
 * opens are separate parameters, so something has to hold the flag between them.
 */
@Composable
private fun BoxScope.MapToolbar(
    basemaps: BasemapSelection,
    location: LocationControls,
    cameraState: CameraState,
    overlays: List<MapOverlay>,
    onOverlaysChange: (List<MapOverlay>) -> Unit,
    basemapMenuExtra: @Composable () -> Unit,
    layersSheetExtra: @Composable () -> Unit,
    offlineMapsSupported: Boolean,
    onSitePlannerClick: (() -> Unit)?,
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MapControlsOverlay(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
        onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
        bearing = cameraState.position.bearing.toFloat(),
        followPhoneBearing = location.followingBearing,
        onCompassClick = location.onCompassClick,
        filterDropdownContent = {
            val filterViewModel: SharedMapViewModel = koinViewModel()
            val filterState by filterViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
            MapFilterMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { filterMenuExpanded = false },
                filterState = filterState,
                actions =
                MapFilterActions(
                    onToggleOnlyFavorites = filterViewModel::toggleOnlyFavorites,
                    onToggleShowWaypoints = filterViewModel::toggleShowWaypointsOnMap,
                    onToggleShowPrecisionCircle = filterViewModel::toggleShowPrecisionCircleOnMap,
                    onSelectLastHeard = filterViewModel::setLastHeardFilter,
                ),
            )
        },
        mapTypeContent = { BasemapButton(selection = basemaps, extra = basemapMenuExtra) },
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
                offlineMapsSupported = offlineMapsSupported,
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

/** Clear of the zoom pair and the attribution row, which share the foot of the map. */
private const val AUTHORING_BAR_BOTTOM = 72
private const val AUTHORING_BAR_SIDE = 16

/**
 * The editor every host gets unless it supplies its own.
 *
 * A default rather than a required argument because there is now one editor that builds everywhere:
 * `EditWaypointDialog` moved out of `androidMain` when its expiry picker stopped depending on
 * `android.app.DatePickerDialog`. While it was Android-only this slot had to be empty by default, and the desktop map —
 * which passed nothing — could not create a waypoint at all: the long press fired and set a pending waypoint that
 * nothing ever rendered.
 */
@Composable
private fun DefaultWaypointEditor(request: WaypointEditRequest) {
    val viewModel: SharedMapViewModel = koinViewModel()
    val displayUnits by viewModel.displayUnits.collectAsStateWithLifecycle()
    EditWaypointDialog(
        waypoint = request.waypoint,
        displayUnits = displayUnits,
        myNodeNum = viewModel.myNodeNum,
        onSend = request.onSend,
        onDelete = request.onDelete,
        onDismissRequest = request.onDismiss,
        onBeginBoxAuthoring = request.onBeginBoxAuthoring,
    )
}
