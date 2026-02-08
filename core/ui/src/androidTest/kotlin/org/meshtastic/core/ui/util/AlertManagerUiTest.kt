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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AlertManagerUiTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val alertManager = AlertManager()

    @Test
    fun alertManager_showsAlert_whenRequested() {
        composeTestRule.setContent {
            val alertData by alertManager.currentAlert.collectAsState()
            alertData?.let { data -> AlertPreviewRenderer(data) }
        }

        val title = "UI Test Alert"
        val message = "This is a message from a UI test."

        alertManager.showAlert(title = title, message = message)

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun alertManager_confirmButton_triggersCallbackAndDismisses() {
        var confirmClicked = false
        composeTestRule.setContent {
            val alertData by alertManager.currentAlert.collectAsState()
            alertData?.let { data -> AlertPreviewRenderer(data) }
        }

        alertManager.showAlert(title = "Confirm Title", onConfirm = { confirmClicked = true })

        // Default confirm text is "Okay" from resources, but AlertPreviewRenderer uses it
        // We'll search for the text "Okay" (assuming it matches the resource value)
        // Since we are in a test, we might need to use a hardcoded string or a resource
        // But for this test, let's just use the confirmText parameter to be sure
        alertManager.showAlert(title = "Confirm Title", confirmText = "Yes", onConfirm = { confirmClicked = true })

        composeTestRule.onNodeWithText("Yes").performClick()

        assert(confirmClicked)
        composeTestRule.onNodeWithText("Confirm Title").assertDoesNotExist()
    }
}
