/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.precisionBitsToMeters

private const val DEFAULT_ZOOM = 15f

@OptIn(MapsComposeExperimentalApi::class)
@Composable
internal fun InlineMap(node: Node, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val mapColorScheme =
        when (dark) {
            true -> ComposeMapColorScheme.DARK
            else -> ComposeMapColorScheme.LIGHT
        }
    key(node.num) {
        val location = LatLng(node.latitude, node.longitude)
        val cameraState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(location, DEFAULT_ZOOM)
        }

        GoogleMap(
            mapColorScheme = mapColorScheme,
            modifier = modifier,
            uiSettings =
            MapUiSettings(
                zoomControlsEnabled = true,
                mapToolbarEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                rotationGesturesEnabled = false,
                scrollGesturesEnabled = false,
                tiltGesturesEnabled = false,
                zoomGesturesEnabled = false,
            ),
            cameraPositionState = cameraState,
        ) {
            val precisionMeters = precisionBitsToMeters(node.position.precision_bits ?: 0)
            val latLng = LatLng(node.latitude, node.longitude)
            if (precisionMeters > 0) {
                Circle(
                    center = latLng,
                    radius = precisionMeters,
                    fillColor = Color(node.colors.second).copy(alpha = 0.2f),
                    strokeColor = Color(node.colors.second),
                    strokeWidth = 2f,
                )
            }
            MarkerComposable(state = rememberUpdatedMarkerState(position = latLng)) { NodeChip(node = node) }
        }
    }
}
