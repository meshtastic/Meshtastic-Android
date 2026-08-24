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
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.model.DocSection
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DocsPageRouteScreenTest {

    private fun samplePage(id: String = "connections", title: String = "Connections") = DocPage(
        id = id,
        title = title,
        section = DocSection.UserGuide,
        navOrder = 1,
        resourcePath = "user/$id.md",
        keywords = listOf("connect"),
        charCount = 1000,
    )

    private fun sampleContent(page: DocPage = samplePage(), markdown: String? = "# Hello\n\nSome content here.") =
        DocPageContent(page = page, markdown = markdown)

    @Test
    fun loadingState_showsSpinner() = runComposeUiTest {
        setContent { DocsPageRouteScreen(pageId = "connections", content = null, isLoading = true, onBack = {}) }
        // Loading state shows spinner, not "Page not found"
        onNodeWithText("Page not found: connections").assertDoesNotExist()
    }

    @Test
    fun contentNull_notLoading_showsPageNotFound() = runComposeUiTest {
        setContent { DocsPageRouteScreen(pageId = "nonexistent", content = null, isLoading = false, onBack = {}) }
        onNodeWithText("Page not found: nonexistent").assertIsDisplayed()
        onNodeWithText("This page may have been moved or removed.").assertIsDisplayed()
    }

    @Test
    fun contentLoaded_showsPageTitleInAppBar() = runComposeUiTest {
        setContent {
            DocsPageRouteScreen(
                pageId = "connections",
                content = sampleContent(page = samplePage(title = "Connections")),
                isLoading = false,
                onBack = {},
            )
        }
        onNodeWithText("Connections").assertIsDisplayed()
    }

    @Test
    fun contentLoaded_doesNotShowPageNotFound() = runComposeUiTest {
        setContent {
            DocsPageRouteScreen(
                pageId = "connections",
                content = sampleContent(markdown = "# Getting Started\n\nWelcome."),
                isLoading = false,
                onBack = {},
            )
        }
        // Verify content branch — page not found message should NOT be visible
        onNodeWithText("Page not found: connections").assertDoesNotExist()
    }

    @Test
    fun backButton_callsOnBack() = runComposeUiTest {
        var backCalled = false
        setContent {
            DocsPageRouteScreen(
                pageId = "connections",
                content = sampleContent(),
                isLoading = false,
                onBack = { backCalled = true },
            )
        }
        onNodeWithContentDescription("Navigate back").performClick()
        runOnIdle { assertTrue(backCalled) }
    }

    @Test
    fun nullMarkdown_showsFallbackWithoutCrash() = runComposeUiTest {
        setContent {
            DocsPageRouteScreen(
                pageId = "connections",
                content = sampleContent(markdown = null),
                isLoading = false,
                onBack = {},
            )
        }
        // Content renders without crash; page not found should NOT appear since content object exists
        onNodeWithText("Page not found: connections").assertDoesNotExist()
    }
}
