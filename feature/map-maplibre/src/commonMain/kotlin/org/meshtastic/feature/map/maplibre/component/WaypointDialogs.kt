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
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.isModifiableBy
import org.meshtastic.core.model.util.waypointIconOrDefault
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.DeleteWaypointDialog
import org.meshtastic.feature.map.component.WaypointInfoDialog
import org.meshtastic.feature.map.maplibre.WaypointEditRequest
import org.meshtastic.proto.Waypoint
import kotlin.math.abs
import org.meshtastic.proto.BoundingBox as ProtoBoundingBox

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
            WaypointInfoSlot(
                waypoint = waypoint,
                myNodeNum = viewModel.myNodeNum,
                isConnected = isConnected,
                displayUnits = displayUnits,
                alertsEnabled = waypoint.id in alertOptIns,
                onToggleAlerts = { viewModel.setGeofenceAlertOptIn(waypoint.id, it) },
                onDismiss = { onSelectedIdChange(null) },
                onEdit = {
                    onSelectedIdChange(null)
                    editing.onEdit(waypoint)
                },
                onDeleteForMe = {
                    onSelectedIdChange(null)
                    deletingId = waypoint.id
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
                onBeginBoxAuthoring = editing.onBeginBox,
            ),
        )
    }

    deletingId?.let { id ->
        waypoints[id]?.waypoint?.let { waypoint ->
            WaypointRemoval(
                // Deleting for everyone re-broadcasts an expiry, so it needs a live connection.
                canDeleteForEveryone = waypoint.removableForEveryone(viewModel.myNodeNum, isConnected),
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

/**
 * The tapped waypoint's details, offering only the actions the current state allows.
 *
 * Stateless so both gates can be exercised without a view model.
 */
@Composable
internal fun WaypointInfoSlot(
    waypoint: Waypoint,
    myNodeNum: Int?,
    isConnected: Boolean,
    displayUnits: MeasurementSystem,
    alertsEnabled: Boolean,
    onToggleAlerts: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
) {
    WaypointInfoDialog(
        waypoint = waypoint,
        displayUnits = displayUnits,
        alertsEnabled = alertsEnabled,
        onToggleAlerts = onToggleAlerts,
        onDismissRequest = onDismiss,
        // Editing re-broadcasts, so it needs a connection and mesh-wide permission: unlocked, or locked to us.
        // `isLocked` alone made our own locked waypoint read-only, with no other route to the editor.
        onEdit = if (waypoint.isModifiableBy(myNodeNum) && isConnected) onEdit else null,
        // Dropping our local copy is not a mesh operation, so a foreign lock does not withhold it.
        onDeleteForMe = onDeleteForMe,
    )
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

/**
 * Long-press placement, the waypoint currently being edited, and the geofence box being drawn for one.
 *
 * @property boxDraft Non-null while the user is defining a bounding box by tapping the map. The editor is closed for
 *   the duration and reopens with the box applied.
 * @property firstCorner The first of the two corner taps, once made. Drawn on the map so the tap reads as registered.
 */
@Stable
internal class WaypointEditing(
    val onLongPress: (Position) -> Unit,
    val pending: Waypoint?,
    val onEdit: (Waypoint) -> Unit,
    val onSend: (Waypoint) -> Unit,
    val onDelete: (Waypoint) -> Unit,
    val onDone: () -> Unit,
    val boxDraft: Waypoint?,
    val firstCorner: Position?,
    val onBeginBox: (Waypoint) -> Unit,
    val onMapTap: (Position) -> Unit,
    val onCancelBox: () -> Unit,
    val onUseVisibleRegion: (BoundingBox) -> Unit,
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
    val box = rememberBoxAuthoring(onApplyBox = { pending = it }, onReopenEditor = { pending = it })

    return WaypointEditing(
        // Not while a box is being drawn: the map is the editor for the duration, and every press on it belongs to
        // that flow. Without this a long press mid-box drops an unrelated waypoint into it, which is the one part of
        // the Google flavor's own guard (`isMainMode && isConnected && boxAuthoringDraft == null`) this had missed.
        onLongPress = { position ->
            if (isConnected && box.draft == null) {
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
            viewModel.sendWaypoint(edited.readyToSend(viewModel::generatePacketId))
            pending = null
        },
        onDelete = { toDelete ->
            // Tell the mesh, not just ourselves. This dropped only the local copy, so deleting from inside the editor
            // left the waypoint on everyone else's map — while deleting the same waypoint from its info dialog, two
            // taps away, removed it properly. The Google flavor broadcasts from both.
            if (toDelete.removableForEveryone(viewModel.myNodeNum, isConnected)) {
                viewModel.sendWaypoint(toDelete.copy(expire = 1))
            }
            viewModel.deleteWaypoint(toDelete.id)
            pending = null
        },
        onDone = { pending = null },
        boxDraft = box.draft,
        firstCorner = box.firstCorner,
        onBeginBox = { draft ->
            // The editor has to close: the map underneath it is what the user is about to tap.
            pending = null
            box.onBegin(draft)
        },
        onMapTap = box.onTap,
        onCancelBox = box.onCancel,
        onUseVisibleRegion = box.onUseVisibleRegion,
    )
}

/** The two-tap geofence box flow, held apart from the editor so each stays readable. */
@Stable
private class BoxAuthoring(
    val draft: Waypoint?,
    val firstCorner: Position?,
    val onBegin: (Waypoint) -> Unit,
    val onTap: (Position) -> Unit,
    val onCancel: () -> Unit,
    val onUseVisibleRegion: (BoundingBox) -> Unit,
)

/**
 * Collects two opposite corner taps into a bounding box.
 *
 * @param onApplyBox the draft, with its box, to hand back to the editor.
 * @param onReopenEditor the draft unchanged, when the user cancels.
 */
@Composable
private fun rememberBoxAuthoring(onApplyBox: (Waypoint) -> Unit, onReopenEditor: (Waypoint) -> Unit): BoxAuthoring {
    var draft by remember { mutableStateOf<Waypoint?>(null) }
    var firstCorner by remember { mutableStateOf<Position?>(null) }

    fun apply(corners: ProtoBoundingBox) {
        draft?.let { onApplyBox(it.copy(bounding_box = corners)) }
        draft = null
        firstCorner = null
    }

    return BoxAuthoring(
        draft = draft,
        firstCorner = firstCorner,
        onBegin = { starting ->
            draft = starting
            firstCorner = null
        },
        onTap = { tapped ->
            val first = firstCorner
            // A second tap almost on top of the first would define a zero-area box, so it is ignored and the flow
            // keeps waiting rather than committing something unusable.
            when {
                draft == null -> Unit
                first == null -> firstCorner = tapped
                first.isDistinctFrom(tapped) -> apply(boundingBoxFromCorners(first, tapped))
                else -> Unit
            }
        },
        onCancel = {
            draft?.let(onReopenEditor)
            draft = null
            firstCorner = null
        },
        onUseVisibleRegion = { visible ->
            apply(
                boundingBoxFromCorners(
                    Position(longitude = visible.west, latitude = visible.south),
                    Position(longitude = visible.east, latitude = visible.north),
                ),
            )
        },
    )
}

/**
 * Whether two taps are far enough apart to describe a box.
 *
 * About 11 m, the same floor the Google flavor applies. Below it the box would be degenerate and the camera could not
 * frame it.
 */
private fun Position.isDistinctFrom(other: Position): Boolean =
    abs(latitude - other.latitude) >= MIN_CORNER_DELTA_DEG && abs(longitude - other.longitude) >= MIN_CORNER_DELTA_DEG

/** A proto bounding box (degrees x 1e7) from two opposite corners, in either order. */
internal fun boundingBoxFromCorners(a: Position, b: Position): ProtoBoundingBox = ProtoBoundingBox(
    longitude_west_i = (minOf(a.longitude, b.longitude) / DEG_SCALE).toInt(),
    latitude_south_i = (minOf(a.latitude, b.latitude) / DEG_SCALE).toInt(),
    longitude_east_i = (maxOf(a.longitude, b.longitude) / DEG_SCALE).toInt(),
    latitude_north_i = (maxOf(a.latitude, b.latitude) / DEG_SCALE).toInt(),
)

private const val MIN_CORNER_DELTA_DEG = 1e-4

/** A new waypoint arrives with id 0 and no icon; it needs both before it goes on air. */
private fun Waypoint.readyToSend(nextPacketId: () -> Int): Waypoint =
    copy(id = if (id == 0) nextPacketId() else id, icon = icon.waypointIconOrDefault())

/** Waypoint coordinates travel as degrees scaled by 1e7. */
private const val DEG_SCALE = 1e-7

/**
 * Whether removing this waypoint can be broadcast to the mesh rather than only dropped locally.
 *
 * Removal travels as an expiry the other nodes honour, so it needs a waypoint we are allowed to modify, one that has
 * been sent at all, and a live connection to send it over.
 */
private fun Waypoint.removableForEveryone(myNodeNum: Int?, isConnected: Boolean): Boolean =
    isModifiableBy(myNodeNum) && isConnected && id != 0
