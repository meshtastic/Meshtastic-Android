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
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.TelemetryConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R

@Composable
fun TelemetryConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    TelemetryConfigItemList(
        telemetryConfig = state.moduleConfig.telemetry,
        enabled = state.connected,
        onSaveClicked = { telemetryInput ->
            val config = moduleConfig { telemetry = telemetryInput }
            viewModel.setModuleConfig(config)
        },
    )
}

@Composable
fun TelemetryConfigItemList(
    telemetryConfig: TelemetryConfig,
    enabled: Boolean,
    onSaveClicked: (TelemetryConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var telemetryInput by rememberSaveable { mutableStateOf(telemetryConfig) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceCategory(text = stringResource(R.string.telemetry_config)) }

        item {
            EditTextPreference(
                title = stringResource(R.string.device_metrics_update_interval_seconds),
                value = telemetryInput.deviceUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { telemetryInput = telemetryInput.copy { deviceUpdateInterval = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.environment_metrics_update_interval_seconds),
                value = telemetryInput.environmentUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { telemetryInput = telemetryInput.copy { environmentUpdateInterval = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.environment_metrics_module_enabled),
                checked = telemetryInput.environmentMeasurementEnabled,
                enabled = enabled,
                onCheckedChange = { telemetryInput = telemetryInput.copy { environmentMeasurementEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.environment_metrics_on_screen_enabled),
                checked = telemetryInput.environmentScreenEnabled,
                enabled = enabled,
                onCheckedChange = { telemetryInput = telemetryInput.copy { environmentScreenEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.environment_metrics_use_fahrenheit),
                checked = telemetryInput.environmentDisplayFahrenheit,
                enabled = enabled,
                onCheckedChange = { telemetryInput = telemetryInput.copy { environmentDisplayFahrenheit = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.air_quality_metrics_module_enabled),
                checked = telemetryInput.airQualityEnabled,
                enabled = enabled,
                onCheckedChange = { telemetryInput = telemetryInput.copy { airQualityEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.air_quality_metrics_update_interval_seconds),
                value = telemetryInput.airQualityInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { telemetryInput = telemetryInput.copy { airQualityInterval = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.power_metrics_module_enabled),
                checked = telemetryInput.powerMeasurementEnabled,
                enabled = enabled,
                onCheckedChange = { telemetryInput = telemetryInput.copy { powerMeasurementEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.power_metrics_update_interval_seconds),
                value = telemetryInput.powerUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { telemetryInput = telemetryInput.copy { powerUpdateInterval = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.power_metrics_on_screen_enabled),
                checked = telemetryInput.powerScreenEnabled,
                enabled = enabled,
                onCheckedChange = { telemetryInput = telemetryInput.copy { powerScreenEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            PreferenceFooter(
                enabled = enabled && telemetryInput != telemetryConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    telemetryInput = telemetryConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(telemetryInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TelemetryConfigPreview() {
    TelemetryConfigItemList(telemetryConfig = TelemetryConfig.getDefaultInstance(), enabled = true, onSaveClicked = {})
}
