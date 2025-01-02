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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ModuleConfigProtos.RemoteHardwarePin
import com.geeksville.mesh.ModuleConfigProtos.RemoteHardwarePinType
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.remoteHardwarePin
import com.google.protobuf.ByteString

@Composable
inline fun <reified T> EditListPreference(
    title: String,
    list: List<T>,
    maxCount: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    crossinline onValuesChanged: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val listState = remember(list) { mutableStateListOf<T>().apply { addAll(list) } }

    Column(modifier = modifier) {
        Text(
            modifier = modifier.padding(16.dp),
            text = title,
            style = MaterialTheme.typography.body2,
            color = if (enabled) {
                Color.Unspecified
            } else {
                MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            },
        )
        listState.forEachIndexed { index, value ->
            val trailingIcon = @Composable {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        listState.removeAt(index)
                        onValuesChanged(listState)
                    }
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Close,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.wrapContentSize(),
                    )
                }
            }

            // handle lora.ignoreIncoming: List<Int>
            if (value is Int) EditTextPreference(
                title = "${index + 1}/$maxCount",
                value = value,
                enabled = enabled,
                keyboardActions = keyboardActions,
                onValueChanged = {
                    listState[index] = it as T
                    onValuesChanged(listState)
                },
                modifier = modifier.fillMaxWidth(),
                trailingIcon = trailingIcon,
            )

            // handle security.adminKey: List<ByteString>
            if (value is ByteString) EditBase64Preference(
                title = "${index + 1}/$maxCount",
                value = value,
                enabled = enabled,
                keyboardActions = keyboardActions,
                onValueChange = {
                    listState[index] = it as T
                    onValuesChanged(listState)
                },
                modifier = modifier.fillMaxWidth(),
                trailingIcon = trailingIcon,
            )

            // handle remoteHardware.availablePins: List<RemoteHardwarePin>
            if (value is RemoteHardwarePin) {
                EditTextPreference(
                    title = "GPIO pin",
                    value = value.gpioPin,
                    enabled = enabled,
                    keyboardActions = keyboardActions,
                    onValueChanged = {
                        if (it in 0..255) {
                            listState[index] = value.copy { gpioPin = it } as T
                            onValuesChanged(listState)
                        }
                    },
                )
                EditTextPreference(
                    title = "Name",
                    value = value.name,
                    maxSize = 14, // name max_size:15
                    enabled = enabled,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                    ),
                    keyboardActions = keyboardActions,
                    onValueChanged = {
                        listState[index] = value.copy { name = it } as T
                        onValuesChanged(listState)
                    },
                    trailingIcon = trailingIcon,
                )
                DropDownPreference(
                    title = "Type",
                    enabled = enabled,
                    items = RemoteHardwarePinType.entries
                        .filter { it != RemoteHardwarePinType.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = value.type,
                    onItemSelected = {
                        listState[index] = value.copy { type = it } as T
                        onValuesChanged(listState)
                    },
                )
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // Add element based on the type T
                val newElement = when (T::class) {
                    Int::class -> 0 as T
                    ByteString::class -> ByteString.EMPTY as T
                    RemoteHardwarePin::class -> remoteHardwarePin {} as T
                    else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
                }
                listState.add(listState.size, newElement)
            },
            enabled = maxCount > listState.size,
            colors = ButtonDefaults.buttonColors(
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            )
        ) { Text(text = stringResource(R.string.add)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditListPreferencePreview() {
    Column {
        EditListPreference(
            title = "Ignore incoming",
            list = listOf(12345, 67890),
            maxCount = 4,
            enabled = true,
            keyboardActions = KeyboardActions {},
            onValuesChanged = {},
        )
        EditListPreference(
            title = "Available pins",
            list = listOf(
                remoteHardwarePin {
                    gpioPin = 12
                    name = "Front door"
                    type = RemoteHardwarePinType.DIGITAL_READ
                },
            ),
            maxCount = 4,
            enabled = true,
            keyboardActions = KeyboardActions {},
            onValuesChanged = {},
        )
    }
}
