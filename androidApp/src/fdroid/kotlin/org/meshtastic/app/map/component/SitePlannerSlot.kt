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
package org.meshtastic.app.map.component

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.feature.map.maplibre.MapLibreSitePlannerSlot
import org.meshtastic.feature.map.maplibre.SitePlannerSession

/**
 * Runs the Site Planner for the F-Droid map. The Google flavor fills the coordinates from Play Services' fused
 * location, which must not enter an F-Droid build, so this one stays manual.
 */
@Composable
fun SitePlannerSlot(session: SitePlannerSession) {
    val mapViewModel: MapViewModel = koinViewModel()
    MapLibreSitePlannerSlot(session) { name, geoJson -> mapViewModel.addGeoJsonLayer(name, geoJson) }
}
