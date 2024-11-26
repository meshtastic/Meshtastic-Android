/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.R

@Composable
fun BitwisePreference(
    title: String,
    value: Int,
    enabled: Boolean,
    items: List<Pair<Int, String>>,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dropDownExpanded by remember { mutableStateOf(value = false) }

    RegularPreference(
        title = title,
        subtitle = value.toString(),
        onClick = { dropDownExpanded = !dropDownExpanded },
        enabled = enabled,
        trailingIcon = if (dropDownExpanded) {
            Icons.TwoTone.KeyboardArrowUp
        } else {
            Icons.TwoTone.KeyboardArrowDown
        },
    )

    Box {
        DropdownMenu(
            expanded = dropDownExpanded,
            onDismissRequest = { dropDownExpanded = !dropDownExpanded },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = { onItemSelected(value xor item.first) },
                    modifier = modifier.fillMaxWidth(),
                    content = {
                        Text(
                            text = item.second,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Checkbox(
                            modifier = modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End),
                            checked = value and item.first != 0,
                            onCheckedChange = { onItemSelected(value xor item.first) },
                            enabled = enabled,
                        )
                    }
                )
            }
            PreferenceFooter(
                enabled = enabled,
                negativeText = R.string.clear,
                onNegativeClicked = { onItemSelected(0) },
                positiveText = R.string.close,
                onPositiveClicked = { dropDownExpanded = false },
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
        enabled = true,
        items = listOf(1 to "TEST1", 2 to "TEST2"),
        onItemSelected = {}
    )
}
