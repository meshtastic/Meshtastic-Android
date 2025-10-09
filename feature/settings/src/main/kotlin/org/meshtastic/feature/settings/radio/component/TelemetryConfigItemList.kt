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

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

private const val MIN_FW_FOR_TELEMETRY_TOGGLE = "2.7.12"

@Composable
fun TelemetryConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val telemetryConfig = state.moduleConfig.telemetry
    val formState = rememberConfigState(initialValue = telemetryConfig)

    val firmwareVersion = state.metadata?.firmwareVersion ?: "1"

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
        item {
            TitledCard(title = stringResource(R.string.telemetry_config)) {
                if (DeviceVersion(firmwareVersion) >= DeviceVersion(MIN_FW_FOR_TELEMETRY_TOGGLE)) {
                    SwitchPreference(
                        title = stringResource(R.string.device_telemetry_enabled),
                        summary = stringResource(R.string.device_telemetry_enabled_summary),
                        checked = formState.value.deviceTelemetryEnabled,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy { deviceTelemetryEnabled = it } },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                    HorizontalDivider()
                }
                val items = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(R.string.device_metrics_update_interval_seconds),
                    selectedItem = formState.value.deviceUpdateInterval.toLong(),
                    enabled = state.connected,
                    items = items.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy { deviceUpdateInterval = it.toInt() } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.environment_metrics_module_enabled),
                    checked = formState.value.environmentMeasurementEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { environmentMeasurementEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val envItems = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(R.string.environment_metrics_update_interval_seconds),
                    selectedItem = formState.value.environmentUpdateInterval.toLong(),
                    enabled = state.connected,
                    items = envItems.map { it.value to it.toDisplayString() },
                    onItemSelected = {
                        formState.value = formState.value.copy { environmentUpdateInterval = it.toInt() }
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.environment_metrics_on_screen_enabled),
                    checked = formState.value.environmentScreenEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { environmentScreenEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.environment_metrics_use_fahrenheit),
                    checked = formState.value.environmentDisplayFahrenheit,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { environmentDisplayFahrenheit = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.air_quality_metrics_module_enabled),
                    checked = formState.value.airQualityEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { airQualityEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val airItems = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(R.string.air_quality_metrics_update_interval_seconds),
                    selectedItem = formState.value.airQualityInterval.toLong(),
                    enabled = state.connected,
                    items = airItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy { airQualityInterval = it.toInt() } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.power_metrics_module_enabled),
                    checked = formState.value.powerMeasurementEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { powerMeasurementEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val powerItems = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(R.string.power_metrics_update_interval_seconds),
                    selectedItem = formState.value.powerUpdateInterval.toLong(),
                    enabled = state.connected,
                    items = powerItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy { powerUpdateInterval = it.toInt() } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.power_metrics_on_screen_enabled),
                    checked = formState.value.powerScreenEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { powerScreenEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
