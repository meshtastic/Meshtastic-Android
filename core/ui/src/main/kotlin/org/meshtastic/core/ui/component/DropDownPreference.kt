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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun <T : Enum<T>> DropDownPreference(
    title: String,
    enabled: Boolean,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    DropDownPreference(
        title = title,
        enabled = enabled,
        items =
        selectedItem.declaringJavaClass.enumConstants?.filter { it.name != "UNRECOGNIZED" }?.map { it to it.name }
            ?: emptyList(),
        selectedItem = selectedItem,
        onItemSelected = onItemSelected,
        modifier = modifier,
        summary = summary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropDownPreference(
    title: String,
    enabled: Boolean,
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val deprecatedItems: List<T> = emptyList() // Protobuf-Java specific deprecation check removed
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                if (enabled) {
                    expanded = !expanded
                }
            },
        ) {
            OutlinedTextField(
                label = { Text(text = title) },
                modifier =
                Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
                readOnly = true,
                value = items.firstOrNull { it.first == selectedItem }?.second ?: "",
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                enabled = enabled,
                supportingText =
                if (summary != null) {
                    { Text(text = summary) }
                } else {
                    null
                },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items
                    .filterNot { it.first in deprecatedItems }
                    .forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.second) },
                            onClick = {
                                onItemSelected(selectionOption.first)
                                expanded = false
                            },
                        )
                    }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DropDownPreferencePreview() {
    DropDownPreference(
        title = "Settings",
        summary = "Lorem ipsum dolor sit amet",
        enabled = true,
        items = listOf("TEST1" to "text1", "TEST2" to "text2"),
        selectedItem = "TEST2",
        onItemSelected = {},
    )
}
