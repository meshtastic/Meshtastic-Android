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
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.app.map.convertIntToEmoji
import org.meshtastic.core.model.geofence.toGeofence
import org.meshtastic.core.model.isLocked
import org.meshtastic.core.model.isModifiableBy
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.geofence
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
    isMyWaypoint: (Int) -> Boolean,
    onShowGeofenceInfo: (Waypoint) -> Unit,
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

            // Non-visual cue: the geofence is otherwise only an orange overlay, so surface it in the marker's
            // accessible snippet for screen-reader and color-challenged users.
            val description = waypoint.description.replace('\n', ' ').replace('\b', ' ')
            val snippet =
                if (waypoint.toGeofence() != null) {
                    val geofenceLabel = stringResource(Res.string.geofence)
                    if (description.isBlank()) geofenceLabel else "$description · $geofenceLabel"
                } else {
                    description
                }

            // Lock cue in the info-window title (parity with the fdroid marker), so a locked waypoint is
            // identifiable rather than only surfacing as a "locked" toast on tap.
            val cleanName = waypoint.name.replace('\n', ' ').replace('\b', ' ')
            val title = if (waypoint.isLocked) "${convertIntToEmoji(LOCK)} $cleanName" else cleanName

            Marker(
                state = markerState,
                icon = icon,
                title = title,
                snippet = snippet,
                visible = true,
                onInfoWindowClick = {
                    when {
                        // Foreign geofences: read-only view hosting the receiver-local crossing-alert opt-in.
                        waypoint.toGeofence() != null && !isMyWaypoint(waypoint.id) -> onShowGeofenceInfo(waypoint)

                        waypoint.isModifiableBy(myNodeNum) || !isConnected -> onEditWaypointRequest(waypoint)

                        else -> scope.launch { context.showToast(Res.string.locked) }
                    }
                },
            )
        }
    }
}

private const val PUSHPIN = 0x1F4CD // Unicode for Round Pushpin
private const val LOCK = 0x1F512 // Unicode for Lock
