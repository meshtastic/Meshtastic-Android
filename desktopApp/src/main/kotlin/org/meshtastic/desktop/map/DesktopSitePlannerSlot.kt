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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.coverage.rememberCoverageEstimate
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.SitePlannerHost
import org.meshtastic.feature.map.component.toSitePlannerParams
import org.meshtastic.feature.map.layers.MapLayersManager
import org.meshtastic.feature.map.maplibre.SitePlannerSession

/** Runs the Site Planner for the desktop map; the estimate becomes a GeoJSON map layer and the map moves to it. */
@Composable
fun DesktopSitePlannerSlot(session: SitePlannerSession) {
    val sharedViewModel: SharedMapViewModel = koinViewModel()
    val layersManager: MapLayersManager = koinInject()

    val ourNode by sharedViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val channelSet by sharedViewModel.channelSet.collectAsStateWithLifecycle()
    val nodes by sharedViewModel.nodes.collectAsStateWithLifecycle()

    // A deep link names the node to plan for; a toolbar launch plans for whatever we are connected to.
    val subject = session.nodeNum?.let { num -> nodes.firstOrNull { it.num == num } } ?: ourNode

    SitePlannerHost(
        initialParams = subject.toSitePlannerParams(channelSet),
        estimate = rememberCoverageEstimate(),
        onDismiss = session.onDismiss,
        onImport = { name, geoJson, latitude, longitude ->
            layersManager.addGeoJsonLayer(name, geoJson)
            session.moveTo(Position(longitude = longitude, latitude = latitude))
        },
        onUseNodeLocation =
        subject?.takeIf { it.validPosition != null }?.let { node -> { node.latitude to node.longitude } },
        onUseMapCenter = { session.mapCenter().let { it.latitude to it.longitude } },
    )
}
