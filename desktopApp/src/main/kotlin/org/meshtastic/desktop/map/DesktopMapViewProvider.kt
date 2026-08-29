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
package org.meshtastic.desktop.map

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import org.meshtastic.feature.map.layers.MapLayersManager
import org.meshtastic.feature.map.maplibre.MapLibreMapViewProvider
import org.meshtastic.feature.map.maplibre.component.ImportedLayersSlot
import org.meshtastic.feature.map.maplibre.layers.rememberRenderableLayers

/**
 * The desktop map provider: the shared MapLibre surfaces, the browser-handed Site Planner, and — through the same
 * imported-layer manager the Android flavours mount — the layers sheet that lets a Site Planner GeoJSON, or any
 * KML/KMZ/GeoJSON, be imported here too.
 */
internal fun desktopMapViewProvider(): MapLibreMapViewProvider = MapLibreMapViewProvider(
    customLayers = {
        val layersManager: MapLayersManager = koinInject()
        val importedLayers by layersManager.mapLayers.collectAsState()
        rememberRenderableLayers(layersManager, importedLayers.filter { it.isVisible })
    },
    sitePlanner = { session -> DesktopSitePlannerSlot(session) },
    layersSheetExtra = { ImportedLayersSlot() },
)
