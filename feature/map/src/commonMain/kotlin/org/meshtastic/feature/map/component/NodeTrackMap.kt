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
package org.meshtastic.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.meshtastic.feature.map.model.MapStyle
import org.meshtastic.proto.Position
import org.maplibre.spatialk.geojson.Position as GeoPosition

private const val DEFAULT_TRACK_ZOOM = 13.0
private const val COORDINATE_SCALE = 1e-7

/**
 * Embeddable position-track map showing a polyline with markers for the given positions.
 *
 * Supports synchronized selection: [selectedPositionTime] highlights the corresponding marker and [onPositionSelected]
 * is called when a marker is tapped, passing the `Position.time` for the host screen to synchronize its card list.
 *
 * Replaces both the Google Maps and OSMDroid flavor-specific NodeTrackMap implementations.
 */
@Composable
fun NodeTrackMap(
    positions: List<Position>,
    modifier: Modifier = Modifier,
    selectedPositionTime: Int? = null,
    onPositionSelected: ((Int) -> Unit)? = null,
) {
    val center =
        remember(positions) {
            positions.firstOrNull()?.let { pos ->
                val lat = (pos.latitude_i ?: 0) * COORDINATE_SCALE
                val lng = (pos.longitude_i ?: 0) * COORDINATE_SCALE
                if (lat != 0.0 || lng != 0.0) GeoPosition(longitude = lng, latitude = lat) else null
            }
        }

    val cameraState =
        rememberCameraState(
            firstPosition =
            CameraPosition(
                target = center ?: GeoPosition(longitude = 0.0, latitude = 0.0),
                zoom = DEFAULT_TRACK_ZOOM,
            ),
        )

    MaplibreMap(modifier = modifier, baseStyle = MapStyle.OpenStreetMap.toBaseStyle(), cameraState = cameraState) {
        NodeTrackLayers(
            positions = positions,
            selectedPositionTime = selectedPositionTime,
            onPositionSelected = onPositionSelected,
        )
    }
}
