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
package org.meshtastic.feature.messaging

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The composer's mention [androidx.compose.foundation.text.input.OutputTransformation] reads the node list. Handing the
 * text field a new transformation instance while it is focused restarts its IME session, which Android turns into a
 * keyboard hide/show (#6950). This pins that a node rename reaches the rendered mention without a restart.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class MessageInputImeSessionTest {

    private fun candidates(longName: String): ImmutableMap<String, MentionCandidate> =
        persistentMapOf(NODE_ID to MentionCandidate(id = NODE_ID, longName = longName, shortName = "ALP"))

    @Test
    fun renamingAMentionedNodeWhileFocusedKeepsTheImeSessionAndRendersTheNewName() = runComposeUiTest {
        var sessionsStarted = 0
        val interceptor =
            object : PlatformTextInputInterceptor {
                override suspend fun interceptStartInputMethod(
                    request: PlatformTextInputMethodRequest,
                    nextHandler: PlatformTextInputSession,
                ): Nothing {
                    sessionsStarted++
                    awaitCancellation()
                }
            }
        var mentionCandidates by mutableStateOf(candidates(OLD_NAME))
        val textFieldState = TextFieldState("hi @$NODE_ID ")

        setContent {
            MaterialTheme {
                InterceptPlatformTextInput(interceptor) {
                    MessageInput(
                        isEnabled = true,
                        isHomoglyphEncodingEnabled = false,
                        textFieldState = textFieldState,
                        mentionCandidates = mentionCandidates,
                        onSendMessage = {},
                    )
                }
            }
        }

        val field = onNode(hasSetTextAction())
        field.requestFocus()
        waitForIdle()
        field.assertIsFocused()
        field.assertTextContains("@$OLD_NAME", substring = true)
        // The interceptor sits on the path the decorator node uses, so the first session proves the counter sees it.
        assertEquals(1, sessionsStarted, "focusing the composer should open exactly one IME session")

        mentionCandidates = candidates(NEW_NAME)
        waitForIdle()

        field.assertIsFocused()
        field.assertTextContains("@$NEW_NAME", substring = true)
        field.assert(!hasText("@$OLD_NAME", substring = true))
        assertEquals(1, sessionsStarted, "a node rename must not restart the IME session of a focused composer")
    }

    private companion object {
        const val NODE_ID = "!a1b2c3d4"
        const val OLD_NAME = "Alpha Node"
        const val NEW_NAME = "Alpha Renamed"
    }
}
