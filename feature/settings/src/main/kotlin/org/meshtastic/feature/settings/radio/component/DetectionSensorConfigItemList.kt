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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.detection_sensor
import org.meshtastic.core.strings.detection_sensor_config
import org.meshtastic.core.strings.detection_sensor_enabled
import org.meshtastic.core.strings.detection_trigger_type
import org.meshtastic.core.strings.friendly_name
import org.meshtastic.core.strings.gpio_pin_to_monitor
import org.meshtastic.core.strings.minimum_broadcast_seconds
import org.meshtastic.core.strings.send_bell_with_alert_message
import org.meshtastic.core.strings.state_broadcast_seconds
import org.meshtastic.core.strings.use_input_pullup_mode
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.gpioPins
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.ModuleConfig

@Composable
fun DetectionSensorConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val detectionSensorConfig = state.moduleConfig.detection_sensor ?: ModuleConfig.DetectionSensorConfig()
    val formState = rememberConfigState(initialValue = detectionSensorConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.detection_sensor),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(detection_sensor = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.detection_sensor_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.detection_sensor_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val minimumBroadcastIntervals = remember {
                    IntervalConfiguration.DETECTION_SENSOR_MINIMUM.allowedIntervals
                }
                DropDownPreference(
                    title = stringResource(Res.string.minimum_broadcast_seconds),
                    selectedItem = (formState.value.minimum_broadcast_secs ?: 0).toLong(),
                    enabled = state.connected,
                    items = minimumBroadcastIntervals.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(minimum_broadcast_secs = it.toInt()) },
                )

                val stateBroadcastIntervals = remember { IntervalConfiguration.DETECTION_SENSOR_STATE.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.state_broadcast_seconds),
                    selectedItem = (formState.value.state_broadcast_secs ?: 0).toLong(),
                    enabled = state.connected,
                    items = stateBroadcastIntervals.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(state_broadcast_secs = it.toInt()) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.send_bell_with_alert_message),
                    checked = formState.value.send_bell,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(send_bell = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.friendly_name),
                    value = formState.value.name ?: "",
                    maxSize = 19, // name max_size:20
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(name = it) },
                )
                HorizontalDivider()
                val pins = remember { gpioPins }
                DropDownPreference(
                    title = stringResource(Res.string.gpio_pin_to_monitor),
                    items = pins,
                    selectedItem = formState.value.monitor_pin ?: 0,
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(monitor_pin = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.detection_trigger_type),
                    enabled = state.connected,
                    items = ModuleConfig.DetectionSensorConfig.TriggerType.entries.map { it to it.name },
                    selectedItem =
                    formState.value.detection_trigger_type
                        ?: ModuleConfig.DetectionSensorConfig.TriggerType.LOGIC_LOW,
                    onItemSelected = { formState.value = formState.value.copy(detection_trigger_type = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.use_input_pullup_mode),
                    checked = formState.value.use_pullup ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(use_pullup = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
            }
        }
    }
}
