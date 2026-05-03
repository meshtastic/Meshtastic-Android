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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.jvm.JvmName

@Composable
fun <T : Enum<T>> DropDownPreference(
    title: String,
    enabled: Boolean,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    itemIcon: @Composable ((T) -> ImageVector)? = null,
    itemColor: @Composable ((T) -> Color)? = null,
    itemLabel: @Composable ((T) -> String)? = null,
) {
    val enumConstants =
        remember(selectedItem) {
            enumEntriesOf(selectedItem).filter { it.name != "UNRECOGNIZED" && !it.isDeprecatedEnumEntry() }
        }

    val items =
        enumConstants.map {
            val label = itemLabel?.invoke(it) ?: it.name
            val icon = itemIcon?.invoke(it)
            val color = itemColor?.invoke(it)
            DropDownItem(it, label, icon, color)
        }

    DropDownPreference(
        title = title,
        enabled = enabled,
        items = items,
        selectedItem = selectedItem,
        onItemSelected = onItemSelected,
        modifier = modifier,
        summary = summary,
    )
}

data class DropDownItem<T>(val value: T, val label: String, val icon: ImageVector? = null, val color: Color? = null)

@JvmName("DropDownPreferencePairs")
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
    DropDownPreference(
        title = title,
        enabled = enabled,
        items = items.map { DropDownItem(it.first, it.second) },
        selectedItem = selectedItem,
        onItemSelected = onItemSelected,
        modifier = modifier,
        summary = summary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun <T> DropDownPreference(
    title: String,
    enabled: Boolean,
    items: List<DropDownItem<T>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                if (enabled) {
                    expanded = !expanded
                }
            },
        ) {
            val currentItem = items.firstOrNull { it.value == selectedItem }
            OutlinedTextField(
                label = { Text(text = title) },
                modifier =
                Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
                readOnly = true,
                value = currentItem?.label ?: "",
                onValueChange = {},
                leadingIcon =
                currentItem?.icon?.let {
                    {
                        Icon(
                            imageVector = it,
                            contentDescription = currentItem.label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                    ?: currentItem?.color?.let {
                        {
                            Icon(
                                painter = ColorPainter(it),
                                contentDescription = currentItem.label,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified,
                            )
                        }
                    },
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
                items.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionOption.icon != null) {
                                    Icon(
                                        imageVector = selectionOption.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                } else if (selectionOption.color != null) {
                                    Icon(
                                        painter = ColorPainter(selectionOption.color),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(selectionOption.label)
                            }
                        },
                        onClick = {
                            onItemSelected(selectionOption.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

internal expect fun <T : Enum<T>> enumEntriesOf(selectedItem: T): List<T>

internal expect fun Enum<*>.isDeprecatedEnumEntry(): Boolean

@Preview(showBackground = true)
@Composable
private fun DropDownPreferencePreview() {
    DropDownPreference(
        title = "Settings",
        summary = "Lorem ipsum dolor sit amet",
        enabled = true,
        items = listOf(DropDownItem("TEST1", "text1"), DropDownItem("TEST2", "text2")),
        selectedItem = "TEST2",
        onItemSelected = {},
    )
}
