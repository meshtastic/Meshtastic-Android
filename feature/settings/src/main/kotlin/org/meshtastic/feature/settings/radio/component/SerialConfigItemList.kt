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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfigProtos.ModuleConfig.SerialConfig
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun SerialConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val serialConfig = state.moduleConfig.serial
    val formState = rememberConfigState(initialValue = serialConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.serial),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { serial = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(R.string.serial_config)) {
                SwitchPreference(
                    title = stringResource(R.string.serial_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.echo_enabled),
                    checked = formState.value.echo,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { echo = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = "RX",
                    value = formState.value.rxd,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { rxd = it } },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = "TX",
                    value = formState.value.txd,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { txd = it } },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(R.string.serial_baud_rate),
                    enabled = state.connected,
                    items =
                    SerialConfig.Serial_Baud.entries
                        .filter { it != SerialConfig.Serial_Baud.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.baud,
                    onItemSelected = { formState.value = formState.value.copy { baud = it } },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.timeout),
                    value = formState.value.timeout,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { timeout = it } },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(R.string.serial_mode),
                    enabled = state.connected,
                    items =
                    SerialConfig.Serial_Mode.entries
                        .filter { it != SerialConfig.Serial_Mode.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.mode,
                    onItemSelected = { formState.value = formState.value.copy { mode = it } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.override_console_serial_port),
                    checked = formState.value.overrideConsoleSerialPort,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { overrideConsoleSerialPort = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
