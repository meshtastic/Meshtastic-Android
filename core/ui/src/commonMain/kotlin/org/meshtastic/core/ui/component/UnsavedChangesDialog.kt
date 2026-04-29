/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.discard_changes
import org.meshtastic.core.resources.stay
import org.meshtastic.core.resources.unsaved_changes_message

@Composable
fun UnsavedChangesDialog(onDiscard: () -> Unit, onStay: () -> Unit) {
    MeshtasticDialog(
        titleRes = Res.string.cancel,
        messageRes = Res.string.unsaved_changes_message,
        confirmTextRes = Res.string.discard_changes,
        onConfirm = onDiscard,
        dismissTextRes = Res.string.stay,
        onDismiss = onStay,
    )
}
