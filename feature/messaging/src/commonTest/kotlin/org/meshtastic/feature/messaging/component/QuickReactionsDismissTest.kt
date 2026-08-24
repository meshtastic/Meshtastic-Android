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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The quick reaction bar is opened per row but owned by the list, so a tap anywhere else can close it. These pin the
 * three ways "anywhere else" shows up — another row, the space around the rows, and the row's own bubble — without the
 * dismissal swallowing a tap that was aimed at an emoji.
 */
@OptIn(ExperimentalTestApi::class)
class QuickReactionsDismissTest {

    private val node: Node = NodePreviewParameterProvider().minnieMouse

    private fun message(uuid: Long, text: String) = Message(
        text = text,
        time = "10:00",
        fromLocal = false,
        status = MessageStatus.RECEIVED,
        snr = 2.5f,
        rssi = 90,
        hopsAway = 0,
        uuid = uuid,
        receivedTime = 0L,
        node = node,
        read = true,
        routingError = 0,
        packetId = uuid.toInt(),
        emojis = listOf(),
        replyId = null,
        viaMqtt = false,
    )

    @Test
    fun tappingAnotherRowClosesTheBar() = runComposeUiTest {
        val harness = setHarness()
        harness.openBarOnFirst(this)
        onNodeWithText(EMOJI).assertIsDisplayed()

        onNodeWithText(SECOND_TEXT).performClick()
        waitForIdle()
        assertNull(harness.openFor(), "a tap on another row should close the bar")
    }

    @Test
    fun tappingTheBackgroundClosesTheBar() = runComposeUiTest {
        val harness = setHarness()
        harness.openBarOnFirst(this)
        onNodeWithText(EMOJI).assertIsDisplayed()

        onNodeWithTag(BACKGROUND_TAG).performTouchInput { click(Offset(centerX, bottom - 1f)) }
        waitForIdle()
        assertNull(harness.openFor(), "a tap on the space around the rows should close the bar")
    }

    @Test
    fun tappingAnEmojiStillReactsAndIsNotEatenByTheDismissal() = runComposeUiTest {
        val harness = setHarness()
        harness.openBarOnFirst(this)

        onNodeWithText(EMOJI).performClick()
        waitForIdle()
        assertEquals(listOf(EMOJI), harness.reactions(), "the emoji tap must still register a reaction")
        assertNull(harness.openFor(), "reacting closes the bar")
    }

    private class Harness(val openFor: () -> Long?, val reactions: () -> List<String>) {
        fun openBarOnFirst(test: ComposeUiTest) {
            test.onNodeWithText(FIRST_TEXT).performTouchInput { doubleClick() }
            test.waitForIdle()
        }
    }

    /** Mirrors the list's ownership: one open row for the whole column, plus the container tap that closes it. */
    private fun ComposeUiTest.setHarness(): Harness {
        // Held outside the composition so the harness can read it, exactly as the list holds it above its rows.
        val openState = mutableStateOf<Long?>(null)
        var open by openState
        val reacted = mutableListOf<String>()
        setContent {
            MaterialTheme {
                Box(
                    modifier =
                    Modifier.testTag(BACKGROUND_TAG).fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { open = null }
                    },
                ) {
                    Column(Modifier.fillMaxWidth().height(ROW_SPACE)) {
                        listOf(FIRST_UUID to FIRST_TEXT, SECOND_UUID to SECOND_TEXT).forEach { (uuid, text) ->
                            MessageItem(
                                message = message(uuid, text),
                                node = node,
                                ourNode = node,
                                selected = false,
                                onStatusClick = {},
                                quickEmojis = listOf(EMOJI),
                                quickReactionsOpen = open == uuid,
                                onQuickReactionsOpenChange = { shown -> open = if (shown) uuid else null },
                                onClick = { if (open != null) open = null },
                                sendReaction = { reacted += it },
                            )
                        }
                    }
                }
            }
        }
        return Harness(openFor = { openState.value }, reactions = { reacted.toList() })
    }

    private companion object {
        const val FIRST_UUID = 1L
        const val SECOND_UUID = 2L
        const val FIRST_TEXT = "first message"
        const val SECOND_TEXT = "second message"
        const val EMOJI = "👍"
        const val BACKGROUND_TAG = "listBackground"
        val ROW_SPACE = 400.dp
    }
}
