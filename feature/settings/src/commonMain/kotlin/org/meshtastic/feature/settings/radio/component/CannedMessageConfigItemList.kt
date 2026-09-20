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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.allow_input_source
import org.meshtastic.core.resources.canned_message
import org.meshtastic.core.resources.canned_message_config
import org.meshtastic.core.resources.canned_message_enabled
import org.meshtastic.core.resources.messages
import org.meshtastic.core.resources.schema_cannedmessage_inputbroker_event_ccw
import org.meshtastic.core.resources.schema_cannedmessage_inputbroker_event_cw
import org.meshtastic.core.resources.schema_cannedmessage_inputbroker_event_press
import org.meshtastic.core.resources.schema_cannedmessage_inputbroker_pin_a
import org.meshtastic.core.resources.schema_cannedmessage_inputbroker_pin_b
import org.meshtastic.core.resources.schema_cannedmessage_inputbroker_pin_press
import org.meshtastic.core.resources.schema_cannedmessage_rotary1_enabled
import org.meshtastic.core.resources.schema_cannedmessage_send_bell
import org.meshtastic.core.resources.schema_cannedmessage_updown1_enabled
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.allow_input_source

@Suppress("DEPRECATION", "LongMethod")
@Composable
fun CannedMessageConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val firmwareVersion = state.metadata?.firmware_version
    val capabilities = remember(firmwareVersion) { Capabilities(firmwareVersion) }
    val cannedMessageConfig = state.moduleConfig.canned_message ?: ModuleConfig.CannedMessageConfig.Builder().build()
    val messages = state.cannedMessageMessages
    val formState = rememberConfigState(initialValue = cannedMessageConfig)
    var messagesInput by rememberSaveable(messages) { mutableStateOf(messages) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        rebootBehavior = RebootBehavior.ALWAYS,
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
                val config = ModuleConfig.Builder().also { wb -> wb.canned_message = formState.value }.build()
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
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.enabled = it }.build()
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.schema_cannedmessage_rotary1_enabled),
                    checked = formState.value.rotary1_enabled,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.rotary1_enabled = it }.build()
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.schema_cannedmessage_inputbroker_pin_a),
                    value = formState.value.inputbroker_pin_a,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.inputbroker_pin_a = it }.build()
                    },
                )
                EditTextPreference(
                    title = stringResource(Res.string.schema_cannedmessage_inputbroker_pin_b),
                    value = formState.value.inputbroker_pin_b,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.inputbroker_pin_b = it }.build()
                    },
                )
                EditTextPreference(
                    title = stringResource(Res.string.schema_cannedmessage_inputbroker_pin_press),
                    value = formState.value.inputbroker_pin_press,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        formState.value =
                            formState.value.newBuilder().also { wb -> wb.inputbroker_pin_press = it }.build()
                    },
                )
                DropDownPreference(
                    title = stringResource(Res.string.schema_cannedmessage_inputbroker_event_press),
                    enabled = state.connected,
                    items = ModuleConfig.CannedMessageConfig.InputEventChar.entries.map { it to it.name },
                    selectedItem = formState.value.inputbroker_event_press,
                    onItemSelected = {
                        formState.value =
                            formState.value.newBuilder().also { wb -> wb.inputbroker_event_press = it }.build()
                    },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.schema_cannedmessage_inputbroker_event_cw),
                    enabled = state.connected,
                    items = ModuleConfig.CannedMessageConfig.InputEventChar.entries.map { it to it.name },
                    selectedItem = formState.value.inputbroker_event_cw,
                    onItemSelected = {
                        formState.value =
                            formState.value.newBuilder().also { wb -> wb.inputbroker_event_cw = it }.build()
                    },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.schema_cannedmessage_inputbroker_event_ccw),
                    enabled = state.connected,
                    items = ModuleConfig.CannedMessageConfig.InputEventChar.entries.map { it to it.name },
                    selectedItem = formState.value.inputbroker_event_ccw,
                    onItemSelected = {
                        formState.value =
                            formState.value.newBuilder().also { wb -> wb.inputbroker_event_ccw = it }.build()
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.schema_cannedmessage_updown1_enabled),
                    checked = formState.value.updown1_enabled,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.updown1_enabled = it }.build()
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                if (
                    capabilities.offers(
                        ModuleConfig.CannedMessageConfig.allow_input_source,
                        isSet = formState.value.allow_input_source.isNotEmpty(),
                    )
                ) {
                    EditTextPreference(
                        title = stringResource(Res.string.allow_input_source),
                        value = formState.value.allow_input_source,
                        maxSize = 63, // allow_input_source max_size:16
                        enabled = state.connected,
                        isError = false,
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = {
                            formState.value =
                                formState.value.newBuilder().also { wb -> wb.allow_input_source = it }.build()
                        },
                    )
                }
                SwitchPreference(
                    title = stringResource(Res.string.schema_cannedmessage_send_bell),
                    checked = formState.value.send_bell,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.send_bell = it }.build()
                    },
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
                    multiline = true,
                )
            }
        }
    }
}
