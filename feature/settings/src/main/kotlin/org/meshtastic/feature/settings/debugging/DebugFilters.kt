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
package org.meshtastic.feature.settings.debugging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.debug_active_filters
import org.meshtastic.core.strings.debug_filter_add
import org.meshtastic.core.strings.debug_filter_add_custom
import org.meshtastic.core.strings.debug_filter_clear
import org.meshtastic.core.strings.debug_filter_included
import org.meshtastic.core.strings.debug_filter_preset_title
import org.meshtastic.core.strings.debug_filters
import org.meshtastic.core.strings.match_all
import org.meshtastic.core.strings.match_any
import org.meshtastic.feature.settings.debugging.DebugViewModel.UiMeshLog

@Composable
fun DebugCustomFilterInput(
    customFilterText: String,
    onCustomFilterTextChange: (String) -> Unit,
    filterTexts: List<String>,
    onFilterTextsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = customFilterText,
            onValueChange = onCustomFilterTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(Res.string.debug_filter_add_custom)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
            KeyboardActions(
                onDone = {
                    if (customFilterText.isNotBlank()) {
                        onFilterTextsChange(filterTexts + customFilterText)
                        onCustomFilterTextChange("")
                    }
                },
            ),
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(
            onClick = {
                if (customFilterText.isNotBlank()) {
                    onFilterTextsChange(filterTexts + customFilterText)
                    onCustomFilterTextChange("")
                }
            },
            enabled = customFilterText.isNotBlank(),
        ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(Res.string.debug_filter_add))
        }
    }
}

@Composable
fun DebugPresetFilters(
    presetFilters: List<String>,
    filterTexts: List<String>,
    logs: List<UiMeshLog>,
    onFilterTextsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableFilters =
        presetFilters.filter { filter ->
            logs.any { log ->
                log.logMessage.contains(filter, ignoreCase = true) ||
                    log.messageType.contains(filter, ignoreCase = true) ||
                    log.formattedReceivedDate.contains(filter, ignoreCase = true)
            }
        }
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.debug_filter_preset_title),
            style = TextStyle(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 4.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            for (filter in availableFilters) {
                FilterChip(
                    selected = filter in filterTexts,
                    onClick = {
                        onFilterTextsChange(
                            if (filter in filterTexts) {
                                filterTexts - filter
                            } else {
                                filterTexts + filter
                            },
                        )
                    },
                    label = { Text(filter) },
                    leadingIcon = {
                        if (filter in filterTexts) {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = stringResource(Res.string.debug_filter_included),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun DebugFilterBar(
    filterTexts: List<String>,
    onFilterTextsChange: (List<String>) -> Unit,
    customFilterText: String,
    onCustomFilterTextChange: (String) -> Unit,
    presetFilters: List<String>,
    logs: List<UiMeshLog>,
    modifier: Modifier = Modifier,
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { showFilterMenu = !showFilterMenu }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.debug_filters),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                    Icon(
                        imageVector =
                        if (filterTexts.isNotEmpty()) {
                            Icons.TwoTone.FilterAlt
                        } else {
                            Icons.TwoTone.FilterAltOff
                        },
                        contentDescription = stringResource(Res.string.debug_filters),
                    )
                }
            }
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false },
                offset = DpOffset(0.dp, 8.dp),
            ) {
                Column(modifier = Modifier.padding(8.dp).width(300.dp)) {
                    DebugCustomFilterInput(
                        customFilterText = customFilterText,
                        onCustomFilterTextChange = onCustomFilterTextChange,
                        filterTexts = filterTexts,
                        onFilterTextsChange = onFilterTextsChange,
                    )
                    DebugPresetFilters(
                        presetFilters = presetFilters,
                        filterTexts = filterTexts,
                        logs = logs,
                        onFilterTextsChange = onFilterTextsChange,
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
fun DebugActiveFilters(
    filterTexts: List<String>,
    onFilterTextsChange: (List<String>) -> Unit,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    if (filterTexts.isNotEmpty()) {
        Column(modifier = modifier) {
            Row(
                modifier =
                Modifier.fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(colorScheme.background.copy(alpha = 1.0f)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.debug_active_filters),
                    style = TextStyle(fontWeight = FontWeight.Bold),
                )
                TextButton(
                    onClick = {
                        onFilterModeChange(
                            if (filterMode == FilterMode.OR) {
                                FilterMode.AND
                            } else {
                                FilterMode.OR
                            },
                        )
                    },
                ) {
                    Text(
                        if (filterMode == FilterMode.OR) {
                            stringResource(Res.string.match_any)
                        } else {
                            stringResource(Res.string.match_all)
                        },
                    )
                }
                IconButton(onClick = { onFilterTextsChange(emptyList()) }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(Res.string.debug_filter_clear),
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                for (filter in filterTexts) {
                    FilterChip(
                        selected = true,
                        onClick = { onFilterTextsChange(filterTexts - filter) },
                        label = { Text(filter) },
                        leadingIcon = { Icon(imageVector = Icons.TwoTone.FilterAlt, contentDescription = null) },
                        trailingIcon = { Icon(imageVector = Icons.Filled.Clear, contentDescription = null) },
                    )
                }
            }
        }
    }
}

enum class FilterMode {
    OR,
    AND,
}
