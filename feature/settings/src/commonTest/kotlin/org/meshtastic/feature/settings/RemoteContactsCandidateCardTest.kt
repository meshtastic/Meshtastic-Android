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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.remote_contacts_add_contact
import org.meshtastic.core.resources.remote_contacts_add_needs_key
import org.meshtastic.core.resources.remote_contacts_add_without_favorite
import org.meshtastic.core.resources.remote_contacts_favorite
import org.meshtastic.core.resources.remote_contacts_unignore
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.RemoteContactAction
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RemoteContactsCandidateCardTest {

    private val keyless =
        Node(
            num = 0x1234ABCD,
            user =
            User.Builder()
                .also { wb ->
                    wb.id = "!1234abcd"
                    wb.long_name = "Keyless"
                    wb.short_name = "KL"
                }
                .build(),
        )

    @Test
    fun aNodeWithoutATrustedKeyDisablesOnlyTheAddButtonsAndSaysWhy() = runComposeUiTest {
        val sent = mutableListOf<RemoteContactAction>()
        setContent {
            AppTheme {
                CandidateCard(
                    node = keyless,
                    target = "Remote",
                    clientBase = true,
                    enabled = true,
                    result = null,
                    onAction = { sent += it },
                )
            }
        }

        onNodeWithText(getString(Res.string.remote_contacts_add_needs_key)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_contacts_add_contact)).assertIsNotEnabled()
        onNodeWithText(getString(Res.string.remote_contacts_add_without_favorite)).assertIsNotEnabled()
        onNodeWithText(getString(Res.string.remote_contacts_favorite)).assertIsEnabled().performClick()
        onNodeWithText(getString(Res.string.remote_contacts_unignore)).assertIsEnabled().performClick()

        assertEquals(listOf(RemoteContactAction.FAVORITE, RemoteContactAction.UNIGNORE), sent)
    }
}
