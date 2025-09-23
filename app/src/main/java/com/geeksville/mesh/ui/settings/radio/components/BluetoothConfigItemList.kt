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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.ConfigProtos.Config.BluetoothConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R

@Composable
fun BluetoothConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val bluetoothConfig = state.radioConfig.bluetooth
    val formState = rememberConfigState(initialValue = bluetoothConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.bluetooth),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { bluetooth = it }
            viewModel.setConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.bluetooth_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.bluetooth_enabled),
                checked = formState.value.enabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            DropDownPreference(
                title = stringResource(R.string.pairing_mode),
                enabled = state.connected,
                items =
                BluetoothConfig.PairingMode.entries
                    .filter { it != BluetoothConfig.PairingMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.mode,
                onItemSelected = { formState.value = formState.value.copy { mode = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.fixed_pin),
                value = formState.value.fixedPin,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    if (it.toString().length == 6) { // ensure 6 digits
                        formState.value = formState.value.copy { fixedPin = it }
                    }
                },
            )
        }
    }
}
