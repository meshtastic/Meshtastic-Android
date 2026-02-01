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

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.air_quality_metrics_module_enabled
import org.meshtastic.core.strings.air_quality_metrics_update_interval_seconds
import org.meshtastic.core.strings.device_metrics_update_interval_seconds
import org.meshtastic.core.strings.device_telemetry_enabled
import org.meshtastic.core.strings.device_telemetry_enabled_summary
import org.meshtastic.core.strings.environment_metrics_module_enabled
import org.meshtastic.core.strings.environment_metrics_on_screen_enabled
import org.meshtastic.core.strings.environment_metrics_update_interval_seconds
import org.meshtastic.core.strings.environment_metrics_use_fahrenheit
import org.meshtastic.core.strings.power_metrics_module_enabled
import org.meshtastic.core.strings.power_metrics_on_screen_enabled
import org.meshtastic.core.strings.power_metrics_update_interval_seconds
import org.meshtastic.core.strings.telemetry
import org.meshtastic.core.strings.telemetry_config
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.ModuleConfig

@Composable
fun TelemetryConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val telemetryConfig = state.moduleConfig.telemetry ?: ModuleConfig.TelemetryConfig()
    val formState = rememberConfigState(initialValue = telemetryConfig)

    val firmwareVersion = state.metadata?.firmware_version
    val capabilities = remember(firmwareVersion) { Capabilities(firmwareVersion) }

    RadioConfigScreenList(
        title = stringResource(Res.string.telemetry),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(telemetry = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.telemetry_config)) {
                if (capabilities.canToggleTelemetryEnabled) {
                    SwitchPreference(
                        title = stringResource(Res.string.device_telemetry_enabled),
                        summary = stringResource(Res.string.device_telemetry_enabled_summary),
                        checked = formState.value.device_telemetry_enabled ?: false,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy(device_telemetry_enabled = it) },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                    HorizontalDivider()
                }
                val items = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.device_metrics_update_interval_seconds),
                    selectedItem = (formState.value.device_update_interval ?: 0).toLong(),
                    enabled = state.connected,
                    items = items.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(device_update_interval = it.toInt()) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.environment_metrics_module_enabled),
                    checked = formState.value.environment_measurement_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(environment_measurement_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val envItems = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.environment_metrics_update_interval_seconds),
                    selectedItem = (formState.value.environment_update_interval ?: 0).toLong(),
                    enabled = state.connected,
                    items = envItems.map { it.value to it.toDisplayString() },
                    onItemSelected = {
                        formState.value = formState.value.copy(environment_update_interval = it.toInt())
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.environment_metrics_on_screen_enabled),
                    checked = formState.value.environment_screen_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(environment_screen_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.environment_metrics_use_fahrenheit),
                    checked = formState.value.environment_display_fahrenheit ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(environment_display_fahrenheit = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.air_quality_metrics_module_enabled),
                    checked = formState.value.air_quality_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(air_quality_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val airItems = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.air_quality_metrics_update_interval_seconds),
                    selectedItem = (formState.value.air_quality_interval ?: 0).toLong(),
                    enabled = state.connected,
                    items = airItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(air_quality_interval = it.toInt()) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.power_metrics_module_enabled),
                    checked = formState.value.power_measurement_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(power_measurement_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val powerItems = remember { IntervalConfiguration.BROADCAST_SHORT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.power_metrics_update_interval_seconds),
                    selectedItem = (formState.value.power_update_interval ?: 0).toLong(),
                    enabled = state.connected,
                    items = powerItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(power_update_interval = it.toInt()) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.power_metrics_on_screen_enabled),
                    checked = formState.value.power_screen_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(power_screen_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
