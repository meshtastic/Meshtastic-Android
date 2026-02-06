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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.debug_default_search
import org.meshtastic.core.strings.debug_logs_export
import org.meshtastic.core.strings.debug_search_clear
import org.meshtastic.core.strings.debug_search_next
import org.meshtastic.core.strings.debug_search_prev
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.debugging.DebugViewModel.UiMeshLog
import org.meshtastic.feature.settings.debugging.LogSearchManager.SearchMatch
import org.meshtastic.feature.settings.debugging.LogSearchManager.SearchState

@Composable
fun DebugSearchNavigation(
    searchState: SearchState,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.width(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${searchState.currentMatchIndex + 1}/${searchState.allMatches.size}",
            modifier = Modifier.padding(end = 4.dp),
            style = TextStyle(fontSize = 12.sp),
        )
        IconButton(onClick = onPreviousMatch, enabled = searchState.hasMatches, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(Res.string.debug_search_prev),
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onNextMatch, enabled = searchState.hasMatches, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.debug_search_next),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun DebugSearchBar(
    searchState: SearchState,
    onSearchTextChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = searchState.searchText,
        onValueChange = onSearchTextChange,
        modifier = modifier.then(Modifier.padding(end = 8.dp)),
        placeholder = { Text(stringResource(Res.string.debug_default_search)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
        KeyboardActions(
            onSearch = {
                // Clear focus when search is performed
            },
        ),
        trailingIcon = {
            Row(
                modifier = Modifier.width(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchState.hasMatches) {
                    DebugSearchNavigation(
                        searchState = searchState,
                        onNextMatch = onNextMatch,
                        onPreviousMatch = onPreviousMatch,
                    )
                }
                if (searchState.searchText.isNotEmpty()) {
                    IconButton(onClick = onClearSearch, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(Res.string.debug_search_clear),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun DebugSearchState(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    filterTexts: List<String>,
    presetFilters: List<String>,
    logs: List<UiMeshLog>,
    onSearchTextChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onClearSearch: () -> Unit,
    onFilterTextsChange: (List<String>) -> Unit,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    onExportLogs: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    var customFilterText by remember { mutableStateOf("") }

    Column(modifier = modifier.background(color = colorScheme.background.copy(alpha = 1.0f)).padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(colorScheme.background.copy(alpha = 1.0f)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DebugSearchBar(
                modifier = Modifier.weight(1f),
                searchState = searchState,
                onSearchTextChange = onSearchTextChange,
                onNextMatch = onNextMatch,
                onPreviousMatch = onPreviousMatch,
                onClearSearch = onClearSearch,
            )
            DebugFilterBar(
                filterTexts = filterTexts,
                onFilterTextsChange = onFilterTextsChange,
                customFilterText = customFilterText,
                onCustomFilterTextChange = { customFilterText = it },
                presetFilters = presetFilters,
                logs = logs,
                modifier = Modifier,
            )
            onExportLogs?.let { onExport ->
                IconButton(onClick = onExport, modifier = Modifier) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = stringResource(Res.string.debug_logs_export),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
    DebugActiveFilters(
        filterTexts = filterTexts,
        onFilterTextsChange = onFilterTextsChange,
        filterMode = filterMode,
        onFilterModeChange = onFilterModeChange,
    )
}

@Composable
fun DebugSearchStateviewModelDefaults(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    filterTexts: List<String>,
    presetFilters: List<String>,
    logs: List<UiMeshLog>,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    onExportLogs: (() -> Unit)? = null,
) {
    val viewModel: DebugViewModel = hiltViewModel()
    DebugSearchState(
        modifier = modifier,
        searchState = searchState,
        filterTexts = filterTexts,
        presetFilters = presetFilters,
        logs = logs,
        onSearchTextChange = viewModel.searchManager::setSearchText,
        onNextMatch = viewModel.searchManager::goToNextMatch,
        onPreviousMatch = viewModel.searchManager::goToPreviousMatch,
        onClearSearch = viewModel.searchManager::clearSearch,
        onFilterTextsChange = viewModel.filterManager::setFilterTexts,
        filterMode = filterMode,
        onFilterModeChange = onFilterModeChange,
        onExportLogs = onExportLogs,
    )
}

@PreviewLightDark
@Composable
private fun DebugSearchBarEmptyPreview() {
    AppTheme {
        Surface {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DebugSearchBar(
                    searchState = SearchState(),
                    onSearchTextChange = {},
                    onNextMatch = {},
                    onPreviousMatch = {},
                    onClearSearch = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
@Suppress("detekt:MagicNumber") // fake data
private fun DebugSearchBarWithTextPreview() {
    AppTheme {
        Surface {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DebugSearchBar(
                    searchState =
                    SearchState(
                        searchText = "test message",
                        currentMatchIndex = 2,
                        allMatches = List(5) { SearchMatch(it, 0, 10, "message") },
                        hasMatches = true,
                    ),
                    onSearchTextChange = {},
                    onNextMatch = {},
                    onPreviousMatch = {},
                    onClearSearch = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
@Suppress("detekt:MagicNumber") // fake data
private fun DebugSearchBarWithMatchesPreview() {
    AppTheme {
        Surface {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DebugSearchBar(
                    searchState =
                    SearchState(
                        searchText = "error",
                        currentMatchIndex = 0,
                        allMatches = List(3) { SearchMatch(it, 0, 5, "message") },
                        hasMatches = true,
                    ),
                    onSearchTextChange = {},
                    onNextMatch = {},
                    onPreviousMatch = {},
                    onClearSearch = {},
                )
            }
        }
    }
}
