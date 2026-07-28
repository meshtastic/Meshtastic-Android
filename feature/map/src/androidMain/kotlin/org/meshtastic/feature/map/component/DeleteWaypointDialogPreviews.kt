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

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.ui.theme.AppTheme

/** Waypoint locked to another node (or offline): confirming removes only our local copy. */
@PreviewLightDark
@Composable
@Suppress("PreviewPublic")
fun DeleteWaypointDialogLocalOnlyPreview() {
    AppTheme {
        DeleteWaypointDialog(
            canDeleteForEveryone = false,
            onDeleteForMe = {},
            onDeleteForEveryone = {},
            onDismissRequest = {},
        )
    }
}

/** Waypoint we may modify mesh-wide while connected: the broadcast opt-in is offered. */
@PreviewLightDark
@Composable
@Suppress("PreviewPublic")
fun DeleteWaypointDialogWithBroadcastPreview() {
    AppTheme {
        DeleteWaypointDialog(
            canDeleteForEveryone = true,
            onDeleteForMe = {},
            onDeleteForEveryone = {},
            onDismissRequest = {},
        )
    }
}
