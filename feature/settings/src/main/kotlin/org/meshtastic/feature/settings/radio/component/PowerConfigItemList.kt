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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.adc_multiplier_override
import org.meshtastic.core.strings.adc_multiplier_override_ratio
import org.meshtastic.core.strings.battery_ina_2xx_i2c_address
import org.meshtastic.core.strings.config_power_is_power_saving_summary
import org.meshtastic.core.strings.enable_power_saving_mode
import org.meshtastic.core.strings.minimum_wake_time_seconds
import org.meshtastic.core.strings.power
import org.meshtastic.core.strings.power_config
import org.meshtastic.core.strings.shutdown_on_power_loss
import org.meshtastic.core.strings.super_deep_sleep_duration_seconds
import org.meshtastic.core.strings.wait_for_bluetooth_duration_seconds
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.config
import org.meshtastic.proto.copy

@Composable
fun PowerConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val powerConfig = state.radioConfig.power
    val formState = rememberConfigState(initialValue = powerConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.power),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { power = it }
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.power_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.enable_power_saving_mode),
                    summary = stringResource(Res.string.config_power_is_power_saving_summary),
                    checked = formState.value.isPowerSaving,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { isPowerSaving = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val items = remember { IntervalConfiguration.ALL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.shutdown_on_power_loss),
                    selectedItem = formState.value.onBatteryShutdownAfterSecs.toLong(),
                    enabled = state.connected,
                    items = items.map { it.value to it.toDisplayString() },
                    onItemSelected = {
                        formState.value = formState.value.copy { onBatteryShutdownAfterSecs = it.toInt() }
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.adc_multiplier_override),
                    checked = formState.value.adcMultiplierOverride > 0f,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.copy { adcMultiplierOverride = if (it) 1.0f else 0.0f }
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (formState.value.adcMultiplierOverride > 0f) {
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.adc_multiplier_override_ratio),
                        value = formState.value.adcMultiplierOverride,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy { adcMultiplierOverride = it } },
                    )
                }
                HorizontalDivider()
                val waitBluetoothItems = remember { IntervalConfiguration.NAG_TIMEOUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.wait_for_bluetooth_duration_seconds),
                    selectedItem = formState.value.waitBluetoothSecs.toLong(),
                    enabled = state.connected,
                    items = waitBluetoothItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy { waitBluetoothSecs = it.toInt() } },
                )
                HorizontalDivider()
                val sdsSecsItems = remember { IntervalConfiguration.ALL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.super_deep_sleep_duration_seconds),
                    selectedItem = formState.value.sdsSecs.toLong(),
                    onItemSelected = { formState.value = formState.value.copy { sdsSecs = it.toInt() } },
                    enabled = state.connected,
                    items = sdsSecsItems.map { it.value to it.toDisplayString() },
                )
                HorizontalDivider()
                val minWakeItems = remember { IntervalConfiguration.NAG_TIMEOUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.minimum_wake_time_seconds),
                    selectedItem = formState.value.minWakeSecs.toLong(),
                    enabled = state.connected,
                    items = minWakeItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy { minWakeSecs = it.toInt() } },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.battery_ina_2xx_i2c_address),
                    value = formState.value.deviceBatteryInaAddress,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { deviceBatteryInaAddress = it } },
                )
            }
        }
    }
}
