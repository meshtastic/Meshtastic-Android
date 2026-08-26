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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.delete_for_everyone
import org.meshtastic.core.resources.waypoint_delete
import org.meshtastic.core.ui.component.MeshtasticDialog

/**
 * Waypoint removal confirmation, shared by both map flavors. Confirming always drops our local copy — that needs no
 * mesh-wide permission, so it stays available for a waypoint locked to another node. Broadcasting the removal to the
 * mesh is the opt-in checkbox, offered only when [canDeleteForEveryone] (unlocked or locked to us, and connected).
 */
@Composable
fun DeleteWaypointDialog(
    canDeleteForEveryone: Boolean,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on eligibility so a mid-dialog disconnect clears the opt-in along with the checkbox that set it, rather
    // than leaving a stale selection that would broadcast on confirm.
    var alsoDeleteForEveryone by remember(canDeleteForEveryone) { mutableStateOf(false) }

    MeshtasticDialog(
        modifier = modifier,
        titleRes = Res.string.waypoint_delete,
        text =
        if (canDeleteForEveryone) {
            {
                // The whole row is one toggleable target so the label names the checkbox to a screen reader.
                Row(
                    modifier =
                    Modifier.fillMaxWidth()
                        .toggleable(
                            value = alsoDeleteForEveryone,
                            role = Role.Checkbox,
                            onValueChange = { alsoDeleteForEveryone = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = alsoDeleteForEveryone, onCheckedChange = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.delete_for_everyone))
                }
            }
        } else {
            null
        },
        confirmTextRes = Res.string.delete,
        onConfirm = { if (canDeleteForEveryone && alsoDeleteForEveryone) onDeleteForEveryone() else onDeleteForMe() },
        dismissTextRes = Res.string.cancel,
        onDismiss = onDismissRequest,
    )
}
