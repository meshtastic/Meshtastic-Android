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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfigProtos.ModuleConfig.CannedMessageConfig
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun CannedMessageConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val cannedMessageConfig = state.moduleConfig.cannedMessage
    val messages = state.cannedMessageMessages
    val formState = rememberConfigState(initialValue = cannedMessageConfig)
    var messagesInput by rememberSaveable(messages) { mutableStateOf(messages) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.canned_message),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            if (messagesInput != messages) {
                viewModel.setCannedMessages(messagesInput)
            }
            if (formState.value != cannedMessageConfig) {
                val config = moduleConfig { cannedMessage = formState.value }
                viewModel.setModuleConfig(config)
            }
        },
    ) {
        item {
            TitledCard(title = stringResource(R.string.canned_message_config)) {
                SwitchPreference(
                    title = stringResource(R.string.canned_message_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.rotary_encoder_1_enabled),
                    checked = formState.value.rotary1Enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { rotary1Enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.gpio_pin_for_rotary_encoder_a_port),
                    value = formState.value.inputbrokerPinA,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { inputbrokerPinA = it } },
                )
                EditTextPreference(
                    title = stringResource(R.string.gpio_pin_for_rotary_encoder_b_port),
                    value = formState.value.inputbrokerPinB,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { inputbrokerPinB = it } },
                )
                EditTextPreference(
                    title = stringResource(R.string.gpio_pin_for_rotary_encoder_press_port),
                    value = formState.value.inputbrokerPinPress,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { inputbrokerPinPress = it } },
                )
                DropDownPreference(
                    title = stringResource(R.string.generate_input_event_on_press),
                    enabled = state.connected,
                    items =
                    CannedMessageConfig.InputEventChar.entries
                        .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.inputbrokerEventPress,
                    onItemSelected = { formState.value = formState.value.copy { inputbrokerEventPress = it } },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(R.string.generate_input_event_on_cw),
                    enabled = state.connected,
                    items =
                    CannedMessageConfig.InputEventChar.entries
                        .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.inputbrokerEventCw,
                    onItemSelected = { formState.value = formState.value.copy { inputbrokerEventCw = it } },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(R.string.generate_input_event_on_ccw),
                    enabled = state.connected,
                    items =
                    CannedMessageConfig.InputEventChar.entries
                        .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.inputbrokerEventCcw,
                    onItemSelected = { formState.value = formState.value.copy { inputbrokerEventCcw = it } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.up_down_select_input_enabled),
                    checked = formState.value.updown1Enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { updown1Enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.allow_input_source),
                    value = formState.value.allowInputSource,
                    maxSize = 63, // allow_input_source max_size:16
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { allowInputSource = it } },
                )
                SwitchPreference(
                    title = stringResource(R.string.send_bell),
                    checked = formState.value.sendBell,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { sendBell = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.messages),
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
