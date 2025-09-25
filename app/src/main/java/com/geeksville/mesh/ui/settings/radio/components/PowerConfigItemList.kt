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
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R

@Composable
fun PowerConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val powerConfig = state.radioConfig.power
    val formState = rememberConfigState(initialValue = powerConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.power),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { power = it }
            viewModel.setConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.power_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.enable_power_saving_mode),
                summary = stringResource(id = R.string.config_power_is_power_saving_summary),
                checked = formState.value.isPowerSaving,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { isPowerSaving = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.shutdown_on_power_loss),
                checked = formState.value.onBatteryShutdownAfterSecs > 0,
                enabled = state.connected,
                onCheckedChange = {
                    formState.value = formState.value.copy { onBatteryShutdownAfterSecs = if (it) 3600 else 0 }
                },
            )
        }

        if (formState.value.onBatteryShutdownAfterSecs > 0) {
            item {
                EditTextPreference(
                    title = stringResource(R.string.shutdown_on_battery_delay_seconds),
                    value = formState.value.onBatteryShutdownAfterSecs,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { onBatteryShutdownAfterSecs = it } },
                )
            }
        }

        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.adc_multiplier_override),
                checked = formState.value.adcMultiplierOverride > 0f,
                enabled = state.connected,
                onCheckedChange = {
                    formState.value = formState.value.copy { adcMultiplierOverride = if (it) 1.0f else 0.0f }
                },
            )
        }

        if (formState.value.adcMultiplierOverride > 0f) {
            item {
                EditTextPreference(
                    title = stringResource(R.string.adc_multiplier_override_ratio),
                    value = formState.value.adcMultiplierOverride,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { adcMultiplierOverride = it } },
                )
            }
        }

        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.wait_for_bluetooth_duration_seconds),
                value = formState.value.waitBluetoothSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { waitBluetoothSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.super_deep_sleep_duration_seconds),
                value = formState.value.sdsSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { sdsSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.minimum_wake_time_seconds),
                value = formState.value.minWakeSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { minWakeSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.battery_ina_2xx_i2c_address),
                value = formState.value.deviceBatteryInaAddress,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { deviceBatteryInaAddress = it } },
            )
        }
    }
}
