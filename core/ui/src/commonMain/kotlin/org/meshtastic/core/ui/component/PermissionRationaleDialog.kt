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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.grant_permission
import org.meshtastic.core.resources.not_now
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons

/**
 * The educational UI the Android permissions guidance requires before **re**-requesting a permission the user has
 * already declined once (`shouldShowRequestPermissionRationale` is true, i.e.
 * [org.meshtastic.core.ui.util.PermissionStatus.DENIED_CAN_RETRY]).
 *
 * Two things make this a rationale rather than a nag, and both are required by the guidance:
 * - [rationaleRes] must say what the feature does with the permission **and what stays disabled without it**.
 * - The dismiss action is a real way out, not a decoration. The next system prompt is the one that can turn a
 *   recoverable denial into a permanent one, so the user must be able to decline again without being cornered.
 *
 * Deliberately not shown for a first request: before any prompt, an extra dialog is friction with nothing to explain
 * that the system dialog does not already say.
 */
@Composable
fun PermissionRationaleDialog(
    titleRes: StringResource,
    rationaleRes: StringResource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = MeshtasticIcons.Info,
) {
    MeshtasticDialog(
        titleRes = titleRes,
        messageRes = rationaleRes,
        icon = icon,
        confirmTextRes = Res.string.grant_permission,
        onConfirm = onConfirm,
        dismissTextRes = Res.string.not_now,
        onDismiss = onDismiss,
    )
}
