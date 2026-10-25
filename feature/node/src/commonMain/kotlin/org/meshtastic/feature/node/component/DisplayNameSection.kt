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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.MAX_DISPLAY_NAME_BYTES
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.limitToBytes
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.display_name
import org.meshtastic.core.resources.display_name_hint
import org.meshtastic.core.resources.save
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Save

/** Edits the local-only display name. The broadcast long name is the placeholder, so the user sees what is replaced. */
@Composable
fun DisplayNameSection(node: Node, onSaveDisplayName: (Int, String) -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(Res.string.display_name),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            var name by remember(node.num, node.displayName) { mutableStateOf(node.displayName) }
            val edited = name.trim() != node.displayName.trim()
            val keyboardController = LocalSoftwareKeyboardController.current

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trimStart().limitToBytes(MAX_DISPLAY_NAME_BYTES) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(node.user.long_name) },
                supportingText = { Text(stringResource(Res.string.display_name_hint)) },
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onSaveDisplayName(node.num, name.trim())
                            keyboardController?.hide()
                        },
                        enabled = edited,
                    ) {
                        Icon(imageVector = MeshtasticIcons.Save, contentDescription = stringResource(Res.string.save))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                KeyboardActions(
                    onDone = {
                        onSaveDisplayName(node.num, name.trim())
                        keyboardController?.hide()
                    },
                ),
            )
        }
    }
}
