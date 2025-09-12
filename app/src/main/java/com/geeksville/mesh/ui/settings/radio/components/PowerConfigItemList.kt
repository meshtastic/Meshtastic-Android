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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.PowerConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel

@Composable
fun PowerConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    PowerConfigItemList(
        powerConfig = state.radioConfig.power,
        enabled = state.connected,
        onSaveClicked = { powerInput ->
            val config = config { power = powerInput }
            viewModel.setConfig(config)
        },
    )
}

@Suppress("LongMethod")
@Composable
fun PowerConfigItemList(powerConfig: PowerConfig, enabled: Boolean, onSaveClicked: (PowerConfig) -> Unit) {
    val focusManager = LocalFocusManager.current
    var powerInput by rememberSaveable { mutableStateOf(powerConfig) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceCategory(text = stringResource(R.string.power_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.enable_power_saving_mode),
                checked = powerInput.isPowerSaving,
                enabled = enabled,
                onCheckedChange = { powerInput = powerInput.copy { isPowerSaving = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.shutdown_on_battery_delay_seconds),
                value = powerInput.onBatteryShutdownAfterSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { onBatteryShutdownAfterSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.adc_multiplier_override_ratio),
                value = powerInput.adcMultiplierOverride,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { adcMultiplierOverride = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.wait_for_bluetooth_duration_seconds),
                value = powerInput.waitBluetoothSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { waitBluetoothSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.super_deep_sleep_duration_seconds),
                value = powerInput.sdsSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { sdsSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.minimum_wake_time_seconds),
                value = powerInput.minWakeSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { minWakeSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.battery_ina_2xx_i2c_address),
                value = powerInput.deviceBatteryInaAddress,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { deviceBatteryInaAddress = it } },
            )
        }

        item {
            PreferenceFooter(
                enabled = enabled && powerInput != powerConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    powerInput = powerConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(powerInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PowerConfigPreview() {
    PowerConfigItemList(powerConfig = PowerConfig.getDefaultInstance(), enabled = true, onSaveClicked = {})
}
