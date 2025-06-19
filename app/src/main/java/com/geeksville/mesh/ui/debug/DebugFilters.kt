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

package com.geeksville.mesh.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.twotone.FilterAlt
import androidx.compose.material.icons.twotone.FilterAltOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun DebugCustomFilterInput(
    customFilterText: String,
    onCustomFilterTextChange: (String) -> Unit,
    filterTexts: List<String>,
    onFilterTextsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = customFilterText,
            onValueChange = onCustomFilterTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add custom filter") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (customFilterText.isNotBlank()) {
                        onFilterTextsChange(filterTexts + customFilterText)
                        onCustomFilterTextChange("")
                    }
                }
            )
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(
            onClick = {
                if (customFilterText.isNotBlank()) {
                    onFilterTextsChange(filterTexts + customFilterText)
                    onCustomFilterTextChange("")
                }
            },
            enabled = customFilterText.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add filter"
            )
        }
    }
}

@Composable
internal fun DebugPresetFilters(
    presetFilters: List<String>,
    filterTexts: List<String>,
    onFilterTextsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Preset Filters",
            style = TextStyle(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (filter in presetFilters) {
                FilterChip(
                    selected = filter in filterTexts,
                    onClick = {
                        onFilterTextsChange(
                            if (filter in filterTexts) {
                                filterTexts - filter
                            } else {
                                filterTexts + filter
                            }
                        )
                    },
                    label = { Text(filter) },
                    leadingIcon = { if (filter in filterTexts) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Done icon",
                        )
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun DebugFilterBar(
    filterTexts: List<String>,
    onFilterTextsChange: (List<String>) -> Unit,
    customFilterText: String,
    onCustomFilterTextChange: (String) -> Unit,
    presetFilters: List<String>,
    modifier: Modifier = Modifier
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(
                onClick = { showFilterMenu = !showFilterMenu }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.debug_filters),
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                    Icon(
                        imageVector = if (filterTexts.isNotEmpty()) {
                            Icons.TwoTone.FilterAlt
                        } else {
                            Icons.TwoTone.FilterAltOff
                        },
                        contentDescription = "Filter"
                    )
                }
            }
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false },
                offset = DpOffset(0.dp, 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .width(300.dp)
                ) {
                    DebugCustomFilterInput(
                        customFilterText = customFilterText,
                        onCustomFilterTextChange = onCustomFilterTextChange,
                        filterTexts = filterTexts,
                        onFilterTextsChange = onFilterTextsChange
                    )
                    DebugPresetFilters(
                        presetFilters = presetFilters,
                        filterTexts = filterTexts,
                        onFilterTextsChange = onFilterTextsChange
                    )
                }
            }
        }
    }
}

@Composable
internal fun DebugActiveFilters(
    filterTexts: List<String>,
    onFilterTextsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    if (filterTexts.isNotEmpty()) {
        Column(modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(colorScheme.background.copy(alpha = 1.0f)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.debug_active_filters),
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
                IconButton(
                    onClick = { onFilterTextsChange(emptyList()) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear all filters"
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                for (filter in filterTexts) {
                    FilterChip(
                        selected = true,
                        onClick = {
                            onFilterTextsChange(filterTexts - filter)
                        },
                        label = { Text(filter) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.TwoTone.FilterAlt,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}
