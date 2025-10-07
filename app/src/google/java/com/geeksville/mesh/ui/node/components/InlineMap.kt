/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.node.components

import androidx.compose.runtime.Composable
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.rememberCameraPositionState
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.component.NodeClusterMarkers
import org.meshtastic.feature.map.model.NodeClusterItem

@OptIn(MapsComposeExperimentalApi::class)
@Composable
internal actual fun InlineMap(node: Node) {
    val location = LatLng(node.latitude, node.longitude)
    val cameraState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(location, 15f) }
    GoogleMap(
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
        NodeClusterMarkers(
            nodeClusterItems =
            listOf(
                NodeClusterItem(
                    node = node,
                    nodePosition = location,
                    nodeTitle = node.user.shortName,
                    nodeSnippet = node.user.longName,
                ),
            ),
            mapFilterState =
            BaseMapViewModel.MapFilterState(
                showWaypoints = false,
                showPrecisionCircle = true,
                onlyFavorites = false,
                lastHeardFilter = LastHeardFilter.Any,
                lastHeardTrackFilter = LastHeardFilter.Any,
            ),
            navigateToNodeDetails = {},
            onClusterClick = { false },
        )
    }
}
