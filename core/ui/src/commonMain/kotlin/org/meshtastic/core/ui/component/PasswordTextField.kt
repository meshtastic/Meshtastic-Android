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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.drop

private const val MAX_PENDING_ECHOES = 64

/**
 * A value-and-callback secret field over [OutlinedSecureTextField], masked with bullets unless [revealed].
 *
 * The field owns its text. A new [value] replaces it only when it is not one of the field's own edits coming back, so a
 * caller whose state lags the typing (a `StateFlow` collected in composition) cannot overwrite keystrokes with a stale
 * value. Cut and copy are disabled, as they were for `PasswordVisualTransformation`.
 *
 * @param maxBytes rejects any edit that would make the UTF-8 encoding longer than this; 0 means no limit.
 */
@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    maxBytes: Int = 0,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    // Not saveable: the caller's state is the source of truth, and a secret has no business in the saved-state bundle.
    val state = remember { TextFieldState(value) }
    val pendingEchoes = remember { ArrayDeque<String>() }
    val applied = remember { AppliedValue(value) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collect { text ->
                // The caller's own value, written below, is not an edit to hand back or to expect back.
                val replaced = applied.replacedWith
                applied.replacedWith = null
                if (replaced == text) return@collect
                if (pendingEchoes.size == MAX_PENDING_ECHOES) pendingEchoes.removeFirst()
                pendingEchoes.addLast(text)
                latestOnValueChange(text)
            }
    }
    SideEffect {
        if (applied.value != value) {
            applied.value = value
            val echo = pendingEchoes.indexOf(value)
            if (echo >= 0) {
                repeat(echo + 1) { pendingEchoes.removeFirst() }
            } else if (state.text.toString() != value) {
                pendingEchoes.clear()
                applied.replacedWith = value
                state.edit {
                    replace(0, length, value)
                    placeCursorAtEnd()
                }
            }
        }
    }

    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle,
        label =
        if (label != null) {
            { label() }
        } else {
            null
        },
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        inputTransformation = remember(maxBytes) { if (maxBytes > 0) MaxBytesTransformation(maxBytes) else null },
        textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
    )
}

/** Routes the [KeyboardActions] callback for [imeAction] through a [KeyboardActionHandler]. */
internal fun KeyboardActions.toHandler(imeAction: ImeAction): KeyboardActionHandler {
    val action =
        when (imeAction) {
            ImeAction.Done -> onDone
            ImeAction.Go -> onGo
            ImeAction.Next -> onNext
            ImeAction.Previous -> onPrevious
            ImeAction.Search -> onSearch
            ImeAction.Send -> onSend
            else -> null
        }
    return KeyboardActionHandler { performDefaultAction ->
        if (action == null) {
            performDefaultAction()
        } else {
            val scope =
                object : KeyboardActionScope {
                    override fun defaultKeyboardAction(imeAction: ImeAction) = performDefaultAction()
                }
            scope.action()
        }
    }
}

private class AppliedValue(var value: String, var replacedWith: String? = null)

private class MaxBytesTransformation(private val maxBytes: Int) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (asCharSequence().toString().encodeToByteArray().size > maxBytes) revertAllChanges()
    }
}
