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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.ui.util.MapViewProvider
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.MapControlsOverlay
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
class MapLibreMapViewProvider : MapViewProvider {

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

        var overlays by remember { mutableStateOf(emptyList<MapOverlay>()) }
        var showFilterMenu by remember { mutableStateOf(false) }
        var showBasemapMenu by remember { mutableStateOf(false) }

        Box(modifier = modifier.fillMaxSize()) {
            MeshMap(
                viewModel = viewModel,
                navigateToNodeDetails = navigateToNodeDetails,
                modifier = Modifier.fillMaxSize(),
                basemap = basemap,
                overlays = overlays,
            )

            MapControlsOverlay(
                onToggleFilterMenu = { showFilterMenu = !showFilterMenu },
                mapTypeContent = {
                    BasemapMenu(
                        expanded = showBasemapMenu,
                        selected = basemap,
                        onDismiss = { showBasemapMenu = false },
                        onSelect = { chosen ->
                            mapPrefs.setMapStyle(Basemaps.all.indexOf(chosen))
                            showBasemapMenu = false
                        },
                    )
                },
                layersContent = {
                    OverlayMenu(
                        enabled = overlays,
                        onToggle = { overlay ->
                            overlays =
                                if (overlays.any { it.id == overlay.id }) {
                                    overlays.filterNot { it.id == overlay.id }
                                } else {
                                    overlays + overlay
                                }
                        },
                    )
                },
                // The site planner has no desktop host and is launched from the F-Droid app's own
                // scaffold, so it is deliberately not offered from here.
                onSitePlannerClick = null,
            )
        }
    }
}

@Composable
private fun BasemapMenu(expanded: Boolean, selected: Basemap, onDismiss: () -> Unit, onSelect: (Basemap) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Basemaps.all.forEach { basemap ->
            DropdownMenuItem(
                text = { Text(text = basemap.label) },
                leadingIcon = { RadioButton(selected = basemap.id == selected.id, onClick = { onSelect(basemap) }) },
                onClick = { onSelect(basemap) },
            )
        }
    }
}

@Composable
private fun OverlayMenu(enabled: List<MapOverlay>, onToggle: (MapOverlay) -> Unit) {
    MapOverlays.all.forEach { overlay ->
        DropdownMenuItem(
            text = { Text(text = overlay.label) },
            leadingIcon = {
                Checkbox(checked = enabled.any { it.id == overlay.id }, onCheckedChange = { onToggle(overlay) })
            },
            onClick = { onToggle(overlay) },
        )
    }
}
