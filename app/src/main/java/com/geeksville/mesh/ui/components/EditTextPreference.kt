/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun EditTextPreference(
    title: String,
    value: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (FocusState) -> Unit = {},
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    var valueState by remember(value) { mutableStateOf(value.toUInt().toString()) }

    EditTextPreference(
        title = title,
        value = valueState,
        enabled = enabled,
        isError = value.toUInt().toString() != valueState,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onValueChanged = {
            if (it.isEmpty()) valueState = it
            else it.toUIntOrNull()?.toInt()?.let { int ->
                valueState = it
                onValueChanged(int)
            }
        },
        onFocusChanged = onFocusChanged,
        modifier = modifier,
        trailingIcon = trailingIcon
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: Float,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (FocusState) -> Unit = {},
    ) {
    var valueState by remember(value) { mutableStateOf(value.toString()) }

    EditTextPreference(
        title = title,
        value = valueState,
        enabled = enabled,
        isError = value.toString() != valueState,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onValueChanged = {
            if (it.isEmpty()) valueState = it
            else it.toFloatOrNull()?.let { float ->
                valueState = it
                onValueChanged(float)
            }
        },
        onFocusChanged = onFocusChanged,
        modifier = modifier
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: Double,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var valueState by remember(value) { mutableStateOf(value.toString()) }
    val decimalSeparators = setOf('.', ',', '٫', '、', '·') // set of possible decimal separators

    EditTextPreference(
        title = title,
        value = valueState,
        enabled = enabled,
        isError = value.toString() != valueState,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onValueChanged = {
            if (it.length <= 1 || it.first() in decimalSeparators) valueState = it
            else it.toDoubleOrNull()?.let { double ->
                valueState = it
                onValueChanged(double)
            }
        },
        onFocusChanged = {},
        modifier = modifier
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: String,
    enabled: Boolean,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxSize: Int = 0, // max_size - 1 (in bytes)
    onFocusChanged: (FocusState) -> Unit = {},
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var isFocused by remember { mutableStateOf(false) }

    TextField(
        value = value,
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .onFocusEvent { isFocused = it.isFocused; onFocusChanged(it) },
        enabled = enabled,
        isError = isError,
        onValueChange = {
            if (maxSize > 0) {
                if (it.toByteArray().size <= maxSize) {
                    onValueChanged(it)
                }
            } else onValueChanged(it)
        },
        label = { Text(title) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = {
            if (trailingIcon != null) {
                trailingIcon()
            } else if (isError) {
                Icon(
                    imageVector = Icons.TwoTone.Info,
                    contentDescription = stringResource(id = R.string.error),
                    tint = MaterialTheme.colors.error
                )
            }
        },
    )

    if (maxSize > 0 && isFocused) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = "${value.toByteArray().size}/$maxSize",
                style = MaterialTheme.typography.caption,
                color = if (isError) MaterialTheme.colors.error else MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditTextPreferencePreview() {
    Column {
        EditTextPreference(
            title = "String",
            value = "Meshtastic",
            maxSize = 39,
            enabled = true,
            isError = false,
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions {},
            onValueChanged = {},
        )
        EditTextPreference(
            title = "Advanced Settings",
            value = UInt.MAX_VALUE.toInt(),
            enabled = true,
            keyboardActions = KeyboardActions {},
            onValueChanged = {}
        )
    }
}
