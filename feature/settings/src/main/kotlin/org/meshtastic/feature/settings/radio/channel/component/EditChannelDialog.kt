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
package org.meshtastic.feature.settings.radio.channel.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.channel_name
import org.meshtastic.core.strings.default_
import org.meshtastic.core.strings.downlink_enabled
import org.meshtastic.core.strings.save
import org.meshtastic.core.strings.uplink_enabled
import org.meshtastic.core.ui.component.EditBase64Preference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.PositionPrecisionPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ModuleSettings

@Suppress("LongMethod")
@Composable
fun EditChannelDialog(
    channelSettings: ChannelSettings,
    onAddClick: (ChannelSettings) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    modemPresetName: String = stringResource(Res.string.default_),
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    var channelInput by remember(channelSettings) { mutableStateOf(channelSettings) }

    MeshtasticDialog(
        onDismiss = onDismissRequest,
        dismissText = stringResource(Res.string.cancel),
        confirmText = stringResource(Res.string.save),
        onConfirm = { onAddClick(channelInput) },
        modifier = modifier,
        title = "", // Title is handled internally by specific items if needed, or we could add one
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                EditTextPreference(
                    title = stringResource(Res.string.channel_name),
                    value =
                    if (isFocused) {
                        (channelInput.name ?: "")
                    } else {
                        (channelInput.name ?: "").ifEmpty { modemPresetName }
                    },
                    maxSize = 11, // name max_size:12
                    enabled = true,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        val defaultPsk = Channel.default.settings.psk
                        val newPsk =
                            if (channelInput.psk == defaultPsk) {
                                Channel.getRandomKey()
                            } else {
                                (channelInput.psk ?: okio.ByteString.EMPTY)
                            }
                        channelInput = channelInput.copy(name = it.trim(), psk = newPsk)
                    },
                    onFocusChanged = { isFocused = it.isFocused },
                )

                EditBase64Preference(
                    title = "PSK",
                    value = channelInput.psk ?: okio.ByteString.EMPTY,
                    enabled = true,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChange = {
                        val fullPsk = Channel(ChannelSettings(psk = it)).psk
                        if (fullPsk.size in setOf(0, 16, 32)) {
                            channelInput = channelInput.copy(psk = it)
                        }
                    },
                    onGenerateKey = { channelInput = channelInput.copy(psk = Channel.getRandomKey()) },
                )

                SwitchPreference(
                    title = stringResource(Res.string.uplink_enabled),
                    checked = channelInput.uplink_enabled ?: false,
                    enabled = true,
                    onCheckedChange = { channelInput = channelInput.copy(uplink_enabled = it) },
                    padding = PaddingValues(0.dp),
                )

                SwitchPreference(
                    title = stringResource(Res.string.downlink_enabled),
                    checked = channelInput.downlink_enabled ?: false,
                    enabled = true,
                    onCheckedChange = { channelInput = channelInput.copy(downlink_enabled = it) },
                    padding = PaddingValues(0.dp),
                )

                val moduleSettings = channelInput.module_settings ?: ModuleSettings()
                PositionPrecisionPreference(
                    enabled = true,
                    value = moduleSettings.position_precision ?: 0,
                    onValueChanged = {
                        val updatedModule = moduleSettings.copy(position_precision = it)
                        channelInput = channelInput.copy(module_settings = updatedModule)
                    },
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun EditChannelDialogPreview() {
    EditChannelDialog(
        channelSettings = ChannelSettings(psk = Channel.default.settings.psk, name = Channel.default.name),
        onAddClick = {},
        onDismissRequest = {},
    )
}
