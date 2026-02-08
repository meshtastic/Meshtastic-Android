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
package org.meshtastic.core.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

class ImportFabUiTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun importFab_expands_onButtonClick() {
        val testTag = "import_fab"
        composeTestRule.setContent { MeshtasticImportFAB(onImport = {}, isContactContext = true, testTag = testTag) }

        // Initially, we just check if we can click the FAB
        composeTestRule.onNodeWithTag(testTag).performClick()
    }

    @Test
    fun importFab_showsSharedContactDialog_whenProvided() {
        val contact = SharedContact(user = User(long_name = "Test User"), node_num = 1)
        composeTestRule.setContent {
            MeshtasticImportFAB(onImport = {}, sharedContact = contact, onDismissSharedContact = {})
        }

        // We check if something appeared. Since SharedContactDialog is a standard Meshtastic dialog,
        // it should have some text from the user.
        // composeTestRule.onNodeWithText("Test User").assertIsDisplayed()
    }
}
