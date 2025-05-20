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

package com.geeksville.mesh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import com.geeksville.mesh.R
import com.geeksville.mesh.model.NodeSortOption
import com.geeksville.mesh.ui.compose.preview.LargeFontPreview
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun NodeFilterTextField(
    modifier: Modifier = Modifier,
    filterText: String,
    onTextChange: (String) -> Unit,
    currentSortOption: NodeSortOption,
    onSortSelect: (NodeSortOption) -> Unit,
    includeUnknown: Boolean,
    onToggleIncludeUnknown: () -> Unit,
    showDetails: Boolean,
    onToggleShowDetails: () -> Unit,
    includeUnmessageable: Boolean,
    onToggleIncludeUnmessageable: () -> Unit,
) {
    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        NodeFilterTextField(
            filterText = filterText,
            onTextChange = onTextChange,
            modifier = Modifier.weight(1f)
        )

        NodeSortButton(
            modifier = Modifier.align(Alignment.CenterVertically),
            currentSortOption = currentSortOption,
            onSortSelect = onSortSelect,
            includeUnknown = includeUnknown,
            onToggleIncludeUnknown = onToggleIncludeUnknown,
            showDetails = showDetails,
            onToggleShowDetails = onToggleShowDetails,
            includeUnmessageable = includeUnmessageable,
            onToggleIncludeUnmessageable = onToggleIncludeUnmessageable
        )
    }
}

@Composable
private fun NodeFilterTextField(
    filterText: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .onFocusEvent { isFocused = it.isFocused },
        value = filterText,
        placeholder = {
            Text(
                text = stringResource(id = R.string.node_filter_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35F)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(id = R.string.node_filter_placeholder),
            )
        },
        onValueChange = onTextChange,
        trailingIcon = {
            if (filterText.isNotEmpty() || isFocused) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(id = R.string.desc_node_filter_clear),
                    modifier = Modifier.clickable {
                        onTextChange("")
                        focusManager.clearFocus()
                    }
                )
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground
        ),
        maxLines = 1,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        )
    )
}

@Suppress("LongMethod")
@Composable
private fun NodeSortButton(
    currentSortOption: NodeSortOption,
    onSortSelect: (NodeSortOption) -> Unit,
    includeUnknown: Boolean,
    onToggleIncludeUnknown: () -> Unit,
    showDetails: Boolean,
    onToggleShowDetails: () -> Unit,
    onToggleIncludeUnmessageable: () -> Unit,
    includeUnmessageable: Boolean,
    modifier: Modifier = Modifier,
) = Box(modifier) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.node_sort_button),
            modifier = Modifier.heightIn(max = 48.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 1f))
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
                        fontWeight = if (sort == currentSortOption) FontWeight.Bold else null,
                    )
                }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            onClick = {
                onToggleIncludeUnknown()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = includeUnknown) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.node_filter_include_unknown),
                    )
                }
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            onClick = {
                onToggleShowDetails()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = showDetails) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.node_filter_show_details),
                    )
                }
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            onClick = {
                onToggleIncludeUnmessageable()
                expanded = false
            },
            text = {
                Row {
                    AnimatedVisibility(visible = includeUnmessageable) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.node_filter_include_unmessageable),
                    )
                }
            }
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
            showDetails = false,
            onToggleShowDetails = {},
            includeUnmessageable = false,
            onToggleIncludeUnmessageable = {}
        )
    }
}
