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
package org.meshtastic.app.map

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.component.CustomTileSourcesMenuItem
import org.meshtastic.app.map.component.ImportedLayersSlot
import org.meshtastic.app.map.component.SitePlannerSlot
import org.meshtastic.core.ui.util.MapViewProvider
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.maplibre.MapLibreMapViewProvider

fun getMapViewProvider(): MapViewProvider = MapLibreMapViewProvider(
    customLayers = {
        val layersManager: MapLayersManager = koinInject()
        val layers by layersManager.mapLayers.collectAsStateWithLifecycle()
        // KML and KMZ are converted to GeoJSON on the way through; everything else MapLibre fetches directly.
        rememberRenderableLayers(layers.filter { it.isVisible })
    },
    customBasemaps = { customRasterBasemaps() },
    basemapMenuExtra = { CustomTileSourcesMenuItem() },
    // EditWaypointDialog drives android.app.DatePickerDialog for the expiry picker, so it cannot live in the map
    // module's shared source set. The flavor supplies it; desktop goes without until there is a multiplatform
    // editor.
    waypointEditor = { request ->
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
    },
    // sitePlannerAvailable() has returned true on this flavor all along, but nothing consumed it — the MapLibre map
    // never offered the button and dropped the sitePlannerNodeNum deep link. Both are wired now.
    sitePlanner = { session -> SitePlannerSlot(session) },
    // The same imported-layer manager the Google flavour opens from its layers button.
    layersSheetExtra = { ImportedLayersSlot() },
)

/** Site Planner (coverage-estimate) — the F-Droid map renders imported coverage as a GeoJSON layer (see #6138). */
@Suppress("FunctionOnlyReturningConstant") // Flavor-dispatched: the google flavor returns a different value.
fun sitePlannerAvailable(): Boolean = true
