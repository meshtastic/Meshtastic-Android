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
import org.meshtastic.proto.Config

@Composable
fun PowerConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val powerConfig = state.radioConfig.power ?: Config.PowerConfig()
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
            val config = Config(power = it)
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.power_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.enable_power_saving_mode),
                    summary = stringResource(Res.string.config_power_is_power_saving_summary),
                    checked = formState.value.is_power_saving ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(is_power_saving = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val items = remember { IntervalConfiguration.ALL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.shutdown_on_power_loss),
                    selectedItem = (formState.value.on_battery_shutdown_after_secs ?: 0).toLong(),
                    enabled = state.connected,
                    items = items.map { it.value to it.toDisplayString() },
                    onItemSelected = {
                        formState.value = formState.value.copy(on_battery_shutdown_after_secs = it.toInt())
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.adc_multiplier_override),
                    checked = (formState.value.adc_multiplier_override ?: 0f) > 0f,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.copy(adc_multiplier_override = if (it) 1.0f else 0.0f)
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if ((formState.value.adc_multiplier_override ?: 0f) > 0f) {
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.adc_multiplier_override_ratio),
                        value = formState.value.adc_multiplier_override ?: 0f,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy(adc_multiplier_override = it) },
                    )
                }
                HorizontalDivider()
                val waitBluetoothItems = remember { IntervalConfiguration.NAG_TIMEOUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.wait_for_bluetooth_duration_seconds),
                    selectedItem = (formState.value.wait_bluetooth_secs ?: 0).toLong(),
                    enabled = state.connected,
                    items = waitBluetoothItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(wait_bluetooth_secs = it.toInt()) },
                )
                HorizontalDivider()
                val sdsSecsItems = remember { IntervalConfiguration.ALL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.super_deep_sleep_duration_seconds),
                    selectedItem = (formState.value.sds_secs ?: 0).toLong(),
                    onItemSelected = { formState.value = formState.value.copy(sds_secs = it.toInt()) },
                    enabled = state.connected,
                    items = sdsSecsItems.map { it.value to it.toDisplayString() },
                )
                HorizontalDivider()
                val minWakeItems = remember { IntervalConfiguration.NAG_TIMEOUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.minimum_wake_time_seconds),
                    selectedItem = (formState.value.min_wake_secs ?: 0).toLong(),
                    enabled = state.connected,
                    items = minWakeItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(min_wake_secs = it.toInt()) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.battery_ina_2xx_i2c_address),
                    value = formState.value.device_battery_ina_address ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(device_battery_ina_address = it) },
                )
            }
        }
    }
}
