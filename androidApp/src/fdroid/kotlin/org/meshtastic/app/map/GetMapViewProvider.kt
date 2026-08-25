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
import org.meshtastic.core.ui.util.MapViewProvider
import org.meshtastic.feature.map.maplibre.MapLibreMapViewProvider
import org.meshtastic.feature.map.maplibre.layers.CustomLayer

fun getMapViewProvider(): MapViewProvider = MapLibreMapViewProvider(
    customLayers = {
        val layersManager: MapLayersManager = koinInject()
        val layers by layersManager.mapLayers.collectAsStateWithLifecycle()
        layers
            .filter { it.isVisible }
            // KML/KMZ still needs converting to GeoJSON before MapLibre can read it; GeoJSON and
            // Site Planner coverage estimates are already in a format it fetches directly.
            .filter { it.layerType != LayerType.KML }
            .mapNotNull { item -> item.uri?.let { uri -> CustomLayer(id = item.id, uri = uri.toString()) } }
    },
)

/** Site Planner (coverage-estimate) — the F-Droid map renders imported coverage as a GeoJSON layer (see #6138). */
@Suppress("FunctionOnlyReturningConstant") // Flavor-dispatched: the google flavor returns a different value.
fun sitePlannerAvailable(): Boolean = true
