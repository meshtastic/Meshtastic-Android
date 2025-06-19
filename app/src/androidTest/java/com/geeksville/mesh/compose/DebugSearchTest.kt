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

package com.geeksville.mesh.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geeksville.mesh.R
import com.geeksville.mesh.model.LogSearchManager.SearchState
import com.geeksville.mesh.ui.debug.DebugSearchBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@RunWith(AndroidJUnit4::class)
class DebugSearchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun debugSearchBar_showsPlaceholder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val placeholder = context.getString(R.string.debug_default_search)
        composeTestRule.setContent {
            DebugSearchBar(
                searchState = SearchState(),
                onSearchTextChange = {},
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = {}
            )
        }
        composeTestRule.onNodeWithText(placeholder).assertIsDisplayed()
    }

    @Test
    fun debugSearchBar_showsClearButtonWhenTextEntered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val placeholder = context.getString(R.string.debug_default_search)
        composeTestRule.setContent {
            var searchText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("test") }
            DebugSearchBar(
                searchState = SearchState(searchText = searchText),
                onSearchTextChange = { searchText = it },
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = { searchText = "" }
            )
        }
        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText(placeholder).assertIsDisplayed()
    }

    @Test
    fun debugSearchBar_searchFor_showsArrowsClearAndValues() {
        val searchText = "test"
        val matchCount = 3
        val currentMatchIndex = 1

        composeTestRule.setContent {
            DebugSearchBar(
                searchState = SearchState(
                    searchText = searchText,
                    currentMatchIndex = currentMatchIndex,
                    allMatches = List(matchCount) { com.geeksville.mesh.model.LogSearchManager.SearchMatch(it, 0, 6, "Packet") },
                    hasMatches = true
                ),
                onSearchTextChange = {},
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = {}
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
} 