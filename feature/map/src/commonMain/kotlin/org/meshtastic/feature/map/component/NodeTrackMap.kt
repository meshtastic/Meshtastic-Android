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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.spatialk.geojson.BoundingBox
import org.meshtastic.feature.map.model.MapStyle
import org.meshtastic.feature.map.util.toGeoPositionOrNull
import org.meshtastic.proto.Position

private const val DEFAULT_TRACK_ZOOM = 13.0
private const val BOUNDS_PADDING_DP = 48

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
    val geoPositions =
        remember(positions) { positions.mapNotNull { pos -> toGeoPositionOrNull(pos.latitude_i, pos.longitude_i) } }

    val center = remember(geoPositions) { geoPositions.firstOrNull() }

    val boundingBox =
        remember(geoPositions) {
            if (geoPositions.size < 2) return@remember null
            val lats = geoPositions.map { it.latitude }
            val lngs = geoPositions.map { it.longitude }
            BoundingBox(
                southwest = org.maplibre.spatialk.geojson.Position(longitude = lngs.min(), latitude = lats.min()),
                northeast = org.maplibre.spatialk.geojson.Position(longitude = lngs.max(), latitude = lats.max()),
            )
        }

    val cameraState =
        rememberCameraState(
            firstPosition =
            CameraPosition(
                target = center ?: org.maplibre.spatialk.geojson.Position(longitude = 0.0, latitude = 0.0),
                zoom = DEFAULT_TRACK_ZOOM,
            ),
        )

    // Fit camera to bounds when the track has multiple positions.
    LaunchedEffect(boundingBox) {
        boundingBox?.let { cameraState.animateTo(boundingBox = it, padding = PaddingValues(BOUNDS_PADDING_DP.dp)) }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = MapStyle.OpenStreetMap.toBaseStyle(),
        cameraState = cameraState,
        options =
        MapOptions(gestureOptions = GestureOptions.RotationLocked, ornamentOptions = OrnamentOptions.AllEnabled),
    ) {
        NodeTrackLayers(
            positions = positions,
            selectedPositionTime = selectedPositionTime,
            onPositionSelected = onPositionSelected,
        )
    }
}
