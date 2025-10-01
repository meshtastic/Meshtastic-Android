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

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.ui.node.DEG_D
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.meshtastic.core.strings.R
import org.meshtastic.feature.map.BaseMapViewModel

@Composable
fun WaypointMarkers(
    displayableWaypoints: List<MeshProtos.Waypoint>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    myNodeNum: Int,
    isConnected: Boolean,
    unicodeEmojiToBitmapProvider: (Int) -> BitmapDescriptor,
    onEditWaypointRequest: (MeshProtos.Waypoint) -> Unit,
) {
    val context = LocalContext.current
    if (mapFilterState.showWaypoints) {
        displayableWaypoints.forEach { waypoint ->
            val markerState =
                rememberUpdatedMarkerState(position = LatLng(waypoint.latitudeI * DEG_D, waypoint.longitudeI * DEG_D))

            Marker(
                state = markerState,
                icon =
                if (waypoint.icon == 0) {
                    unicodeEmojiToBitmapProvider(PUSHPIN) // Default icon (Round Pushpin)
                } else {
                    unicodeEmojiToBitmapProvider(waypoint.icon)
                },
                title = waypoint.name.replace('\n', ' ').replace('\b', ' '),
                snippet = waypoint.description.replace('\n', ' ').replace('\b', ' '),
                visible = true,
                onInfoWindowClick = {
                    if (waypoint.lockedTo == 0 || waypoint.lockedTo == myNodeNum || !isConnected) {
                        onEditWaypointRequest(waypoint)
                    } else {
                        Toast.makeText(context, context.getString(R.string.locked), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

private const val PUSHPIN = 0x1F4CD // Unicode for Round Pushpin
