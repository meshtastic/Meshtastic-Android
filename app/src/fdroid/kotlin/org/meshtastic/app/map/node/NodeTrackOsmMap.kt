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
package org.meshtastic.app.map.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import org.meshtastic.app.map.addCopyright
import org.meshtastic.app.map.addPolyline
import org.meshtastic.app.map.addPositionMarkers
import org.meshtastic.app.map.addScaleBarOverlay
import org.meshtastic.app.map.model.CustomTileSource
import org.meshtastic.app.map.rememberMapViewWithLifecycle
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.proto.Position
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/**
 * A focused OSMDroid map composable that renders **only** a node's position track — a dashed polyline with directional
 * markers for each historical position.
 *
 * Unlike the monolithic [org.meshtastic.app.map.MapView], this composable does **not** include node clusters,
 * waypoints, location tracking, or any map controls. It is designed to be embedded inside the position-log adaptive
 * layout.
 */
@Composable
fun NodeTrackOsmMap(positions: List<Position>, applicationId: String, mapStyleId: Int, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val geoPoints =
        remember(positions) { positions.map { GeoPoint((it.latitude_i ?: 0) * DEG_D, (it.longitude_i ?: 0) * DEG_D) } }
    val cameraView = remember(geoPoints) { BoundingBox.fromGeoPoints(geoPoints) }
    val mapView =
        rememberMapViewWithLifecycle(
            applicationId = applicationId,
            box = cameraView,
            tileSource = CustomTileSource.getTileSource(mapStyleId),
        )

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            map.addCopyright()
            map.addScaleBarOverlay(density)
            map.addPolyline(density, geoPoints) {}
            map.addPositionMarkers(positions) {}
        },
    )
}
