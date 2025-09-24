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

package com.geeksville.mesh.ui.node.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.NodeSortOption
import com.geeksville.mesh.ui.common.preview.LargeFontPreview
import com.geeksville.mesh.ui.common.theme.AppTheme
import org.meshtastic.core.strings.R

@Suppress("LongParameterList")
@Composable
fun NodeFilterTextField(
    modifier: Modifier = Modifier,
    filterText: String,
    onTextChange: (String) -> Unit,
    currentSortOption: NodeSortOption,
    onSortSelect: (NodeSortOption) -> Unit,
    includeUnknown: Boolean,
    onToggleIncludeUnknown: () -> Unit,
    onlyOnline: Boolean,
    onToggleOnlyOnline: () -> Unit,
    onlyDirect: Boolean,
    onToggleOnlyDirect: () -> Unit,
    showDetails: Boolean,
    onToggleShowDetails: () -> Unit,
    showIgnored: Boolean,
    onToggleShowIgnored: () -> Unit,
    ignoredNodeCount: Int,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Row {
            NodeFilterTextField(filterText = filterText, onTextChange = onTextChange, modifier = Modifier.weight(1f))

            NodeSortButton(
                modifier = Modifier.align(Alignment.CenterVertically),
                currentSortOption = currentSortOption,
                onSortSelect = onSortSelect,
                toggles =
                NodeFilterToggles(
                    includeUnknown = includeUnknown,
                    onToggleIncludeUnknown = onToggleIncludeUnknown,
                    onlyOnline = onlyOnline,
                    onToggleOnlyOnline = onToggleOnlyOnline,
                    onlyDirect = onlyDirect,
                    onToggleOnlyDirect = onToggleOnlyDirect,
                    showDetails = showDetails,
                    onToggleShowDetails = onToggleShowDetails,
                    showIgnored = showIgnored,
                    onToggleShowIgnored = onToggleShowIgnored,
                    ignoredNodeCount = ignoredNodeCount,
                ),
            )
        }
        if (showIgnored) {
            Box(
                modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceDim)
                    .clickable { onToggleShowIgnored() }
                    .padding(vertical = 16.dp, horizontal = 24.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.node_filter_ignored),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NodeFilterTextField(filterText: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier = modifier.defaultMinSize(minHeight = 48.dp).onFocusEvent { isFocused = it.isFocused },
        value = filterText,
        placeholder = {
            Text(
                text = stringResource(id = R.string.node_filter_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35F),
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = stringResource(id = R.string.node_filter_placeholder))
        },
        onValueChange = onTextChange,
        trailingIcon = {
            if (filterText.isNotEmpty() || isFocused) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(id = R.string.desc_node_filter_clear),
                    modifier =
                    Modifier.clickable {
                        onTextChange("")
                        focusManager.clearFocus()
                    },
                )
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
        maxLines = 1,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@Suppress("LongMethod")
@Composable
private fun NodeSortButton(
    currentSortOption: NodeSortOption,
    onSortSelect: (NodeSortOption) -> Unit,
    toggles: NodeFilterToggles,
    modifier: Modifier = Modifier,
) = Box(modifier) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.node_sort_button),
            modifier = Modifier.heightIn(max = 48.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 1f)),
    ) {
        NodeSortOption.entries.forEach { sort ->
            DropdownMenuItem(
                onClick = {
                    onSortSelect(sort)
                    expanded = false
                },
                text = {
                    Text(
                        text = stringResource(id = sort.stringRes),
                        fontWeight = if (sort == currentSortOption) FontWeight.ExtraBold else null,
                    )
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            onClick = {
                toggles.onToggleIncludeUnknown()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = toggles.includeUnknown) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(text = stringResource(id = R.string.node_filter_include_unknown))
                }
            },
        )
        DropdownMenuItem(
            onClick = {
                toggles.onToggleOnlyOnline()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = toggles.onlyOnline) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(text = stringResource(id = R.string.node_filter_only_online))
                }
            },
        )
        DropdownMenuItem(
            onClick = {
                toggles.onToggleOnlyDirect()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = toggles.onlyDirect) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(text = stringResource(id = R.string.node_filter_only_direct))
                }
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            onClick = {
                toggles.onToggleShowDetails()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = toggles.showDetails) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(text = stringResource(id = R.string.node_filter_show_details))
                }
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            onClick = {
                toggles.onToggleShowIgnored()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = toggles.showIgnored) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(text = stringResource(id = R.string.node_filter_show_ignored))
                    if (toggles.ignoredNodeCount > 0) {
                        Text(
                            text = " (${toggles.ignoredNodeCount})",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            },
        )
    }
}

@PreviewLightDark
@LargeFontPreview
@Composable
private fun NodeFilterTextFieldPreview() {
    AppTheme {
        NodeFilterTextField(
            filterText = "Filter text",
            onTextChange = {},
            currentSortOption = NodeSortOption.LAST_HEARD,
            onSortSelect = {},
            includeUnknown = false,
            onToggleIncludeUnknown = {},
            onlyOnline = false,
            onToggleOnlyOnline = {},
            onlyDirect = false,
            onToggleOnlyDirect = {},
            showDetails = false,
            onToggleShowDetails = {},
            showIgnored = false,
            onToggleShowIgnored = {},
            ignoredNodeCount = 0,
        )
    }
}

data class NodeFilterToggles(
    val includeUnknown: Boolean,
    val onToggleIncludeUnknown: () -> Unit,
    val onlyOnline: Boolean,
    val onToggleOnlyOnline: () -> Unit,
    val onlyDirect: Boolean,
    val onToggleOnlyDirect: () -> Unit,
    val showDetails: Boolean,
    val onToggleShowDetails: () -> Unit,
    val showIgnored: Boolean,
    val onToggleShowIgnored: () -> Unit,
    val ignoredNodeCount: Int,
)
