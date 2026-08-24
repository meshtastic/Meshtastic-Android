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
package org.meshtastic.feature.messaging.ui.contact

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `rememberSwipeToDismissBoxState` keeps `confirmValueChange` in a keyless `rememberSaveable`, so the lambda it holds
 * is the one from the row's first composition. Muting a row therefore used to leave the gesture calling a closure that
 * still saw the contact as unmuted, and the next swipe re-muted it instead of unmuting.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeableContactRowTest {

    private fun contact(isMuted: Boolean) = Contact(
        contactKey = "0!abcd1234",
        shortName = "ABCD",
        longName = "Longfellow",
        lastMessageTime = null,
        lastMessageText = "hi",
        unreadCount = 0,
        messageCount = 1,
        isMuted = isMuted,
        isUnmessageable = false,
    )

    @Test
    fun swipingAMutedRowUnmutesItRatherThanRemutingIt() = runComposeUiTest {
        var muted by mutableStateOf(false)
        var muteCalls = 0

        setContent {
            MaterialTheme {
                val current = contact(isMuted = muted)
                SwipeableContactRow(
                    contact = current,
                    enabled = true,
                    // Mirrors the production call site, which decides the direction from the contact it captured.
                    onMute = {
                        muteCalls++
                        muted = !current.isMuted
                    },
                    onDelete = {},
                ) {
                    Box(Modifier.testTag(ROW_TAG).fillMaxWidth().height(64.dp)) { Text(current.longName) }
                }
            }
        }

        onNodeWithTag(ROW_TAG).performTouchInput { swipeRight() }
        waitForIdle()
        assertTrue(muted, "first swipe should mute")
        assertEquals(1, muteCalls, "a swipe is one action, not one per animation frame")
        // The box turns its own gestures off while the row is settled open, so a row that does not come home is a row
        // that can never be swiped again.
        assertEquals(0f, onNodeWithTag(ROW_TAG).getBoundsInRoot().left.value, "row should animate home")

        onNodeWithTag(ROW_TAG).performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals(2, muteCalls, "the row must stay swipeable after it settles back")
        assertFalse(muted, "second swipe should unmute, not re-mute")
    }

    private companion object {
        const val ROW_TAG = "swipeableContactRow"
    }
}
