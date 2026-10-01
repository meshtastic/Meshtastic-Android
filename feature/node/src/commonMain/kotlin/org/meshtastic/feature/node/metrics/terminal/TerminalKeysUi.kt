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
package org.meshtastic.feature.node.metrics.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.remote_shell_key_alt
import org.meshtastic.core.resources.remote_shell_key_ctrl
import org.meshtastic.core.resources.remote_shell_key_down
import org.meshtastic.core.resources.remote_shell_key_end
import org.meshtastic.core.resources.remote_shell_key_escape
import org.meshtastic.core.resources.remote_shell_key_home
import org.meshtastic.core.resources.remote_shell_key_left
import org.meshtastic.core.resources.remote_shell_key_page_down
import org.meshtastic.core.resources.remote_shell_key_page_up
import org.meshtastic.core.resources.remote_shell_key_right
import org.meshtastic.core.resources.remote_shell_key_tab
import org.meshtastic.core.resources.remote_shell_key_up
import org.meshtastic.core.resources.remote_shell_modifier_locked
import org.meshtastic.core.resources.remote_shell_modifier_once

private val KEY_HEIGHT = 48.dp
private val KEY_SPACING = 2.dp
private val KEY_LABEL_SIZE = 13.sp

/** Reset the invisible sink past this length so it does not accumulate a whole session of keystrokes. */
private const val SINK_TRIM_LENGTH = 256

private sealed interface ExtraKey {
    data class Special(val key: TerminalKey, val label: String, val description: StringResource) : ExtraKey

    data class Typed(val char: Char) : ExtraKey

    data object Ctrl : ExtraKey

    data object Alt : ExtraKey
}

/** Termux's default extra-keys layout: the two rows its users already have in their thumbs. */
private val EXTRA_KEY_ROWS: List<List<ExtraKey>> =
    listOf(
        listOf(
            ExtraKey.Special(TerminalKey.ESCAPE, "ESC", Res.string.remote_shell_key_escape),
            ExtraKey.Typed('/'),
            ExtraKey.Typed('-'),
            ExtraKey.Special(TerminalKey.HOME, "HOME", Res.string.remote_shell_key_home),
            ExtraKey.Special(TerminalKey.UP, "↑", Res.string.remote_shell_key_up),
            ExtraKey.Special(TerminalKey.END, "END", Res.string.remote_shell_key_end),
            ExtraKey.Special(TerminalKey.PAGE_UP, "PGUP", Res.string.remote_shell_key_page_up),
        ),
        listOf(
            ExtraKey.Special(TerminalKey.TAB, "TAB", Res.string.remote_shell_key_tab),
            ExtraKey.Ctrl,
            ExtraKey.Alt,
            ExtraKey.Special(TerminalKey.LEFT, "←", Res.string.remote_shell_key_left),
            ExtraKey.Special(TerminalKey.DOWN, "↓", Res.string.remote_shell_key_down),
            ExtraKey.Special(TerminalKey.RIGHT, "→", Res.string.remote_shell_key_right),
            ExtraKey.Special(TerminalKey.PAGE_DOWN, "PGDN", Res.string.remote_shell_key_page_down),
        ),
    )

/**
 * The extra-keys rows. CTRL and ALT are sticky: one tap applies to the next key, a second tap locks them, a third
 * releases.
 */
@Composable
internal fun ExtraKeysBar(
    modifiers: Modifiers,
    onKey: (TerminalKey) -> Unit,
    onChar: (Char) -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = KEY_SPACING)) {
        EXTRA_KEY_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    when (key) {
                        is ExtraKey.Special ->
                            KeyCap(label = key.label, description = stringResource(key.description)) { onKey(key.key) }

                        is ExtraKey.Typed ->
                            KeyCap(label = key.char.toString(), description = null) { onChar(key.char) }

                        ExtraKey.Ctrl ->
                            KeyCap(
                                label = "CTRL",
                                description = stringResource(Res.string.remote_shell_key_ctrl),
                                state = modifiers.ctrl,
                                onClick = onToggleCtrl,
                            )

                        ExtraKey.Alt ->
                            KeyCap(
                                label = "ALT",
                                description = stringResource(Res.string.remote_shell_key_alt),
                                state = modifiers.alt,
                                onClick = onToggleAlt,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyCap(label: String, description: String?, state: ModifierState? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (state) {
            ModifierState.ONCE -> colors.primaryContainer to colors.onPrimaryContainer
            ModifierState.LOCKED -> colors.primary to colors.onPrimary
            else -> Color.Transparent to colors.primary
        }
    val stateText =
        when (state) {
            ModifierState.ONCE -> stringResource(Res.string.remote_shell_modifier_once)
            ModifierState.LOCKED -> stringResource(Res.string.remote_shell_modifier_locked)
            else -> null
        }
    Box(
        modifier =
        Modifier.weight(1f)
            .height(KEY_HEIGHT)
            .padding(KEY_SPACING)
            .background(container, MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                description?.let { contentDescription = it }
                stateText?.let { stateDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = KEY_LABEL_SIZE,
            maxLines = 1,
        )
    }
}

/** Hardware keys a terminal must send itself, rather than let the text field interpret. */
private val NAVIGATION_KEYS =
    mapOf(
        Key.DirectionUp to TerminalKey.UP,
        Key.DirectionDown to TerminalKey.DOWN,
        Key.DirectionLeft to TerminalKey.LEFT,
        Key.DirectionRight to TerminalKey.RIGHT,
        Key.MoveHome to TerminalKey.HOME,
        Key.MoveEnd to TerminalKey.END,
        Key.PageUp to TerminalKey.PAGE_UP,
        Key.PageDown to TerminalKey.PAGE_DOWN,
        Key.Escape to TerminalKey.ESCAPE,
        Key.Delete to TerminalKey.DELETE,
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

/** Callbacks from the keyboard sink, grouped so the composable stays readable. */
internal class TerminalKeyHandler(
    val onChar: (Char) -> Unit,
    val onEnter: () -> Unit,
    val onBackspace: () -> Unit,
    val onKey: (TerminalKey) -> Unit,
    val onChord: (Char, ctrl: Boolean, alt: Boolean) -> Unit,
)

/**
 * Zero-size field that holds keyboard focus so both hardware keys and the soft keyboard reach the session.
 *
 * The value is state-backed and never cleared outright: Compose hands back the field's whole content, and a reset only
 * lands on the next recomposition, so a callback arriving first would re-deliver characters already sent. We track what
 * we consumed and forward the delta, mapping a shrinking field to backspaces.
 */
@Composable
internal fun KeyboardSink(focusRequester: FocusRequester, handler: TerminalKeyHandler) {
    var sinkText by remember { mutableStateOf("") }
    BasicTextField(
        value = sinkText,
        onValueChange = { newText ->
            val consumed = sinkText
            if (newText.length < consumed.length && consumed.startsWith(newText)) {
                repeat(consumed.length - newText.length) { handler.onBackspace() }
            } else {
                val fresh = if (newText.startsWith(consumed)) newText.substring(consumed.length) else newText
                fresh.forEach { c -> if (c == '\n' || c == '\r') handler.onEnter() else handler.onChar(c) }
            }
            sinkText = if (newText.length > SINK_TRIM_LENGTH) "" else newText
        },
        modifier = Modifier.size(1.dp).focusRequester(focusRequester).onKeyEvent { handleKey(it, handler) },
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        cursorBrush = SolidColor(Color.Transparent),
    )
}

/** Handles the key if it is one the terminal sends itself; returns whether it did. */
private fun handleKey(event: KeyEvent, handler: TerminalKeyHandler): Boolean {
    val navigation = NAVIGATION_KEYS[event.key]
    val chordLetter = LETTER_KEYS[event.key]?.takeIf { event.isCtrlPressed || event.isAltPressed }
    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
    val handled =
        event.type == KeyEventType.KeyDown &&
            (navigation != null || chordLetter != null || isEnter || event.key in SINK_EDIT_KEYS)
    if (handled) {
        when {
            navigation != null -> handler.onKey(navigation)
            chordLetter != null -> handler.onChord(chordLetter, event.isCtrlPressed, event.isAltPressed)
            isEnter -> handler.onEnter()
            event.key == Key.Tab -> handler.onKey(TerminalKey.TAB)
            else -> handler.onBackspace()
        }
    }
    return handled
}

private val SINK_EDIT_KEYS = setOf(Key.Tab, Key.Backspace)
