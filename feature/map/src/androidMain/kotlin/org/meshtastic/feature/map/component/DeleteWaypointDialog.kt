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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.delete_for_everyone
import org.meshtastic.core.resources.delete_for_me
import org.meshtastic.core.resources.waypoint_delete

/**
 * Waypoint removal confirmation, shared by both map flavors. "Delete for me" drops only our local copy and is always
 * offered — a locked waypoint belongs to another node mesh-wide, but our own database is ours to prune. "Delete for
 * everyone" broadcasts the removal and so is gated on [canDeleteForEveryone] (unlocked or locked to us, and connected).
 */
@Composable
fun DeleteWaypointDialog(
    canDeleteForEveryone: Boolean,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.waypoint_delete)) },
        // Up to three actions, none of them abbreviable, so they stack instead of crowding a single row. All of them
        // live in the confirm slot to keep that stack in one column; dismissing is the Cancel entry.
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onDeleteForMe) {
                    Text(text = stringResource(Res.string.delete_for_me), color = MaterialTheme.colorScheme.error)
                }
                if (canDeleteForEveryone) {
                    TextButton(onClick = onDeleteForEveryone) {
                        Text(
                            text = stringResource(Res.string.delete_for_everyone),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.cancel)) }
            }
        },
        dismissButton = null,
        modifier = modifier,
    )
}
