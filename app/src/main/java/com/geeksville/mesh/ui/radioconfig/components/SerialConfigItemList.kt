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

package com.geeksville.mesh.ui.radioconfig.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.SerialConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel

@Composable
fun SerialConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    SerialConfigItemList(
        serialConfig = state.moduleConfig.serial,
        enabled = state.connected,
        onSaveClicked = { serialInput ->
            val config = moduleConfig { serial = serialInput }
            viewModel.setModuleConfig(config)
        }
    )
}

@Composable
fun SerialConfigItemList(
    serialConfig: SerialConfig,
    enabled: Boolean,
    onSaveClicked: (SerialConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var serialInput by rememberSaveable { mutableStateOf(serialConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = stringResource(R.string.serial_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.serial_enabled),
                checked = serialInput.enabled,
                enabled = enabled,
                onCheckedChange = { serialInput = serialInput.copy { this.enabled = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.echo_enabled),
                checked = serialInput.echo,
                enabled = enabled,
                onCheckedChange = { serialInput = serialInput.copy { echo = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = "RX",
                value = serialInput.rxd,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { rxd = it } }
            )
        }

        item {
            EditTextPreference(
                title = "TX",
                value = serialInput.txd,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { txd = it } }
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.serial_baud_rate),
                enabled = enabled,
                items = SerialConfig.Serial_Baud.entries
                    .filter { it != SerialConfig.Serial_Baud.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = serialInput.baud,
                onItemSelected = { serialInput = serialInput.copy { baud = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.timeout),
                value = serialInput.timeout,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { timeout = it } }
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.serial_mode),
                enabled = enabled,
                items = SerialConfig.Serial_Mode.entries
                    .filter { it != SerialConfig.Serial_Mode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = serialInput.mode,
                onItemSelected = { serialInput = serialInput.copy { mode = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.override_console_serial_port),
                checked = serialInput.overrideConsoleSerialPort,
                enabled = enabled,
                onCheckedChange = {
                    serialInput = serialInput.copy { overrideConsoleSerialPort = it }
                }
            )
        }
        item { HorizontalDivider() }

        item {
            PreferenceFooter(
                enabled = enabled && serialInput != serialConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    serialInput = serialConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(serialInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SerialConfigPreview() {
    SerialConfigItemList(
        serialConfig = SerialConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
