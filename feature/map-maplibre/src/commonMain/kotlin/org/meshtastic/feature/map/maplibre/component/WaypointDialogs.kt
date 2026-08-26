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
import org.meshtastic.core.model.isLocked
import org.meshtastic.core.model.isModifiableBy
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
                onBeginBoxAuthoring = editing.onBeginBox,
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
            viewModel.sendWaypoint(edited.readyToSend(viewModel::generatePacketId))
            pending = null
        },
        onDelete = { toDelete ->
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
    copy(id = if (id == 0) nextPacketId() else id, icon = if (icon == 0) DEFAULT_WAYPOINT_ICON else icon)

/** Waypoint coordinates travel as degrees scaled by 1e7. */
private const val DEG_SCALE = 1e-7

/** Round pushpin — what the Google flavor stamps on a waypoint saved without one. */
private const val DEFAULT_WAYPOINT_ICON = 0x1F4CD
