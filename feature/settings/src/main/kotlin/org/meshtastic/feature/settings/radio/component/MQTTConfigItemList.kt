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
@file:Suppress("LongMethod")

package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.address
import org.meshtastic.core.strings.default_mqtt_address
import org.meshtastic.core.strings.encryption_enabled
import org.meshtastic.core.strings.json_output_enabled
import org.meshtastic.core.strings.map_reporting
import org.meshtastic.core.strings.mqtt
import org.meshtastic.core.strings.mqtt_config
import org.meshtastic.core.strings.mqtt_enabled
import org.meshtastic.core.strings.password
import org.meshtastic.core.strings.proxy_to_client_enabled
import org.meshtastic.core.strings.root_topic
import org.meshtastic.core.strings.tls_enabled
import org.meshtastic.core.strings.username
import org.meshtastic.core.ui.component.EditPasswordPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Composable
fun MQTTConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val destNum = destNode?.num
    val mqttConfig = state.moduleConfig.mqtt ?: ModuleConfig.MQTTConfig()
    val formState = rememberConfigState(initialValue = mqttConfig)

    val currentMapReportSettings = formState.value.map_report_settings ?: ModuleConfig.MapReportSettings()
    if (!(currentMapReportSettings.should_report_location ?: false)) {
        val settings = currentMapReportSettings.copy(should_report_location = viewModel.shouldReportLocation(destNum))
        formState.value = formState.value.copy(map_report_settings = settings)
    }

    val consentValid =
        if (formState.value.map_reporting_enabled ?: false) {
            (formState.value.map_report_settings?.should_report_location ?: false) &&
                (formState.value.map_report_settings?.publish_interval_secs ?: 0) >= MIN_INTERVAL_SECS
        } else {
            true
        }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.mqtt),
        onBack = onBack,
        configState = formState,
        enabled = state.connected && consentValid,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(mqtt = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.mqtt_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.mqtt_enabled),
                    checked = formState.value.enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.address),
                    value = formState.value.address ?: "",
                    maxSize = 63, // address max_size:64
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(address = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.username),
                    value = formState.value.username ?: "",
                    maxSize = 63, // username max_size:64
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(username = it) },
                )
                HorizontalDivider()
                EditPasswordPreference(
                    title = stringResource(Res.string.password),
                    value = formState.value.password ?: "",
                    maxSize = 63, // password max_size:64
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(password = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.encryption_enabled),
                    checked = formState.value.encryption_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(encryption_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.json_output_enabled),
                    checked = formState.value.json_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(json_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val defaultAddress = stringResource(Res.string.default_mqtt_address)
                val isDefault =
                    (formState.value.address ?: "").isEmpty() ||
                        (formState.value.address ?: "").contains(defaultAddress)
                val enforceTls = isDefault && (formState.value.proxy_to_client_enabled ?: false)
                SwitchPreference(
                    title = stringResource(Res.string.tls_enabled),
                    checked = (formState.value.tls_enabled ?: false) || enforceTls,
                    enabled = state.connected && !enforceTls,
                    onCheckedChange = { formState.value = formState.value.copy(tls_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.root_topic),
                    value = formState.value.root ?: "",
                    maxSize = 31, // root max_size:32
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(root = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.proxy_to_client_enabled),
                    checked = formState.value.proxy_to_client_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(proxy_to_client_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.map_reporting)) {
                val mapReportSettings = formState.value.map_report_settings ?: ModuleConfig.MapReportSettings()
                MapReportingPreference(
                    mapReportingEnabled = formState.value.map_reporting_enabled ?: false,
                    onMapReportingEnabledChanged = {
                        formState.value = formState.value.copy(map_reporting_enabled = it)
                    },
                    shouldReportLocation = mapReportSettings.should_report_location ?: false,
                    onShouldReportLocationChanged = {
                        viewModel.setShouldReportLocation(destNum, it)
                        val settings = mapReportSettings.copy(should_report_location = it)
                        formState.value = formState.value.copy(map_report_settings = settings)
                    },
                    positionPrecision = mapReportSettings.position_precision ?: 0,
                    onPositionPrecisionChanged = {
                        val settings = mapReportSettings.copy(position_precision = it)
                        formState.value = formState.value.copy(map_report_settings = settings)
                    },
                    publishIntervalSecs = mapReportSettings.publish_interval_secs ?: 0,
                    onPublishIntervalSecsChanged = {
                        val settings = mapReportSettings.copy(publish_interval_secs = it)
                        formState.value = formState.value.copy(map_report_settings = settings)
                    },
                    enabled = state.connected,
                )
            }
        }
    }
}

private const val MIN_INTERVAL_SECS = 3600
