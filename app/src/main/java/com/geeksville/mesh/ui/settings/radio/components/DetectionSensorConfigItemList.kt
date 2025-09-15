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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel

@Composable
fun DetectionSensorConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    DetectionSensorConfigItemList(
        detectionSensorConfig = state.moduleConfig.detectionSensor,
        enabled = state.connected,
        onSaveClicked = { detectionSensorInput ->
            val config = moduleConfig { detectionSensor = detectionSensorInput }
            viewModel.setModuleConfig(config)
        },
    )
}

@Suppress("LongMethod")
@Composable
fun DetectionSensorConfigItemList(
    detectionSensorConfig: ModuleConfig.DetectionSensorConfig,
    enabled: Boolean,
    onSaveClicked: (ModuleConfig.DetectionSensorConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var detectionSensorInput by rememberSaveable { mutableStateOf(detectionSensorConfig) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceCategory(text = stringResource(R.string.detection_sensor_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.detection_sensor_enabled),
                checked = detectionSensorInput.enabled,
                enabled = enabled,
                onCheckedChange = { detectionSensorInput = detectionSensorInput.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.minimum_broadcast_seconds),
                value = detectionSensorInput.minimumBroadcastSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { detectionSensorInput = detectionSensorInput.copy { minimumBroadcastSecs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.state_broadcast_seconds),
                value = detectionSensorInput.stateBroadcastSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { detectionSensorInput = detectionSensorInput.copy { stateBroadcastSecs = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.send_bell_with_alert_message),
                checked = detectionSensorInput.sendBell,
                enabled = enabled,
                onCheckedChange = { detectionSensorInput = detectionSensorInput.copy { sendBell = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.friendly_name),
                value = detectionSensorInput.name,
                maxSize = 19, // name max_size:20
                enabled = enabled,
                isError = false,
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { detectionSensorInput = detectionSensorInput.copy { name = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.gpio_pin_to_monitor),
                value = detectionSensorInput.monitorPin,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { detectionSensorInput = detectionSensorInput.copy { monitorPin = it } },
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.detection_trigger_type),
                enabled = enabled,
                items =
                ModuleConfig.DetectionSensorConfig.TriggerType.entries
                    .filter { it != ModuleConfig.DetectionSensorConfig.TriggerType.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = detectionSensorInput.detectionTriggerType,
                onItemSelected = { detectionSensorInput = detectionSensorInput.copy { detectionTriggerType = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.use_input_pullup_mode),
                checked = detectionSensorInput.usePullup,
                enabled = enabled,
                onCheckedChange = { detectionSensorInput = detectionSensorInput.copy { usePullup = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            PreferenceFooter(
                enabled = enabled && detectionSensorInput != detectionSensorConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    detectionSensorInput = detectionSensorConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(detectionSensorInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DetectionSensorConfigPreview() {
    DetectionSensorConfigItemList(
        detectionSensorConfig = ModuleConfig.DetectionSensorConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = {},
    )
}
