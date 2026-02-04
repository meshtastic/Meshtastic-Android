/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.gpio_pin
import org.meshtastic.core.strings.ignore_incoming
import org.meshtastic.core.strings.name
import org.meshtastic.core.strings.type
import org.meshtastic.proto.RemoteHardwarePin
import org.meshtastic.proto.RemoteHardwarePinType

@Suppress("LongMethod")
@Composable
inline fun <reified T> EditListPreference(
    title: String,
    list: List<T>,
    maxCount: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    crossinline onValuesChanged: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val focusManager = LocalFocusManager.current
    val listState = remember(list) { mutableStateListOf<T>().apply { addAll(list) } }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        listState.forEachIndexed { index, value ->
            val trailingIcon =
                @Composable {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            listState.removeAt(index)
                            onValuesChanged(listState)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Close,
                            contentDescription = stringResource(Res.string.delete),
                            modifier = Modifier.wrapContentSize(),
                        )
                    }
                }

            when (value) {
                is Int -> {
                    EditTextPreference(
                        title = "${index + 1}/$maxCount",
                        value = value,
                        enabled = enabled,
                        keyboardActions = keyboardActions,
                        onValueChanged = {
                            listState[index] = it as T
                            onValuesChanged(listState)
                        },
                        trailingIcon = trailingIcon,
                    )
                }
                is okio.ByteString -> {
                    EditBase64Preference(
                        title = "${index + 1}/$maxCount",
                        value = value,
                        enabled = enabled,
                        keyboardActions = keyboardActions,
                        onValueChange = {
                            listState[index] = it as T
                            onValuesChanged(listState)
                        },
                        trailingIcon = trailingIcon,
                    )
                }
                is RemoteHardwarePin -> {
                    EditTextPreference(
                        title = stringResource(Res.string.gpio_pin),
                        value = value.gpio_pin,
                        enabled = enabled,
                        keyboardActions = keyboardActions,
                        onValueChanged = { newValue ->
                            val it = newValue as Int
                            if (it in 0..255) {
                                listState[index] = value.copy(gpio_pin = it) as T
                                onValuesChanged(listState)
                            }
                        },
                    )
                    EditTextPreference(
                        title = stringResource(Res.string.name),
                        value = value.name,
                        maxSize = 14, // name max_size:15
                        enabled = enabled,
                        isError = false,
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = keyboardActions,
                        onValueChanged = { newValue ->
                            val it = newValue as String
                            listState[index] = value.copy(name = it) as T
                            onValuesChanged(listState)
                        },
                        trailingIcon = trailingIcon,
                    )
                    DropDownPreference(
                        title = stringResource(Res.string.type),
                        enabled = enabled,
                        items =
                        RemoteHardwarePinType.entries
                            .filter { it != RemoteHardwarePinType.UNKNOWN }
                            .map { it to it.name },
                        selectedItem = value.type,
                        onItemSelected = {
                            listState[index] = value.copy(type = it) as T
                            onValuesChanged(listState)
                        },
                    )
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // Add element based on the type T
                val newElement =
                    when (T::class) {
                        Int::class -> 0 as T
                        okio.ByteString::class -> okio.ByteString.EMPTY as T
                        RemoteHardwarePin::class -> RemoteHardwarePin() as T
                        else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
                    }
                listState.add(listState.size, newElement)
            },
            enabled = maxCount > listState.size,
        ) {
            Text(text = stringResource(Res.string.add))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditListPreferencePreview() {
    Column {
        EditListPreference(
            title = stringResource(Res.string.ignore_incoming),
            summary = "This is a summary",
            list = listOf(12345, 67890),
            maxCount = 4,
            enabled = true,
            keyboardActions = KeyboardActions {},
            onValuesChanged = {},
        )
        EditListPreference(
            title = "Available pins",
            list =
            listOf(
                RemoteHardwarePin(gpio_pin = 12, name = "Front door", type = RemoteHardwarePinType.DIGITAL_READ),
            ),
            maxCount = 4,
            enabled = true,
            keyboardActions = KeyboardActions {},
            onValuesChanged = {},
        )
    }
}
