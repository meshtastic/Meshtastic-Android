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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.manage_map_layers
import org.meshtastic.core.resources.map_tile_source
import org.meshtastic.core.resources.only_favorites
import org.meshtastic.core.resources.show_precision_circle
import org.meshtastic.core.resources.show_waypoints
import org.meshtastic.core.ui.icon.Layers
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.MapViewProvider
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.maplibre.layers.CustomLayer
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.feature.map.maplibre.style.MapOverlay
import org.meshtastic.feature.map.maplibre.style.MapOverlays

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
) : MapViewProvider {

    @Composable
    override fun MapView(
        modifier: Modifier,
        navigateToNodeDetails: (Int) -> Unit,
        waypointId: Int?,
        sitePlannerNodeNum: Int?,
    ) {
        val viewModel: SharedMapViewModel = koinViewModel()
        val mapPrefs: MapPrefs = koinInject()

        val styleIndex by mapPrefs.mapStyle.collectAsStateWithLifecycle()
        val basemap = Basemaps.all.getOrElse(styleIndex) { Basemaps.default }

        val cameraState = rememberCameraState()
        val location = rememberLocationControls(cameraState)

        var overlays by remember { mutableStateOf(emptyList<MapOverlay>()) }
        // Owned here, not inside FilterMenu: the button that opens it lives in MapControlsOverlay and only reports
        // the press back through onToggleFilterMenu.
        var filterMenuExpanded by remember { mutableStateOf(false) }

        Box(modifier = modifier.fillMaxSize()) {
            MeshMap(
                viewModel = viewModel,
                navigateToNodeDetails = navigateToNodeDetails,
                modifier = Modifier.fillMaxSize(),
                basemap = basemap,
                overlays = overlays,
                customLayers = customLayers(),
                cameraState = cameraState,
                locationState = location.state,
                followLocation = location.following,
                bearingUpdate = location.bearingUpdate,
            )

            MapControlsOverlay(
                onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                bearing = cameraState.position.bearing.toFloat(),
                followPhoneBearing = location.followingBearing,
                onCompassClick = location.onCompassClick,
                filterDropdownContent = {
                    FilterMenu(
                        viewModel = viewModel,
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                    )
                },
                mapTypeContent = { BasemapMenu(basemap = basemap, onSelect = mapPrefs::setMapStyle) },
                layersContent = { OverlayMenu(selected = overlays, onSelectedChange = { overlays = it }) },
                // The site planner is launched from the F-Droid app's own scaffold and has no desktop host.
                onSitePlannerClick = null,
                isLocationTrackingEnabled = location.following,
                onToggleLocationTracking = location.onToggleFollow,
            )
        }
    }
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

@Composable
private fun FilterMenu(viewModel: BaseMapViewModel, expanded: Boolean, onDismissRequest: () -> Unit) {
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        CheckableItem(
            label = stringResource(Res.string.only_favorites),
            checked = filterState.onlyFavorites,
            onClick = viewModel::toggleOnlyFavorites,
        )
        CheckableItem(
            label = stringResource(Res.string.show_waypoints),
            checked = filterState.showWaypoints,
            onClick = viewModel::toggleShowWaypointsOnMap,
        )
        CheckableItem(
            label = stringResource(Res.string.show_precision_circle),
            checked = filterState.showPrecisionCircle,
            onClick = viewModel::toggleShowPrecisionCircleOnMap,
        )
    }
}

@Composable
private fun BasemapMenu(basemap: Basemap, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MapButton(
            icon = MeshtasticIcons.Map,
            contentDescription = stringResource(Res.string.map_tile_source),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Basemaps.all.forEach { entry ->
                BasemapItem(
                    basemap = entry,
                    selected = entry.id == basemap.id,
                    onClick = {
                        onSelect(Basemaps.all.indexOf(entry))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OverlayMenu(selected: List<MapOverlay>, onSelectedChange: (List<MapOverlay>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MapButton(
            icon = MeshtasticIcons.Layers,
            contentDescription = stringResource(Res.string.manage_map_layers),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapOverlays.all.forEach { overlay ->
                CheckableItem(
                    label = overlay.label,
                    checked = selected.any { it.id == overlay.id },
                    onClick = {
                        onSelectedChange(
                            if (selected.any { it.id == overlay.id }) {
                                selected.filterNot { it.id == overlay.id }
                            } else {
                                selected + overlay
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun BasemapItem(basemap: Basemap, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = basemap.label) },
        leadingIcon = { RadioButton(selected = selected, onClick = onClick) },
        onClick = onClick,
    )
}

@Composable
private fun CheckableItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = label) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = { onClick() }) },
        onClick = onClick,
    )
}
