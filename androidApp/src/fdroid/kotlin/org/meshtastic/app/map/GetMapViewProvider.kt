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
import org.meshtastic.app.map.component.ImportedLayersSlot
import org.meshtastic.app.map.component.SitePlannerSlot
import org.meshtastic.core.ui.util.MapViewProvider
import org.meshtastic.feature.map.maplibre.MapLibreMapViewProvider
import org.meshtastic.feature.map.maplibre.component.CustomTileSourcesMenuItem

fun getMapViewProvider(): MapViewProvider = MapLibreMapViewProvider(
    customLayers = {
        val layersManager: MapLayersManager = koinInject()
        val layers by layersManager.mapLayers.collectAsStateWithLifecycle()
        // KML and KMZ are converted to GeoJSON on the way through; everything else MapLibre fetches directly.
        rememberRenderableLayers(layers.filter { it.isVisible })
    },
    customBasemaps = { androidCustomRasterBasemaps() },
    // Everything but the picker is common now; Android is just the platform that has one.
    basemapMenuExtra = { CustomTileSourcesMenuItem(onAddLocalMbTiles = rememberMbTilesImport()) },
    // waypointEditor is not passed: EditWaypointDialog is multiplatform now and the provider defaults to it.
    // sitePlannerAvailable() has returned true on this flavor all along, but nothing consumed it — the MapLibre map
    // never offered the button and dropped the sitePlannerNodeNum deep link. Both are wired now.
    // MapLibre's offline packs actually download here; on desktop they never do, so the default is off.
    offlineMapsSupported = true,
    sitePlanner = { session -> SitePlannerSlot(session) },
    // The same imported-layer manager the Google flavour opens from its layers button.
    layersSheetExtra = { ImportedLayersSlot() },
)

/** Site Planner (coverage-estimate) — the F-Droid map renders imported coverage as a GeoJSON layer (see #6138). */
@Suppress("FunctionOnlyReturningConstant") // Flavor-dispatched: the google flavor returns a different value.
fun sitePlannerAvailable(): Boolean = true
