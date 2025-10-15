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

@file:Suppress("LongMethod")

package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.EditPasswordPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun MQTTConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val destNum = destNode?.num
    val mqttConfig = state.moduleConfig.mqtt
    val formState = rememberConfigState(initialValue = mqttConfig)

    if (!formState.value.mapReportSettings.shouldReportLocation) {
        val settings =
            formState.value.mapReportSettings.copy {
                this.shouldReportLocation = viewModel.shouldReportLocation(destNum)
            }
        formState.value = formState.value.copy { mapReportSettings = settings }
    }

    val consentValid =
        if (formState.value.mapReportingEnabled) {
            formState.value.mapReportSettings.shouldReportLocation &&
                mqttConfig.mapReportSettings.publishIntervalSecs >= MIN_INTERVAL_SECS
        } else {
            true
        }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.mqtt),
        onBack = onBack,
        configState = formState,
        enabled = state.connected && consentValid,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { mqtt = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(R.string.mqtt_config)) {
                SwitchPreference(
                    title = stringResource(R.string.mqtt_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.address),
                    value = formState.value.address,
                    maxSize = 63, // address max_size:64
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { address = it } },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.username),
                    value = formState.value.username,
                    maxSize = 63, // username max_size:64
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { username = it } },
                )
                HorizontalDivider()
                EditPasswordPreference(
                    title = stringResource(R.string.password),
                    value = formState.value.password,
                    maxSize = 63, // password max_size:64
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { password = it } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.encryption_enabled),
                    checked = formState.value.encryptionEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { encryptionEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.json_output_enabled),
                    checked = formState.value.jsonEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { jsonEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val defaultAddress = stringResource(R.string.default_mqtt_address)
                val isDefault = formState.value.address.isEmpty() || formState.value.address.contains(defaultAddress)
                val enforceTls = isDefault && formState.value.proxyToClientEnabled
                SwitchPreference(
                    title = stringResource(R.string.tls_enabled),
                    checked = formState.value.tlsEnabled || enforceTls,
                    enabled = state.connected && !enforceTls,
                    onCheckedChange = { formState.value = formState.value.copy { tlsEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.root_topic),
                    value = formState.value.root,
                    maxSize = 31, // root max_size:32
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { root = it } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.proxy_to_client_enabled),
                    checked = formState.value.proxyToClientEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { proxyToClientEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }

        item {
            TitledCard(title = stringResource(R.string.map_reporting)) {
                MapReportingPreference(
                    mapReportingEnabled = formState.value.mapReportingEnabled,
                    onMapReportingEnabledChanged = {
                        formState.value = formState.value.copy { mapReportingEnabled = it }
                    },
                    shouldReportLocation = formState.value.mapReportSettings.shouldReportLocation,
                    onShouldReportLocationChanged = {
                        viewModel.setShouldReportLocation(destNum, it)
                        val settings = formState.value.mapReportSettings.copy { this.shouldReportLocation = it }
                        formState.value = formState.value.copy { mapReportSettings = settings }
                    },
                    positionPrecision = formState.value.mapReportSettings.positionPrecision,
                    onPositionPrecisionChanged = {
                        val settings = formState.value.mapReportSettings.copy { positionPrecision = it }
                        formState.value = formState.value.copy { mapReportSettings = settings }
                    },
                    publishIntervalSecs = formState.value.mapReportSettings.publishIntervalSecs,
                    onPublishIntervalSecsChanged = {
                        val settings = formState.value.mapReportSettings.copy { publishIntervalSecs = it }
                        formState.value = formState.value.copy { mapReportSettings = settings }
                    },
                    enabled = state.connected,
                )
            }
        }
    }
}

private const val MIN_INTERVAL_SECS = 3600
