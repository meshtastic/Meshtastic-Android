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
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.spatialk.geojson.BoundingBox
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.feature.map.model.MapStyle
import org.meshtastic.proto.Position
import org.maplibre.spatialk.geojson.Position as GeoPosition

private const val DEFAULT_TRACEROUTE_ZOOM = 10.0
private const val COORDINATE_SCALE = 1e-7
private const val BOUNDS_PADDING_DP = 64

/**
 * Embeddable traceroute map showing forward/return route polylines with hop markers.
 *
 * This composable is designed to be embedded inside a parent scaffold (e.g. TracerouteMapScreen). It does NOT include
 * its own Scaffold or AppBar.
 *
 * Replaces both the Google Maps and OSMDroid flavor-specific TracerouteMap implementations.
 */
@Composable
fun TracerouteMap(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    onMappableCountChanged: (shown: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val geoPositions =
        remember(tracerouteNodePositions) {
            tracerouteNodePositions.values.mapNotNull { pos ->
                val lat = (pos.latitude_i ?: 0) * COORDINATE_SCALE
                val lng = (pos.longitude_i ?: 0) * COORDINATE_SCALE
                if (lat != 0.0 || lng != 0.0) GeoPosition(longitude = lng, latitude = lat) else null
            }
        }

    val center = remember(geoPositions) { geoPositions.firstOrNull() }

    val boundingBox =
        remember(geoPositions) {
            if (geoPositions.size < 2) return@remember null
            val lats = geoPositions.map { it.latitude }
            val lngs = geoPositions.map { it.longitude }
            BoundingBox(
                southwest = GeoPosition(longitude = lngs.min(), latitude = lats.min()),
                northeast = GeoPosition(longitude = lngs.max(), latitude = lats.max()),
            )
        }

    val cameraState =
        rememberCameraState(
            firstPosition =
            CameraPosition(
                target = center ?: GeoPosition(longitude = 0.0, latitude = 0.0),
                zoom = DEFAULT_TRACEROUTE_ZOOM,
            ),
        )

    // Fit camera to bounds when the traceroute has multiple node positions.
    LaunchedEffect(boundingBox) {
        boundingBox?.let { cameraState.animateTo(boundingBox = it, padding = PaddingValues(BOUNDS_PADDING_DP.dp)) }
    }

    MaplibreMap(modifier = modifier, baseStyle = MapStyle.OpenStreetMap.toBaseStyle(), cameraState = cameraState) {
        TracerouteLayers(
            overlay = tracerouteOverlay,
            nodePositions = tracerouteNodePositions,
            nodes = emptyMap(), // Node lookups for short names are best-effort
            onMappableCountChanged = onMappableCountChanged,
        )
    }
}
