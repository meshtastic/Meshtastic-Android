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
package org.meshtastic.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AboutScreenTest {

    @Test
    fun `about screen displays all sections and content`() = runComposeUiTest {
        var navigatedUp = false
        var navigatedToAcknowledgements = false

        setContent {
            AppTheme {
                AboutScreen(
                    appVersionName = "2.5.0",
                    onNavigateUp = { navigatedUp = true },
                    onNavigateToAcknowledgements = { navigatedToAcknowledgements = true },
                )
            }
        }

        // Top app bar
        onNodeWithText("About").assertIsDisplayed()

        // What is Meshtastic section
        onNodeWithText("What is Meshtastic?").assertIsDisplayed()
        onNodeWithText(
            "An open source, off-grid, decentralized, mesh network that runs on affordable, low-power radios.",
        )
            .assertIsDisplayed()

        // Apps section
        onNodeWithText("Apps").assertIsDisplayed()
        onNodeWithText("Need Hardware?").assertIsDisplayed()
        onNodeWithText(
            "Meshtastic requires a compatible device. Our backers and partners offer ready-to-use hardware. " +
                "Here are some of the most popular options.",
        )
            .assertIsDisplayed()
        onNodeWithText("GitHub Repository").assertIsDisplayed()
        onNodeWithText("Version").assertIsDisplayed()
        onNodeWithText("2.5.0").assertIsDisplayed()
        onNodeWithText("Acknowledgements").assertIsDisplayed()

        // Project information section
        onNodeWithText("Project information").assertIsDisplayed()
        onNodeWithText("Website").assertIsDisplayed()
        onNodeWithText("Documentation").assertIsDisplayed()

        // Copyright footer
        onNodeWithText("Meshtastic® Copyright Meshtastic LLC").assertIsDisplayed()

        onNodeWithText("Acknowledgements").performClick()
        assertTrue(navigatedToAcknowledgements)

        onNodeWithContentDescription("Navigate Back").performClick()
        assertTrue(navigatedUp)
    }
}
