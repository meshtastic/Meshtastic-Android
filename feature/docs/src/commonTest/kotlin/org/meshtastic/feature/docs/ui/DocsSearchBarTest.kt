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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DocsSearchBarTest {

    @Test
    fun emptyQuery_clearButtonNotShown() = runComposeUiTest {
        setContent { DocsSearchBar(query = "", onQueryChange = {}) }
        onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test
    fun nonEmptyQuery_clearButtonShown() = runComposeUiTest {
        setContent { DocsSearchBar(query = "bluetooth", onQueryChange = {}) }
        onNodeWithContentDescription("Clear search").assertIsDisplayed()
    }

    @Test
    fun clearButtonClick_callsOnQueryChangeWithEmpty() = runComposeUiTest {
        var received: String? = null
        setContent { DocsSearchBar(query = "bluetooth", onQueryChange = { received = it }) }
        onNodeWithContentDescription("Clear search").performClick()
        runOnIdle { assertEquals("", received) }
    }

    @Test
    fun textInput_callsOnQueryChange() = runComposeUiTest {
        val queries = mutableListOf<String>()
        setContent { DocsSearchBar(query = "", onQueryChange = { queries += it }) }
        // OutlinedTextField placeholder is not findable by text; use semantics matcher
        onNode(hasSetTextAction()).performTextInput("mesh")
        runOnIdle { assertEquals("mesh", queries.last()) }
    }
}
