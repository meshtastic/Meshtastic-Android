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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
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
        val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()

        var overlays by remember { mutableStateOf(emptyList<MapOverlay>()) }
        var filterMenuExpanded by remember { mutableStateOf(false) }
        var basemapMenuExpanded by remember { mutableStateOf(false) }
        var overlayMenuExpanded by remember { mutableStateOf(false) }

        Box(modifier = modifier.fillMaxSize()) {
            MeshMap(
                viewModel = viewModel,
                navigateToNodeDetails = navigateToNodeDetails,
                modifier = Modifier.fillMaxSize(),
                basemap = basemap,
                overlays = overlays,
                customLayers = customLayers(),
            )

            MapControlsOverlay(
                onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                filterDropdownContent = {
                    DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
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
                },
                mapTypeContent = {
                    Box {
                        MapButton(
                            icon = MeshtasticIcons.Map,
                            contentDescription = stringResource(Res.string.map_tile_source),
                            onClick = { basemapMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = basemapMenuExpanded,
                            onDismissRequest = { basemapMenuExpanded = false },
                        ) {
                            Basemaps.all.forEach { entry ->
                                BasemapItem(
                                    basemap = entry,
                                    selected = entry.id == basemap.id,
                                    onClick = {
                                        mapPrefs.setMapStyle(Basemaps.all.indexOf(entry))
                                        basemapMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                layersContent = {
                    Box {
                        MapButton(
                            icon = MeshtasticIcons.Layers,
                            contentDescription = stringResource(Res.string.manage_map_layers),
                            onClick = { overlayMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = overlayMenuExpanded,
                            onDismissRequest = { overlayMenuExpanded = false },
                        ) {
                            MapOverlays.all.forEach { overlay ->
                                CheckableItem(
                                    label = overlay.label,
                                    checked = overlays.any { it.id == overlay.id },
                                    onClick = {
                                        overlays =
                                            if (overlays.any { it.id == overlay.id }) {
                                                overlays.filterNot { it.id == overlay.id }
                                            } else {
                                                overlays + overlay
                                            }
                                    },
                                )
                            }
                        }
                    }
                },
                // The site planner is launched from the F-Droid app's own scaffold and has no desktop host.
                onSitePlannerClick = null,
            )
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
