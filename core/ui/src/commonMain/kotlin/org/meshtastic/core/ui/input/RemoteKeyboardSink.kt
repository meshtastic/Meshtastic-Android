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
package org.meshtastic.core.ui.input

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zero-size field that holds keyboard focus so both hardware keys and the soft keyboard reach a remote session.
 *
 * It never holds what was typed. Each edit - a typed character, an IME commit, a soft-keyboard backspace, a paste - is
 * read as input and reverted in the same transformation, so the field stays at [SINK_SENTINEL] with the caret at its
 * end; the sentinel is there so a soft backspace has something to delete. Keys the session sends itself are taken in
 * the preview pass, before the field could move its caret or edit with them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteKeyboardSink(focusRequester: FocusRequester, handler: RemoteKeyHandler, modifier: Modifier = Modifier) {
    val state = rememberTextFieldState(SINK_SENTINEL)
    val currentHandler by rememberUpdatedState(handler)
    val transformation = remember {
        InputTransformation {
            val text = asCharSequence()
            val edit =
                sinkEdit(
                    (0 until changes.changeCount).map { i ->
                        val range = changes.getRange(i)
                        SinkChange(
                            changes.getOriginalRange(i).length,
                            text.subSequence(range.min, range.max).toString(),
                        )
                    },
                )
            revertAllChanges()
            repeat(edit.deleted) { currentHandler.onBackspace() }
            deliverText(edit.inserted, currentHandler)
        }
    }
    BasicTextField(
        state = state,
        inputTransformation = transformation,
        modifier =
        modifier.size(1.dp).focusRequester(focusRequester).onPreviewKeyEvent { handleKey(it, currentHandler) },
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        cursorBrush = SolidColor(Color.Transparent),
        // No suggestions or composing: an IME rewriting a word in place would replay it as keystrokes.
        keyboardOptions =
        KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
    )
}

/**
 * Callbacks from [RemoteKeyboardSink]. Typed text arrives through [onText] without line breaks, which come through
 * [onEnter]. A null [onChord] leaves Ctrl/Alt+letter chords to the host instead of consuming them.
 */
class RemoteKeyHandler(
    val onText: (String) -> Unit,
    val onEnter: () -> Unit,
    val onBackspace: () -> Unit,
    val onKey: (RemoteKey) -> Unit,
    val onChord: ((Char, ctrl: Boolean, alt: Boolean) -> Unit)? = null,
)

/** What the keyboard sink always holds between edits. */
private const val SINK_SENTINEL = " "

/** Hands typed text over in runs, splitting it at line breaks so each one becomes an Enter. */
internal fun deliverText(text: String, handler: RemoteKeyHandler) {
    var start = 0
    for (i in text.indices) {
        if (text[i] == '\n' || text[i] == '\r') {
            if (i > start) handler.onText(text.substring(start, i))
            handler.onEnter()
            start = i + 1
        }
    }
    if (start < text.length) handler.onText(text.substring(start))
}

/** Hardware keys a remote session sends itself, rather than let the text field interpret. */
private val NAVIGATION_KEYS =
    mapOf(
        Key.DirectionUp to RemoteKey.UP,
        Key.DirectionDown to RemoteKey.DOWN,
        Key.DirectionLeft to RemoteKey.LEFT,
        Key.DirectionRight to RemoteKey.RIGHT,
        Key.MoveHome to RemoteKey.HOME,
        Key.MoveEnd to RemoteKey.END,
        Key.PageUp to RemoteKey.PAGE_UP,
        Key.PageDown to RemoteKey.PAGE_DOWN,
        Key.Escape to RemoteKey.ESCAPE,
        Key.Delete to RemoteKey.DELETE,
    )

private val LETTER_KEYS =
    listOf(
        Key.A,
        Key.B,
        Key.C,
        Key.D,
        Key.E,
        Key.F,
        Key.G,
        Key.H,
        Key.I,
        Key.J,
        Key.K,
        Key.L,
        Key.M,
        Key.N,
        Key.O,
        Key.P,
        Key.Q,
        Key.R,
        Key.S,
        Key.T,
        Key.U,
        Key.V,
        Key.W,
        Key.X,
        Key.Y,
        Key.Z,
    )
        .mapIndexed { i, key -> key to ('a' + i) }
        .toMap()

private val SINK_EDIT_KEYS = setOf(Key.Tab, Key.Backspace)

/** Handles the key if it is one the session sends itself; returns whether it did. */
private fun handleKey(event: KeyEvent, handler: RemoteKeyHandler): Boolean {
    val navigation = NAVIGATION_KEYS[event.key]
    val onChord = handler.onChord
    val chordLetter = LETTER_KEYS[event.key]?.takeIf { onChord != null && (event.isCtrlPressed || event.isAltPressed) }
    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
    val handled =
        event.type == KeyEventType.KeyDown &&
            (navigation != null || chordLetter != null || isEnter || event.key in SINK_EDIT_KEYS)
    if (handled) {
        when {
            navigation != null -> handler.onKey(navigation)
            chordLetter != null -> onChord?.invoke(chordLetter, event.isCtrlPressed, event.isAltPressed)
            isEnter -> handler.onEnter()
            event.key == Key.Tab -> handler.onKey(RemoteKey.TAB)
            else -> handler.onBackspace()
        }
    }
    return handled
}
