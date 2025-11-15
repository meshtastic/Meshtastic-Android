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

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.clear
import org.meshtastic.core.strings.close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitwisePreference(
    title: String,
    value: Int,
    enabled: Boolean,
    items: List<Pair<Int, String>>,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) {
                expanded = !expanded
            }
        },
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            readOnly = true,
            value = value.toString(),
            onValueChange = {},
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            enabled = enabled,
            supportingText = { if (summary != null) Text(text = summary) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(text = item.second, overflow = TextOverflow.Ellipsis)
                        Checkbox(
                            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End),
                            checked = value and item.first != 0,
                            onCheckedChange = { onItemSelected(value xor item.first) },
                            enabled = enabled,
                        )
                    },
                    onClick = { onItemSelected(value xor item.first) },
                )
            }
            PreferenceFooter(
                enabled = enabled,
                negativeText = Res.string.clear,
                onNegativeClicked = { onItemSelected(0) },
                positiveText = Res.string.close,
                onPositiveClicked = { expanded = false },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BitwisePreferencePreview() {
    BitwisePreference(
        title = "Settings",
        value = 3,
        summary = "This is a summary",
        enabled = true,
        items = listOf(1 to "TEST1", 2 to "TEST2"),
        onItemSelected = {},
    )
}
