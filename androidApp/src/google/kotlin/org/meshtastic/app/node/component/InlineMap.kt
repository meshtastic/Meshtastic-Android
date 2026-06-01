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
package org.meshtastic.app.node.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.meshtastic.app.map.component.rememberNodeChipDescriptor
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.precisionBitsToMeters

private const val DEFAULT_ZOOM = 15f

@Composable
fun InlineMap(node: Node, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val mapColorScheme =
        when (dark) {
            true -> ComposeMapColorScheme.DARK
            else -> ComposeMapColorScheme.LIGHT
        }

    // Defensive workaround: propagate ViewTreeLifecycleOwner to the root view so that
    // any internal maps-compose ComposeView (e.g., info windows) can find the lifecycle
    // when walking up the view tree.
    //
    // IMPORTANT: capture and restore the previous owners on dispose. This InlineMap is hosted inside the
    // node-detail NavEntry, whose LocalLifecycleOwner is a transient, entry-scoped lifecycle. Leaving it
    // attached to the activity root view after the entry is destroyed (e.g. navigating back to the node
    // list) would make subsequently opened Popups/DropdownMenus inherit a DESTROYED lifecycle and
    // render at 0x0 (invisible). See the node-list popup regression.
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    DisposableEffect(lifecycleOwner, savedStateRegistryOwner) {
        val root = view.rootView
        val prevRootLifecycleOwner = root.findViewTreeLifecycleOwner()
        val prevRootSavedStateRegistryOwner = root.findViewTreeSavedStateRegistryOwner()
        root.setViewTreeLifecycleOwner(lifecycleOwner)
        root.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        onDispose {
            root.setViewTreeLifecycleOwner(prevRootLifecycleOwner)
            root.setViewTreeSavedStateRegistryOwner(prevRootSavedStateRegistryOwner)
        }
    }

    key(node.num) {
        val location = LatLng(node.latitude, node.longitude)
        val cameraState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(location, DEFAULT_ZOOM)
        }
        val markerIcon = rememberNodeChipDescriptor(node)

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
            val precisionMeters = precisionBitsToMeters(node.position.precision_bits)
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
            Marker(
                state = rememberUpdatedMarkerState(position = latLng),
                icon = markerIcon,
            )
        }
    }
}
