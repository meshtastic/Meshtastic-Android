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

package com.geeksville.mesh.ui.settings.radio.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.CannedMessageConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R

@Composable
fun CannedMessageConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    CannedMessageConfigItemList(
        messages = state.cannedMessageMessages,
        cannedMessageConfig = state.moduleConfig.cannedMessage,
        enabled = state.connected,
        onSaveClicked = { messagesInput, cannedMessageInput ->
            if (messagesInput != state.cannedMessageMessages) {
                viewModel.setCannedMessages(messagesInput)
            }
            if (cannedMessageInput != state.moduleConfig.cannedMessage) {
                val config = moduleConfig { cannedMessage = cannedMessageInput }
                viewModel.setModuleConfig(config)
            }
        },
    )
}

@Composable
fun CannedMessageConfigItemList(
    messages: String,
    cannedMessageConfig: CannedMessageConfig,
    enabled: Boolean,
    onSaveClicked: (messages: String, config: CannedMessageConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var messagesInput by rememberSaveable { mutableStateOf(messages) }
    var cannedMessageInput by rememberSaveable { mutableStateOf(cannedMessageConfig) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceCategory(text = stringResource(R.string.canned_message_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.canned_message_enabled),
                checked = cannedMessageInput.enabled,
                enabled = enabled,
                onCheckedChange = { cannedMessageInput = cannedMessageInput.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.rotary_encoder_1_enabled),
                checked = cannedMessageInput.rotary1Enabled,
                enabled = enabled,
                onCheckedChange = { cannedMessageInput = cannedMessageInput.copy { rotary1Enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.gpio_pin_for_rotary_encoder_a_port),
                value = cannedMessageInput.inputbrokerPinA,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { cannedMessageInput = cannedMessageInput.copy { inputbrokerPinA = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.gpio_pin_for_rotary_encoder_b_port),
                value = cannedMessageInput.inputbrokerPinB,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { cannedMessageInput = cannedMessageInput.copy { inputbrokerPinB = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.gpio_pin_for_rotary_encoder_press_port),
                value = cannedMessageInput.inputbrokerPinPress,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { cannedMessageInput = cannedMessageInput.copy { inputbrokerPinPress = it } },
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.generate_input_event_on_press),
                enabled = enabled,
                items =
                CannedMessageConfig.InputEventChar.entries
                    .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventPress,
                onItemSelected = { cannedMessageInput = cannedMessageInput.copy { inputbrokerEventPress = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            DropDownPreference(
                title = stringResource(R.string.generate_input_event_on_cw),
                enabled = enabled,
                items =
                CannedMessageConfig.InputEventChar.entries
                    .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventCw,
                onItemSelected = { cannedMessageInput = cannedMessageInput.copy { inputbrokerEventCw = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            DropDownPreference(
                title = stringResource(R.string.generate_input_event_on_ccw),
                enabled = enabled,
                items =
                CannedMessageConfig.InputEventChar.entries
                    .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventCcw,
                onItemSelected = { cannedMessageInput = cannedMessageInput.copy { inputbrokerEventCcw = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.up_down_select_input_enabled),
                checked = cannedMessageInput.updown1Enabled,
                enabled = enabled,
                onCheckedChange = { cannedMessageInput = cannedMessageInput.copy { updown1Enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.allow_input_source),
                value = cannedMessageInput.allowInputSource,
                maxSize = 63, // allow_input_source max_size:16
                enabled = enabled,
                isError = false,
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { cannedMessageInput = cannedMessageInput.copy { allowInputSource = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.send_bell),
                checked = cannedMessageInput.sendBell,
                enabled = enabled,
                onCheckedChange = { cannedMessageInput = cannedMessageInput.copy { sendBell = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.messages),
                value = messagesInput,
                maxSize = 200, // messages max_size:201
                enabled = enabled,
                isError = false,
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { messagesInput = it },
            )
        }

        item {
            PreferenceFooter(
                enabled = enabled && cannedMessageInput != cannedMessageConfig || messagesInput != messages,
                onCancelClicked = {
                    focusManager.clearFocus()
                    messagesInput = messages
                    cannedMessageInput = cannedMessageConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(messagesInput, cannedMessageInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CannedMessageConfigPreview() {
    CannedMessageConfigItemList(
        messages = "",
        cannedMessageConfig = CannedMessageConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { _, _ -> },
    )
}
