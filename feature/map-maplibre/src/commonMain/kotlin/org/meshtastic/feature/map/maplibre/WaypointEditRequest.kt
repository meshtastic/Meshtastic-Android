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
package org.meshtastic.feature.map.maplibre

import androidx.compose.runtime.Stable
import org.meshtastic.proto.Waypoint

/**
 * A waypoint the map is asking its host to present an editor for.
 *
 * The map owns everything except the editor itself: it decides when to ask, assigns a packet id and default icon, and
 * performs the send or delete. All the host has to do is show a dialog and call back.
 *
 * The editor is a slot so a host can substitute its own, but it no longer has to supply one: `EditWaypointDialog`
 * builds for every target since its expiry picker moved from `android.app.DatePickerDialog` to Material 3's, so the
 * provider defaults to it and desktop creates waypoints like anything else.
 *
 * @property waypoint The waypoint to edit. A new one carries `id == 0` and only its coordinates.
 * @property onBeginBoxAuthoring Hands the draft back so the user can define its geofence bounding box by tapping the
 *   map, which only the map can offer. The editor closes; the map re-opens it with the box applied.
 *   `EditWaypointDialog` has taken this callback all along — the F-Droid host simply never passed it, so the button did
 *   nothing.
 */
@Stable
class WaypointEditRequest(
    val waypoint: Waypoint,
    val onSend: (Waypoint) -> Unit,
    val onDelete: (Waypoint) -> Unit,
    val onDismiss: () -> Unit,
    val onBeginBoxAuthoring: (Waypoint) -> Unit,
)
