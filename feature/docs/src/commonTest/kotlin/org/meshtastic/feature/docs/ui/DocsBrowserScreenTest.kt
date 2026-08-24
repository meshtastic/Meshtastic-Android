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
package org.meshtastic.feature.docs.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DocsBrowserScreenTest {

    private fun samplePage(id: String = "connections", title: String = "Connections") = DocPage(
        id = id,
        title = title,
        section = DocSection.UserGuide,
        navOrder = 1,
        resourcePath = "user/$id.md",
        keywords = listOf("connect"),
        charCount = 1000,
    )

    @Test
    fun loadingState_showsSpinner() = runComposeUiTest {
        setContent {
            DocsBrowserScreen(
                pages = emptyList(),
                isLoading = true,
                searchQuery = "",
                onSearchQueryChange = {},
                onSelectPage = {},
                onBack = {},
            )
        }
        onNodeWithText("Loading documentation...").assertIsDisplayed()
    }

    @Test
    fun emptyPages_noSearchQuery_showsNoDocsMessage() = runComposeUiTest {
        setContent {
            DocsBrowserScreen(
                pages = emptyList(),
                isLoading = false,
                searchQuery = "",
                onSearchQueryChange = {},
                onSelectPage = {},
                onBack = {},
            )
        }
        onNodeWithText("No documentation available").assertIsDisplayed()
    }

    @Test
    fun emptyPages_withSearchQuery_showsNoResultsMessage() = runComposeUiTest {
        setContent {
            DocsBrowserScreen(
                pages = emptyList(),
                isLoading = false,
                searchQuery = "xyzzy",
                onSearchQueryChange = {},
                onSelectPage = {},
                onBack = {},
            )
        }
        onNodeWithText("No results found").assertIsDisplayed()
    }

    @Test
    fun pagesLoaded_showsTitles() = runComposeUiTest {
        setContent {
            DocsBrowserScreen(
                pages = listOf(samplePage(), samplePage(id = "mqtt", title = "MQTT")),
                isLoading = false,
                searchQuery = "",
                onSearchQueryChange = {},
                onSelectPage = {},
                onBack = {},
            )
        }
        onNodeWithText("Connections").assertIsDisplayed()
        onNodeWithText("MQTT").assertIsDisplayed()
    }

    @Test
    fun pageItemClick_callsOnSelectPage() = runComposeUiTest {
        var selectedId: String? = null
        setContent {
            DocsBrowserScreen(
                pages = listOf(samplePage()),
                isLoading = false,
                searchQuery = "",
                onSearchQueryChange = {},
                onSelectPage = { selectedId = it },
                onBack = {},
            )
        }
        onNodeWithContentDescription("Open Connections").performClick()
        runOnIdle { assertEquals("connections", selectedId) }
    }

    @Test
    fun backButton_callsOnBack() = runComposeUiTest {
        var backCalled = false
        setContent {
            DocsBrowserScreen(
                pages = listOf(samplePage()),
                isLoading = false,
                searchQuery = "",
                onSearchQueryChange = {},
                onSelectPage = {},
                onBack = { backCalled = true },
            )
        }
        onNodeWithContentDescription("Navigate back").performClick()
        runOnIdle { assertTrue(backCalled) }
    }

    @Test
    fun userGuideSection_showsSectionHeader() = runComposeUiTest {
        setContent {
            DocsBrowserScreen(
                pages = listOf(samplePage()),
                isLoading = false,
                searchQuery = "",
                onSearchQueryChange = {},
                onSelectPage = {},
                onBack = {},
            )
        }
        onNodeWithText("User Guide").assertIsDisplayed()
    }
}
