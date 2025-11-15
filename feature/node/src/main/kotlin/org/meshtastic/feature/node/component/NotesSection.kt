/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add_a_note
import org.meshtastic.core.strings.notes
import org.meshtastic.core.strings.save
import org.meshtastic.core.ui.component.TitledCard

@Composable
fun NotesSection(node: Node, onSaveNotes: (Int, String) -> Unit, modifier: Modifier = Modifier) {
    if (node.isFavorite) {
        TitledCard(title = stringResource(Res.string.notes), modifier = modifier) {
            val originalNotes = node.notes
            var notes by remember(node.notes) { mutableStateOf(node.notes) }
            val edited = notes.trim() != originalNotes.trim()
            val keyboardController = LocalSoftwareKeyboardController.current

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text(stringResource(Res.string.add_a_note)) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onSaveNotes(node.num, notes.trim())
                            keyboardController?.hide()
                        },
                        enabled = edited,
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = stringResource(Res.string.save))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                KeyboardActions(
                    onDone = {
                        onSaveNotes(node.num, notes.trim())
                        keyboardController?.hide()
                    },
                ),
            )
        }
    }
}
