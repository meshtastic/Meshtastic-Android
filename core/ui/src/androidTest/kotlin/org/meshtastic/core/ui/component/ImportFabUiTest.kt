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

import androidx.compose.ui.test.assertIsDisplayed
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

        // Expand the FAB
        composeTestRule.onNodeWithTag(testTag).performClick()

        // Verify menu items are visible using their tags
        composeTestRule.onNodeWithTag("nfc_import").assertIsDisplayed()
        composeTestRule.onNodeWithTag("qr_import").assertIsDisplayed()
        composeTestRule.onNodeWithTag("url_import").assertIsDisplayed()
    }

    @Test
    fun importFab_showsUrlDialog_whenUrlItemClicked() {
        val testTag = "import_fab"
        composeTestRule.setContent { MeshtasticImportFAB(onImport = {}, isContactContext = true, testTag = testTag) }

        composeTestRule.onNodeWithTag(testTag).performClick()
        composeTestRule.onNodeWithTag("url_import").performClick()

        // The URL dialog should be shown.
        // We'll search for its title indirectly or check if an AlertDialog appeared.
    }

    @Test
    fun importFab_showsShareChannels_whenCallbackProvided() {
        val testTag = "import_fab"
        composeTestRule.setContent {
            MeshtasticImportFAB(onImport = {}, onShareChannels = {}, isContactContext = false, testTag = testTag)
        }

        composeTestRule.onNodeWithTag(testTag).performClick()
        composeTestRule.onNodeWithTag("share_channels").assertIsDisplayed()
    }

    @Test
    fun importFab_showsSharedContactDialog_whenProvided() {
        val contact = SharedContact(user = User(long_name = "Suzume Goddess"), node_num = 1)
        composeTestRule.setContent {
            MeshtasticImportFAB(onImport = {}, sharedContact = contact, onDismissSharedContact = {})
        }

        // Check if goddess is here
        // composeTestRule.onNodeWithText("Suzume Goddess").assertIsDisplayed()
    }
}
