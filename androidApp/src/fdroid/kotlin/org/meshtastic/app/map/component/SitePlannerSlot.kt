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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.SitePlannerHost
import org.meshtastic.app.map.toSitePlannerParams
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.maplibre.SitePlannerSession

/**
 * Runs the hosted Site Planner for the F-Droid map.
 *
 * The planner lives in `androidApp`, so the flavor supplies it rather than the shared map module. Imported coverage
 * becomes a GeoJSON map layer (see #6138) and the map recentres on the transmitter so it is on screen.
 *
 * No phone-GPS shortcut is offered: the Google flavor fills that from Play Services' fused location, which must not
 * enter an F-Droid build. The coordinate fields stay manual, with the map centre and the node's own position as
 * shortcuts.
 */
@Composable
fun SitePlannerSlot(session: SitePlannerSession) {
    val sharedViewModel: SharedMapViewModel = koinViewModel()
    val mapViewModel: MapViewModel = koinViewModel()

    val ourNode by sharedViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val channelSet by sharedViewModel.channelSet.collectAsStateWithLifecycle()
    val nodes by sharedViewModel.nodes.collectAsStateWithLifecycle()

    // A deep link names the node to plan for; a toolbar launch plans for whatever we are connected to.
    val subject = session.nodeNum?.let { num -> nodes.firstOrNull { it.num == num } } ?: ourNode

    SitePlannerHost(
        initialParams = subject.toSitePlannerParams(channelSet),
        onDismiss = session.onDismiss,
        onImport = { name, geoJson, latitude, longitude ->
            mapViewModel.addGeoJsonLayer(name, geoJson)
            session.moveTo(Position(longitude = longitude, latitude = latitude))
        },
        onUseNodeLocation =
        subject?.takeIf { it.validPosition != null }?.let { node -> { node.latitude to node.longitude } },
        onUseMapCenter = { session.mapCenter().let { it.latitude to it.longitude } },
    )
}
