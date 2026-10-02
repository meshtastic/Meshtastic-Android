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
package org.meshtastic.feature.connections.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.connect
import org.meshtastic.core.resources.deep_link_connect_title
import org.meshtastic.core.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeepLinkConnectPromptTest {

    private val applied = mutableListOf<String>()
    private var done = 0

    @Test
    fun `a deep link address waits for the user to confirm`() = runComposeUiTest {
        setPrompt(skipConfirmation = false)

        onNodeWithText(getString(Res.string.deep_link_connect_title)).assertIsDisplayed()
        assertTrue(applied.isEmpty())

        onNodeWithText(getString(Res.string.connect)).performClick()
        waitForIdle()

        assertEquals(listOf(ADDRESS), applied)
        assertEquals(1, done)
    }

    @Test
    fun `cancelling the prompt applies nothing`() = runComposeUiTest {
        setPrompt(skipConfirmation = false)

        onNodeWithText(getString(Res.string.cancel)).performClick()
        waitForIdle()

        assertTrue(applied.isEmpty())
        assertEquals(1, done)
    }

    @Test
    fun `the launch switch applies the address once with no dialog`() = runComposeUiTest {
        setPrompt(skipConfirmation = true)
        waitForIdle()

        onNodeWithText(getString(Res.string.deep_link_connect_title)).assertDoesNotExist()
        assertEquals(listOf(ADDRESS), applied)
        assertEquals(1, done)
    }

    private fun ComposeUiTest.setPrompt(skipConfirmation: Boolean) {
        setContent {
            MaterialTheme {
                DeepLinkConnectPrompt(
                    address = ADDRESS,
                    skipConfirmation = skipConfirmation,
                    onApply = { applied += it },
                    onDone = { done++ },
                )
            }
        }
    }

    private companion object {
        const val ADDRESS = "t10.0.2.2:4403"
    }
}
