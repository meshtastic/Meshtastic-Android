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
package org.meshtastic.app.map.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberComposeBitmapDescriptor
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch
import org.meshtastic.app.map.convertIntToEmoji
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.locked
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.proto.Waypoint

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun WaypointMarkers(
    displayableWaypoints: List<Waypoint>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    myNodeNum: Int,
    isConnected: Boolean,
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

            val iconCodePoint = if (waypoint.icon == 0) PUSHPIN else waypoint.icon
            val emojiText = convertIntToEmoji(iconCodePoint)
            val icon =
                rememberComposeBitmapDescriptor(iconCodePoint) {
                    Text(text = emojiText, fontSize = 32.sp, modifier = Modifier.padding(2.dp))
                }

            Marker(
                state = markerState,
                icon = icon,
                title = waypoint.name.replace('\n', ' ').replace('\b', ' '),
                snippet = waypoint.description.replace('\n', ' ').replace('\b', ' '),
                visible = true,
                onInfoWindowClick = {
                    if (waypoint.locked_to == 0 || waypoint.locked_to == myNodeNum || !isConnected) {
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
