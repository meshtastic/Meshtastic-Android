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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.bluetooth_config
import org.meshtastic.core.resources.bluetooth_enabled
import org.meshtastic.core.resources.fixed_pin
import org.meshtastic.core.resources.pairing_mode
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.proto.Config

private const val PIN_LENGTH = 6

@Composable
fun BluetoothConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val bluetoothConfig = state.radioConfig.bluetooth ?: Config.BluetoothConfig()
    val formState = rememberConfigState(initialValue = bluetoothConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        rebootBehavior = RebootBehavior.ALWAYS,
        title = stringResource(Res.string.bluetooth),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = Config(bluetooth = it)
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.bluetooth_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.bluetooth_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.pairing_mode),
                    enabled = state.connected,
                    items =
                    Config.BluetoothConfig.PairingMode.entries
                        .filter { it.name != "UNRECOGNIZED" }
                        .map { it to it.name },
                    selectedItem =
                    formState.value.mode.takeUnless { it.name == "UNRECOGNIZED" }
                        ?: Config.BluetoothConfig.PairingMode.RANDOM_PIN,
                    onItemSelected = { formState.value = formState.value.copy(mode = it) },
                )
                HorizontalDivider()
                FixedPinPreference(
                    pinValue = formState.value.fixed_pin,
                    enabled = state.connected,
                    focusManager = focusManager,
                    onPinChange = { formState.value = formState.value.copy(fixed_pin = it) },
                )
            }
        }
    }
}

@Composable
private fun FixedPinPreference(
    pinValue: Int,
    enabled: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onPinChange: (Int) -> Unit,
) {
    var pinState by remember(pinValue) { mutableStateOf(pinValue.toString().padStart(PIN_LENGTH, '0')) }
    val pinIsError = pinState.length != PIN_LENGTH || !pinState.all { it.isDigit() }
    EditTextPreference(
        title = stringResource(Res.string.fixed_pin),
        value = pinState,
        enabled = enabled,
        isError = pinIsError,
        keyboardOptions =
        KeyboardOptions.Default.copy(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        onValueChanged = { input ->
            if (input.length <= PIN_LENGTH && input.all { it.isDigit() }) {
                pinState = input
                if (input.length == PIN_LENGTH) {
                    onPinChange(input.toInt())
                }
            }
        },
    )
}
