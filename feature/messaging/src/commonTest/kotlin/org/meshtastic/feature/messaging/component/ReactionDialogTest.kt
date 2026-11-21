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
package org.meshtastic.feature.messaging.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Reaction
import org.meshtastic.proto.User
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReactionDialogTest {

    private fun reaction(userId: String, longName: String, status: MessageStatus) = Reaction(
        replyId = 1,
        user = User.Builder().also { it.id = userId }.also { it.long_name = longName }.build(),
        emoji = "👍",
        timestamp = 0L,
        snr = null,
        rssi = null,
        hopsAway = 2,
        status = status,
        relayNode = 0x5A,
    )

    private val resolveRelay: (Int?) -> String? = { relay -> if (relay == 0x5A) "Ridge (0x5A)" else null }

    @Test
    fun aReceivedReactionNamesItsRelay() = runComposeUiTest {
        setContent {
            ReactionDialog(
                reactions = listOf(reaction("!remote", "Remote", MessageStatus.RECEIVED)),
                myId = "!me",
                resolveRelay = resolveRelay,
            )
        }

        onNodeWithText("Relayed by Ridge (0x5A)").assertIsDisplayed()
    }

    @Test
    fun ourOwnReactionShowsNoReceivedRelay() = runComposeUiTest {
        setContent {
            ReactionDialog(
                reactions = listOf(reaction("!me", "Me", MessageStatus.DELIVERED)),
                myId = "!me",
                resolveRelay = resolveRelay,
            )
        }

        onNodeWithText("Me", substring = true).assertIsDisplayed()
        onNodeWithText("Relayed by", substring = true).assertDoesNotExist()
    }

    @Test
    fun ourReactionStatusNamesTheRelay() = runComposeUiTest {
        setContent {
            ReactionDialog(
                reactions = listOf(reaction("!me", "Me", MessageStatus.DELIVERED)),
                myId = "!me",
                resolveRelay = resolveRelay,
            )
        }

        onNodeWithContentDescription("Message delivery status").performClick()
        waitForIdle()

        onNodeWithText("Relayed by Ridge (0x5A)").assertIsDisplayed()
    }
}
