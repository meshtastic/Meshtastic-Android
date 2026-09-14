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
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Enter-to-send is a physical-keyboard shortcut (#5246). These run on the desktop target, where every key event is
 * physical, so they pin that half of the contract; the on-screen keyboard is exempted by isFromSoftKeyboard and cannot
 * be driven from here.
 */
@OptIn(ExperimentalTestApi::class)
class MessageInputEnterKeyTest {

    @Test
    fun enterSendsAndLeavesTheTextAlone() = runComposeUiTest {
        var sent = 0
        val textFieldState = TextFieldState(DRAFT)
        setContent { Composer(textFieldState) { sent++ } }

        val field = onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(1, sent, "Enter on a physical keyboard should send")
        assertEquals(DRAFT, textFieldState.text.toString(), "sending must not leave a trailing newline behind")
    }

    @Test
    fun shiftEnterInsertsANewlineAndDoesNotSend() = runComposeUiTest {
        var sent = 0
        val textFieldState = TextFieldState(DRAFT)
        setContent { Composer(textFieldState) { sent++ } }

        val field = onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        waitForIdle()

        assertEquals(0, sent, "Shift+Enter composes, it does not send")
        assertTrue(textFieldState.text.contains('\n'), "Shift+Enter should insert a newline")
    }

    @Test
    fun enterOnAnEmptyComposerSendsNothing() = runComposeUiTest {
        var sent = 0
        val textFieldState = TextFieldState("")
        setContent { Composer(textFieldState) { sent++ } }

        val field = onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(0, sent, "an empty composer has nothing to send")
        assertFalse(textFieldState.text.contains('\n'), "the consumed Enter must not fall through to the field")
    }

    @Composable
    private fun Composer(textFieldState: TextFieldState, onSendMessage: () -> Unit) {
        MaterialTheme {
            MessageInput(
                isEnabled = true,
                isHomoglyphEncodingEnabled = false,
                textFieldState = textFieldState,
                mentionCandidates = persistentMapOf(),
                onSendMessage = onSendMessage,
            )
        }
    }

    private companion object {
        const val DRAFT = "hello mesh"
    }
}
