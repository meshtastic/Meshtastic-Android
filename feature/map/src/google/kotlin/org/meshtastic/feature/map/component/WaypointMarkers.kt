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
package org.meshtastic.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.locked
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.proto.Waypoint

private const val DEG_D = 1e-7

@Composable
fun WaypointMarkers(
    displayableWaypoints: List<Waypoint>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    myNodeNum: Int,
    isConnected: Boolean,
    unicodeEmojiToBitmapProvider: (Int) -> BitmapDescriptor,
    onEditWaypointRequest: (Waypoint) -> Unit,
    selectedWaypointId: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    if (mapFilterState.showWaypoints) {
        displayableWaypoints.forEach { waypoint ->
            val markerState =
                rememberUpdatedMarkerState(
                    position = LatLng((waypoint.latitude_i ?: 0) * DEG_D, (waypoint.longitude_i ?: 0) * DEG_D),
                )

            LaunchedEffect(selectedWaypointId) {
                if (selectedWaypointId == waypoint.id) {
                    markerState.showInfoWindow()
                }
            }

            Marker(
                state = markerState,
                icon =
                if ((waypoint.icon ?: 0) == 0) {
                    unicodeEmojiToBitmapProvider(PUSHPIN) // Default icon (Round Pushpin)
                } else {
                    unicodeEmojiToBitmapProvider(waypoint.icon!!)
                },
                title = (waypoint.name ?: "").replace('\n', ' ').replace('\b', ' '),
                snippet = (waypoint.description ?: "").replace('\n', ' ').replace('\b', ' '),
                visible = true,
                onInfoWindowClick = {
                    if ((waypoint.locked_to ?: 0) == 0 || waypoint.locked_to == myNodeNum || !isConnected) {
                        onEditWaypointRequest(waypoint)
                    } else {
                        scope.launch { context.showToast(Res.string.locked) }
                    }
                },
            )
        }
    }
}

private const val PUSHPIN = 0x1F4CD // Unicode for Round Pushpin
