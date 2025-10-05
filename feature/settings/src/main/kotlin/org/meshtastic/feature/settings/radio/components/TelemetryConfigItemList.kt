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

package org.meshtastic.feature.settings.radio.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
fun TelemetryConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val telemetryConfig = state.moduleConfig.telemetry
    val formState = rememberConfigState(initialValue = telemetryConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.telemetry),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { telemetry = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.telemetry_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.device_telemetry_enabled),
                summary = stringResource(R.string.device_telemetry_enabled_summary),
                checked = formState.value.deviceTelemetryEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { deviceTelemetryEnabled = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.device_metrics_update_interval_seconds),
                value = formState.value.deviceUpdateInterval,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { deviceUpdateInterval = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.environment_metrics_update_interval_seconds),
                value = formState.value.environmentUpdateInterval,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { environmentUpdateInterval = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.environment_metrics_module_enabled),
                checked = formState.value.environmentMeasurementEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { environmentMeasurementEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.environment_metrics_on_screen_enabled),
                checked = formState.value.environmentScreenEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { environmentScreenEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.environment_metrics_use_fahrenheit),
                checked = formState.value.environmentDisplayFahrenheit,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { environmentDisplayFahrenheit = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.air_quality_metrics_module_enabled),
                checked = formState.value.airQualityEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { airQualityEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.air_quality_metrics_update_interval_seconds),
                value = formState.value.airQualityInterval,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { airQualityInterval = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.power_metrics_module_enabled),
                checked = formState.value.powerMeasurementEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { powerMeasurementEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.power_metrics_update_interval_seconds),
                value = formState.value.powerUpdateInterval,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { powerUpdateInterval = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.power_metrics_on_screen_enabled),
                checked = formState.value.powerScreenEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { powerScreenEnabled = it } },
            )
        }
        item { HorizontalDivider() }
    }
}
