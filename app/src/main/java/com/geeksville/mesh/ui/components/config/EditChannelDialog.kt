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

package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.EditBase64Preference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PositionPrecisionPreference
import com.geeksville.mesh.ui.components.SwitchPreference

@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditChannelDialog(
    channelSettings: ChannelProtos.ChannelSettings,
    onAddClick: (ChannelProtos.ChannelSettings) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    modemPresetName: String = "Default",
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    var channelInput by remember(channelSettings) { mutableStateOf(channelSettings) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background,
        text = {
            Column(modifier.fillMaxWidth()) {
                EditTextPreference(
                    title = stringResource(R.string.channel_name),
                    value = if (isFocused) channelInput.name else channelInput.name.ifEmpty { modemPresetName },
                    maxSize = 11, // name max_size:12
                    enabled = true,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        channelInput = channelInput.copy {
                            name = it.trim()
                            if (psk == Channel.default.settings.psk) psk = Channel.getRandomKey()
                        }
                    },
                    onFocusChanged = { isFocused = it.isFocused },
                )

                EditBase64Preference(
                    title = "PSK",
                    value = channelInput.psk,
                    enabled = true,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChange = {
                        val fullPsk = Channel(channelSettings { psk = it }).psk
                        if (fullPsk.size() in setOf(0, 16, 32)) {
                            channelInput = channelInput.copy { psk = it }
                        }
                    },
                    onGenerateKey = {
                        channelInput = channelInput.copy { psk = Channel.getRandomKey() }
                    },
                )

                SwitchPreference(
                    title = "Uplink enabled",
                    checked = channelInput.uplinkEnabled,
                    enabled = true,
                    onCheckedChange = {
                        channelInput = channelInput.copy { uplinkEnabled = it }
                    },
                    padding = PaddingValues(0.dp)
                )

                SwitchPreference(
                    title = "Downlink enabled",
                    checked = channelInput.downlinkEnabled,
                    enabled = true,
                    onCheckedChange = {
                        channelInput = channelInput.copy { downlinkEnabled = it }
                    },
                    padding = PaddingValues(0.dp)
                )

                PositionPrecisionPreference(
                    title = "Position enabled",
                    enabled = true,
                    value = channelInput.moduleSettings.positionPrecision,
                    onValueChanged = {
                        val module = channelInput.moduleSettings.copy { positionPrecision = it }
                        channelInput = channelInput.copy { moduleSettings = module }
                    },
                )
            }
        },
        buttons = {
            FlowRow(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = modifier.weight(1f),
                    onClick = onDismissRequest
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = modifier.weight(1f),
                    onClick = {
                        onAddClick(channelInput)
                    },
                    enabled = true,
                ) { Text(stringResource(R.string.save)) }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun EditChannelDialogPreview() {
    EditChannelDialog(
        channelSettings = channelSettings {
            psk = Channel.default.settings.psk
            name = Channel.default.name
        },
        onAddClick = { },
        onDismissRequest = { },
    )
}
