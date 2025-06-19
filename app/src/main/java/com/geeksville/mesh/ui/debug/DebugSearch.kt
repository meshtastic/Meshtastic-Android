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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.LogSearchManager.SearchMatch
import com.geeksville.mesh.model.LogSearchManager.SearchState
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.model.DebugViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
internal fun DebugSearchNavigation(
    searchState: SearchState,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.width(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${searchState.currentMatchIndex + 1}/${searchState.allMatches.size}",
            modifier = Modifier.padding(end = 4.dp),
            style = TextStyle(fontSize = 12.sp)
        )
        IconButton(
            onClick = onPreviousMatch,
            enabled = searchState.hasMatches,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous match",
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(
            onClick = onNextMatch,
            enabled = searchState.hasMatches,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next match",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun DebugSearchBar(
    searchState: SearchState,
    onSearchTextChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchState.searchText,
        onValueChange = onSearchTextChange,
        modifier = modifier
            .padding(end = 8.dp),
        placeholder = { Text(stringResource(R.string.debug_default_search)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                // Clear focus when search is performed
            }
        ),
        trailingIcon = {
            Row(
                modifier = Modifier.width(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchState.hasMatches) {
                    DebugSearchNavigation(
                        searchState = searchState,
                        onNextMatch = onNextMatch,
                        onPreviousMatch = onPreviousMatch
                    )
                }
                if (searchState.searchText.isNotEmpty()) {
                    IconButton(
                        onClick = onClearSearch,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun DebugSearchState(
    searchState: SearchState,
    filterTexts: List<String>,
    presetFilters: List<String>,
    onSearchTextChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onClearSearch: () -> Unit,
    onFilterTextsChange: (List<String>) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(colorScheme.background.copy(alpha = 1.0f)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DebugSearchBar(
                searchState = searchState,
                onSearchTextChange = onSearchTextChange,
                onNextMatch = onNextMatch,
                onPreviousMatch = onPreviousMatch,
                onClearSearch = onClearSearch
            )
            DebugFilterBar(
                filterTexts = filterTexts,
                onFilterTextsChange = onFilterTextsChange,
                customFilterText = "",
                onCustomFilterTextChange = {},
                presetFilters = presetFilters
            )
        }
    }

    DebugActiveFilters(
        filterTexts = filterTexts,
        onFilterTextsChange = onFilterTextsChange
    )
}

@Composable
fun DebugSearchStateviewModelDefaults(
    searchState: SearchState,
    filterTexts: List<String>,
    presetFilters: List<String>,
) {
    val viewModel: DebugViewModel = hiltViewModel()
    DebugSearchState(
        searchState = searchState,
        filterTexts = filterTexts,
        presetFilters = presetFilters,
        onSearchTextChange = viewModel.searchManager::setSearchText,
        onNextMatch = viewModel.searchManager::goToNextMatch,
        onPreviousMatch = viewModel.searchManager::goToPreviousMatch,
        onClearSearch = viewModel.searchManager::clearSearch,
        onFilterTextsChange = viewModel.filterManager::setFilterTexts,
    )
}

@PreviewLightDark
@Composable
private fun DebugSearchBarEmptyPreview() {
    AppTheme {
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DebugSearchBar(
                    searchState = SearchState(),
                    onSearchTextChange = { },
                    onNextMatch = { },
                    onPreviousMatch = { },
                    onClearSearch = { }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DebugSearchBar(
                    searchState = SearchState(
                        searchText = "test message",
                        currentMatchIndex = 2,
                        allMatches = List(5) { SearchMatch(it, 0, 10, "message") },
                        hasMatches = true
                    ),
                    onSearchTextChange = { },
                    onNextMatch = { },
                    onPreviousMatch = { },
                    onClearSearch = { }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DebugSearchBar(
                    searchState = SearchState(
                        searchText = "error",
                        currentMatchIndex = 0,
                        allMatches = List(3) { SearchMatch(it, 0, 5, "message") },
                        hasMatches = true
                    ),
                    onSearchTextChange = { },
                    onNextMatch = { },
                    onPreviousMatch = { },
                    onClearSearch = { }
                )
            }
        }
    }
}
