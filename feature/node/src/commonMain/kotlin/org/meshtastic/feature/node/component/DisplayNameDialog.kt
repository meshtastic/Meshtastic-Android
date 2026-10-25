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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.MAX_DISPLAY_NAME_BYTES
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.limitToBytes
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.display_name_hint
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.set_display_name

/** Node-list entry point for the local display name. Saving an empty name clears it. */
@Composable
fun DisplayNameDialog(node: Node, onSave: (Int, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(node.num) { mutableStateOf(node.displayName) }
    val save = {
        onSave(node.num, name.trim())
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.set_display_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trimStart().limitToBytes(MAX_DISPLAY_NAME_BYTES) },
                singleLine = true,
                placeholder = { Text(node.user.long_name) },
                supportingText = { Text(stringResource(Res.string.display_name_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = { TextButton(onClick = { save() }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
