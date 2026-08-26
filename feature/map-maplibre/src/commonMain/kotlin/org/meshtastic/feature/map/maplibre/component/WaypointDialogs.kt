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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.isLocked
import org.meshtastic.core.model.isModifiableBy
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.DeleteWaypointDialog
import org.meshtastic.feature.map.component.WaypointInfoDialog
import org.meshtastic.feature.map.maplibre.WaypointEditRequest
import org.meshtastic.proto.Waypoint

/**
 * Every waypoint dialog the map can show: details for the tapped one, the host's editor, and delete confirmation.
 *
 * Owns the editing and deletion steps itself, so the caller only has to track which waypoint is open.
 */
@Composable
internal fun WaypointDialogs(
    viewModel: SharedMapViewModel,
    selectedId: Int?,
    onSelectedIdChange: (Int?) -> Unit,
    editing: WaypointEditing,
    editor: @Composable (WaypointEditRequest) -> Unit,
) {
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val displayUnits by viewModel.displayUnits.collectAsStateWithLifecycle()
    val alertOptIns by viewModel.geofenceAlertOptIns.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()

    var deletingId by remember { mutableStateOf<Int?>(null) }

    selectedId?.let { id ->
        waypoints[id]?.waypoint?.let { waypoint ->
            WaypointInfoDialog(
                waypoint = waypoint,
                displayUnits = displayUnits,
                alertsEnabled = waypoint.id in alertOptIns,
                onToggleAlerts = { viewModel.setGeofenceAlertOptIn(waypoint.id, it) },
                onDismissRequest = { onSelectedIdChange(null) },
                // Editing re-broadcasts, so it needs a connection — and a locked foreign geofence stays read-only.
                onEdit =
                if (!waypoint.isLocked && isConnected) {
                    {
                        onSelectedIdChange(null)
                        editing.onEdit(waypoint)
                    }
                } else {
                    null
                },
                onDeleteForMe =
                if (!waypoint.isLocked) {
                    {
                        onSelectedIdChange(null)
                        deletingId = waypoint.id
                    }
                } else {
                    null
                },
            )
        }
    }

    editing.pending?.let { waypoint ->
        editor(
            WaypointEditRequest(
                waypoint = waypoint,
                onSend = editing.onSend,
                onDelete = editing.onDelete,
                onDismiss = editing.onDone,
            ),
        )
    }

    deletingId?.let { id ->
        waypoints[id]?.waypoint?.let { waypoint ->
            WaypointRemoval(
                // Deleting for everyone re-broadcasts an expiry, so it needs a live connection.
                canDeleteForEveryone = waypoint.isModifiableBy(viewModel.myNodeNum) && isConnected && waypoint.id != 0,
                onDeleteForMe = { viewModel.deleteWaypoint(waypoint.id) },
                onDeleteForEveryone = {
                    viewModel.sendWaypoint(waypoint.copy(expire = 1))
                    viewModel.deleteWaypoint(waypoint.id)
                },
                onDone = { deletingId = null },
            )
        }
    }
}

/** Delete confirmation, wrapped so dismissing and acting both clear the pending waypoint. */
@Composable
private fun WaypointRemoval(
    canDeleteForEveryone: Boolean,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDone: () -> Unit,
) {
    DeleteWaypointDialog(
        canDeleteForEveryone = canDeleteForEveryone,
        onDeleteForMe = {
            onDeleteForMe()
            onDone()
        },
        onDeleteForEveryone = {
            onDeleteForEveryone()
            onDone()
        },
        onDismissRequest = onDone,
    )
}

/** Long-press placement and the waypoint currently being edited. */
@Stable
internal class WaypointEditing(
    val onLongPress: (Position) -> Unit,
    val pending: Waypoint?,
    val onEdit: (Waypoint) -> Unit,
    val onSend: (Waypoint) -> Unit,
    val onDelete: (Waypoint) -> Unit,
    val onDone: () -> Unit,
)

/**
 * Tracks which waypoint the host is being asked to edit.
 *
 * A long press places a new one at the pressed position, which is how the Google flavor does it too. Creation needs a
 * live connection, since saving a waypoint means broadcasting it.
 */
@Composable
internal fun rememberWaypointEditing(): WaypointEditing {
    val viewModel: SharedMapViewModel = koinViewModel()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Waypoint?>(null) }

    return WaypointEditing(
        onLongPress = { position ->
            if (isConnected) {
                pending =
                    Waypoint(
                        latitude_i = (position.latitude / DEG_SCALE).toInt(),
                        longitude_i = (position.longitude / DEG_SCALE).toInt(),
                    )
            }
        },
        pending = pending,
        onEdit = { pending = it },
        onSend = { edited ->
            // A new waypoint arrives with id 0 and no icon; give it both before it goes on air.
            var outgoing = edited
            if (outgoing.id == 0) outgoing = outgoing.copy(id = viewModel.generatePacketId())
            if (outgoing.icon == 0) outgoing = outgoing.copy(icon = DEFAULT_WAYPOINT_ICON)
            viewModel.sendWaypoint(outgoing)
            pending = null
        },
        onDelete = { toDelete ->
            viewModel.deleteWaypoint(toDelete.id)
            pending = null
        },
        onDone = { pending = null },
    )
}

/** Waypoint coordinates travel as degrees scaled by 1e7. */
private const val DEG_SCALE = 1e-7

/** Round pushpin — what the Google flavor stamps on a waypoint saved without one. */
private const val DEFAULT_WAYPOINT_ICON = 0x1F4CD
