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

package com.geeksville.mesh.ui.map.components

import androidx.compose.runtime.Composable
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.node.DegD
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
fun WaypointMarkers(
    displayableWaypoints: List<MeshProtos.Waypoint>,
    mapFilterState: UIViewModel.MapFilterState, // Assuming MapFilterState is accessible
    myNodeNum: Int,
    isConnected: Boolean,
    unicodeEmojiToBitmapProvider: (Int) -> BitmapDescriptor,
    onEditWaypointRequest: (MeshProtos.Waypoint) -> Unit
) {
    if (mapFilterState.showWaypoints) {
        displayableWaypoints.forEach { waypoint ->
            // Use rememberMarkerState instead of rememberUpdatedMarkerState if the position doesn't change frequently after initial display
            // For dynamic updates to position, ensure the key for rememberMarkerState changes or use rememberUpdatedMarkerState
            val markerState = rememberUpdatedMarkerState(
                position = LatLng(
                    waypoint.latitudeI * DegD,
                    waypoint.longitudeI * DegD
                )
            )

            Marker(
                state = markerState,
                icon = if (waypoint.icon == 0) {
                    unicodeEmojiToBitmapProvider(0x1F4CD) // Default icon (Round Pushpin)
                } else {
                    unicodeEmojiToBitmapProvider(waypoint.icon)
                },
                title = waypoint.name,
                snippet = waypoint.description,
                visible = true, // Visibility is controlled by the condition above
                onInfoWindowClick = {
                    // Check if editable
                    if (
                        waypoint.lockedTo == 0 ||
                        waypoint.lockedTo == myNodeNum ||
                        !isConnected
                    ) {
                        onEditWaypointRequest(waypoint)
                    } else {
                        // Optionally show a toast that it's locked by someone else
                        // Or simply do nothing to prevent editing.
                    }
                }
            )
        }
    }
}

