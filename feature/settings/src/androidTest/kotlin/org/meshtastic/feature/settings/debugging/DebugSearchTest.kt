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

package org.meshtastic.feature.settings.debugging

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.feature.settings.debugging.DebugViewModel.UiMeshLog
import org.meshtastic.feature.settings.debugging.LogSearchManager.SearchMatch
import org.meshtastic.feature.settings.debugging.LogSearchManager.SearchState
import org.meshtastic.core.strings.R as Res

@RunWith(AndroidJUnit4::class)
class DebugSearchTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun debugSearchBar_showsPlaceholder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val placeholder = context.getString(Res.string.debug_default_search)
        composeTestRule.setContent {
            DebugSearchBar(
                searchState = SearchState(),
                onSearchTextChange = {},
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = {},
            )
        }
        composeTestRule.onNodeWithText(placeholder).assertIsDisplayed()
    }

    @Test
    fun debugSearchBar_showsClearButtonWhenTextEntered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val placeholder = context.getString(Res.string.debug_default_search)
        composeTestRule.setContent {
            var searchText by remember { mutableStateOf("test") }
            DebugSearchBar(
                searchState = SearchState(searchText = searchText),
                onSearchTextChange = { searchText = it },
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = { searchText = "" },
            )
        }
        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(placeholder).assertIsDisplayed()
    }

    @Test
    fun debugSearchBar_searchFor_showsArrowsClearAndValues() {
        val searchText = "test"
        val matchCount = 3
        val currentMatchIndex = 1

        composeTestRule.setContent {
            DebugSearchBar(
                searchState =
                SearchState(
                    searchText = searchText,
                    currentMatchIndex = currentMatchIndex,
                    allMatches = List(matchCount) { SearchMatch(it, 0, 6, "Packet") },
                    hasMatches = true,
                ),
                onSearchTextChange = {},
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = {},
            )
        }
        // Check the match count display (e.g., '2/3')
        composeTestRule.onNodeWithText("${currentMatchIndex + 1}/$matchCount").assertIsDisplayed()
        // Check the navigation arrows
        composeTestRule.onNodeWithContentDescription("Previous match").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next match").assertIsDisplayed()
        // Check the clear button
        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
    }

    @Test
    fun debugFilterBar_showsFilterButtonAndMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filterLabel = context.getString(Res.string.debug_filters)
        composeTestRule.setContent {
            var filterTexts by remember { mutableStateOf(listOf<String>()) }
            var customFilterText by remember { mutableStateOf("") }
            val presetFilters = listOf("Error", "Warning", "Info")
            val logs =
                listOf(
                    UiMeshLog(
                        uuid = "1",
                        messageType = "Info",
                        formattedReceivedDate = "2024-01-01 12:00:00",
                        logMessage = "Sample log message",
                    ),
                )
            DebugFilterBar(
                filterTexts = filterTexts,
                onFilterTextsChange = { filterTexts = it },
                customFilterText = customFilterText,
                onCustomFilterTextChange = { customFilterText = it },
                presetFilters = presetFilters,
                logs = logs,
            )
        }
        // The filter button should be visible
        composeTestRule.onNodeWithText(filterLabel).assertIsDisplayed()
    }

    @Test
    fun debugFilterBar_addCustomFilter_displaysActiveFilter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activeFiltersLabel = context.getString(Res.string.debug_active_filters)
        composeTestRule.setContent {
            var filterTexts by remember { mutableStateOf(listOf<String>()) }
            var customFilterText by remember { mutableStateOf("") }
            Column(modifier = Modifier.padding(16.dp)) {
                DebugActiveFilters(
                    filterTexts = filterTexts,
                    onFilterTextsChange = { filterTexts = it },
                    filterMode = FilterMode.OR,
                    onFilterModeChange = {},
                )
                DebugCustomFilterInput(
                    customFilterText = customFilterText,
                    onCustomFilterTextChange = { customFilterText = it },
                    filterTexts = filterTexts,
                    onFilterTextsChange = { filterTexts = it },
                )
            }
        }
        with(composeTestRule) {
            onNodeWithText("Add custom filter").performTextInput("MyFilter")
            onNodeWithContentDescription("Add filter").performClick()
            onNodeWithText(activeFiltersLabel).assertIsDisplayed()
            onNodeWithText("MyFilter").assertIsDisplayed()
        }
    }

    @Test
    fun debugActiveFilters_clearAllFilters_removesFilters() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activeFiltersLabel = context.getString(Res.string.debug_active_filters)
        composeTestRule.setContent {
            var filterTexts by remember { mutableStateOf(listOf("A", "B")) }
            DebugActiveFilters(
                filterTexts = filterTexts,
                onFilterTextsChange = { filterTexts = it },
                filterMode = FilterMode.OR,
                onFilterModeChange = {},
            )
        }
        // The active filters label and chips should be visible
        composeTestRule.onNodeWithText(activeFiltersLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("A").assertIsDisplayed()
        composeTestRule.onNodeWithText("B").assertIsDisplayed()
        // Click the clear all filters button
        composeTestRule.onNodeWithContentDescription("Clear all filters").performClick()
        // The filter chips should no longer be visible
        composeTestRule.onNodeWithText("A").assertDoesNotExist()
        composeTestRule.onNodeWithText("B").assertDoesNotExist()
    }
}
