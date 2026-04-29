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
@file:Suppress("MagicNumber")

package org.meshtastic.app.map.discovery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.core.ui.util.DiscoveryNeighborType

private const val DEFAULT_ZOOM = 12f
private const val BOUNDS_PADDING_PX = 100

private val DirectColor = Color(0xFF4CAF50)
private val MeshColor = Color(0xFF2196F3)
private val UserColor = Color(0xFFFF9800)
private val DirectLineColor = Color(0xFF4CAF50).copy(alpha = 0.5f)

/**
 * Google Maps implementation of the discovery map. Renders discovered node markers color-coded by neighbor type (green
 * = direct, blue = mesh) with polylines from the user position to direct neighbors. Auto-zooms to fit all markers.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun DiscoveryGoogleMap(
    userLatitude: Double,
    userLongitude: Double,
    nodes: List<DiscoveryMapNode>,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT

    val userLatLng = remember(userLatitude, userLongitude) { LatLng(userLatitude, userLongitude) }
    val hasValidUserPosition = userLatitude != 0.0 || userLongitude != 0.0
    val validNodes = remember(nodes) { nodes.filter { it.latitude != 0.0 || it.longitude != 0.0 } }

    val cameraState = rememberCameraPositionState {
        position =
            CameraPosition.fromLatLngZoom(if (hasValidUserPosition) userLatLng else LatLng(0.0, 0.0), DEFAULT_ZOOM)
    }

    // Auto-fit bounds on first composition
    LaunchedEffect(validNodes, hasValidUserPosition) {
        val allPoints = buildList {
            if (hasValidUserPosition) add(userLatLng)
            validNodes.forEach { add(LatLng(it.latitude, it.longitude)) }
        }
        if (allPoints.size >= 2) {
            val boundsBuilder = LatLngBounds.builder()
            allPoints.forEach { boundsBuilder.include(it) }
            cameraState.animate(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), BOUNDS_PADDING_PX))
        } else if (allPoints.size == 1) {
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(allPoints.first(), DEFAULT_ZOOM))
        }
    }

    GoogleMap(
        mapColorScheme = mapColorScheme,
        modifier = modifier,
        uiSettings =
        MapUiSettings(
            zoomControlsEnabled = true,
            mapToolbarEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
        ),
        cameraPositionState = cameraState,
    ) {
        // User position marker
        if (hasValidUserPosition) {
            MarkerComposable(state = rememberUpdatedMarkerState(position = userLatLng), title = "Your Position") {
                DiscoveryMarkerChip(label = "You", color = UserColor)
            }
        }

        // Node markers
        validNodes.forEach { node ->
            val nodeLatLng = LatLng(node.latitude, node.longitude)
            val markerColor =
                when (node.neighborType) {
                    DiscoveryNeighborType.DIRECT -> DirectColor
                    DiscoveryNeighborType.MESH -> MeshColor
                }
            val nodeIcon =
                if (node.isSensorNode) {
                    org.meshtastic.core.ui.icon.MeshtasticIcons.Temperature
                } else {
                    org.meshtastic.core.ui.icon.MeshtasticIcons.Person
                }
            MarkerComposable(
                state = rememberUpdatedMarkerState(position = nodeLatLng),
                title = node.longName ?: node.shortName ?: "Unknown",
                snippet = "SNR: ${node.snr} dB / RSSI: ${node.rssi} dBm",
            ) {
                DiscoveryMarkerChip(label = node.shortName ?: "?", color = markerColor, icon = nodeIcon)
            }
        }

        // Polylines from user to direct neighbors
        if (hasValidUserPosition) {
            validNodes
                .filter { it.neighborType == DiscoveryNeighborType.DIRECT }
                .forEach { node ->
                    Polyline(
                        points = listOf(userLatLng, LatLng(node.latitude, node.longitude)),
                        color = DirectLineColor,
                        width = 4f,
                    )
                }
        }
    }
}
