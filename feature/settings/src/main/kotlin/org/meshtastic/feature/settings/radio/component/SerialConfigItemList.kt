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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.echo_enabled
import org.meshtastic.core.strings.override_console_serial_port
import org.meshtastic.core.strings.serial
import org.meshtastic.core.strings.serial_baud_rate
import org.meshtastic.core.strings.serial_config
import org.meshtastic.core.strings.serial_enabled
import org.meshtastic.core.strings.serial_mode
import org.meshtastic.core.strings.timeout
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Composable
fun SerialConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val serialConfig = state.moduleConfig.serial ?: ModuleConfig.SerialConfig()
    val formState = rememberConfigState(initialValue = serialConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.serial),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(serial = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.serial_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.serial_enabled),
                    checked = formState.value.enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.echo_enabled),
                    checked = formState.value.echo ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(echo = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = "RX",
                    value = formState.value.rxd ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(rxd = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = "TX",
                    value = formState.value.txd ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(txd = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.serial_baud_rate),
                    enabled = state.connected,
                    items = ModuleConfig.SerialConfig.Serial_Baud.entries.map { it to it.name },
                    selectedItem = formState.value.baud ?: ModuleConfig.SerialConfig.Serial_Baud.BAUD_DEFAULT,
                    onItemSelected = { formState.value = formState.value.copy(baud = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.timeout),
                    value = formState.value.timeout ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(timeout = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.serial_mode),
                    enabled = state.connected,
                    items = ModuleConfig.SerialConfig.Serial_Mode.entries.map { it to it.name },
                    selectedItem = formState.value.mode ?: ModuleConfig.SerialConfig.Serial_Mode.DEFAULT,
                    onItemSelected = { formState.value = formState.value.copy(mode = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.override_console_serial_port),
                    checked = formState.value.override_console_serial_port ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(override_console_serial_port = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
