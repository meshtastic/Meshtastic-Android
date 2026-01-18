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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.allow_input_source
import org.meshtastic.core.strings.canned_message
import org.meshtastic.core.strings.canned_message_config
import org.meshtastic.core.strings.canned_message_enabled
import org.meshtastic.core.strings.generate_input_event_on_ccw
import org.meshtastic.core.strings.generate_input_event_on_cw
import org.meshtastic.core.strings.generate_input_event_on_press
import org.meshtastic.core.strings.gpio_pin_for_rotary_encoder_a_port
import org.meshtastic.core.strings.gpio_pin_for_rotary_encoder_b_port
import org.meshtastic.core.strings.gpio_pin_for_rotary_encoder_press_port
import org.meshtastic.core.strings.messages
import org.meshtastic.core.strings.rotary_encoder_1_enabled
import org.meshtastic.core.strings.send_bell
import org.meshtastic.core.strings.up_down_select_input_enabled
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Composable
fun CannedMessageConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val cannedMessageConfig = state.moduleConfig.canned_message ?: ModuleConfig.CannedMessageConfig()
    val messages = state.cannedMessageMessages
    val formState = rememberConfigState(initialValue = cannedMessageConfig)
    var messagesInput by rememberSaveable(messages) { mutableStateOf(messages) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.canned_message),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        additionalDirtyCheck = { messagesInput != messages },
        onDiscard = { messagesInput = messages },
        onSave = {
            if (messagesInput != messages) {
                viewModel.setCannedMessages(messagesInput)
            }
            if (formState.value != cannedMessageConfig) {
                val config = ModuleConfig(canned_message = formState.value)
                viewModel.setModuleConfig(config)
            }
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.canned_message_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.canned_message_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.rotary_encoder_1_enabled),
                    checked = formState.value.rotary1_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(rotary1_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.gpio_pin_for_rotary_encoder_a_port),
                    value = formState.value.inputbroker_pin_a ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(inputbroker_pin_a = it) },
                )
                EditTextPreference(
                    title = stringResource(Res.string.gpio_pin_for_rotary_encoder_b_port),
                    value = formState.value.inputbroker_pin_b ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(inputbroker_pin_b = it) },
                )
                EditTextPreference(
                    title = stringResource(Res.string.gpio_pin_for_rotary_encoder_press_port),
                    value = formState.value.inputbroker_pin_press ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(inputbroker_pin_press = it) },
                )
                DropDownPreference(
                    title = stringResource(Res.string.generate_input_event_on_press),
                    enabled = state.connected,
                    items = ModuleConfig.CannedMessageConfig.InputEventChar.entries.map { it to it.name },
                    selectedItem =
                    formState.value.inputbroker_event_press ?: ModuleConfig.CannedMessageConfig.InputEventChar.NONE,
                    onItemSelected = { formState.value = formState.value.copy(inputbroker_event_press = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.generate_input_event_on_cw),
                    enabled = state.connected,
                    items = ModuleConfig.CannedMessageConfig.InputEventChar.entries.map { it to it.name },
                    selectedItem =
                    formState.value.inputbroker_event_cw ?: ModuleConfig.CannedMessageConfig.InputEventChar.NONE,
                    onItemSelected = { formState.value = formState.value.copy(inputbroker_event_cw = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.generate_input_event_on_ccw),
                    enabled = state.connected,
                    items = ModuleConfig.CannedMessageConfig.InputEventChar.entries.map { it to it.name },
                    selectedItem =
                    formState.value.inputbroker_event_ccw ?: ModuleConfig.CannedMessageConfig.InputEventChar.NONE,
                    onItemSelected = { formState.value = formState.value.copy(inputbroker_event_ccw = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.up_down_select_input_enabled),
                    checked = formState.value.updown1_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(updown1_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.allow_input_source),
                    value = formState.value.allow_input_source ?: "",
                    maxSize = 63, // allow_input_source max_size:16
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(allow_input_source = it) },
                )
                SwitchPreference(
                    title = stringResource(Res.string.send_bell),
                    checked = formState.value.send_bell ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(send_bell = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.messages),
                    value = messagesInput,
                    maxSize = 200, // messages max_size:201
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { messagesInput = it },
                )
            }
        }
    }
}
