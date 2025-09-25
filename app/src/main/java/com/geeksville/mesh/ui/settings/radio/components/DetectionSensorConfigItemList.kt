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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R

@Composable
fun DetectionSensorConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val detectionSensorConfig = state.moduleConfig.detectionSensor
    val formState = rememberConfigState(initialValue = detectionSensorConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.detection_sensor),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { detectionSensor = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.detection_sensor_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.detection_sensor_enabled),
                checked = formState.value.enabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.minimum_broadcast_seconds),
                value = formState.value.minimumBroadcastSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { minimumBroadcastSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.state_broadcast_seconds),
                value = formState.value.stateBroadcastSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { stateBroadcastSecs = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.send_bell_with_alert_message),
                checked = formState.value.sendBell,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { sendBell = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.friendly_name),
                value = formState.value.name,
                maxSize = 19, // name max_size:20
                enabled = state.connected,
                isError = false,
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { name = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.gpio_pin_to_monitor),
                value = formState.value.monitorPin,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { monitorPin = it } },
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.detection_trigger_type),
                enabled = state.connected,
                items =
                ModuleConfig.DetectionSensorConfig.TriggerType.entries
                    .filter { it != ModuleConfig.DetectionSensorConfig.TriggerType.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.detectionTriggerType,
                onItemSelected = { formState.value = formState.value.copy { detectionTriggerType = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.use_input_pullup_mode),
                checked = formState.value.usePullup,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { usePullup = it } },
            )
        }
        item { HorizontalDivider() }
    }
}
