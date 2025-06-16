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

package com.geeksville.mesh.ui.radioconfig.components

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.MQTTConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.EditPasswordPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel

const val MapConsentPreferencesKey = "map_consent_preferences"

@Composable
fun MQTTConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val destNum = destNode?.num

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    MQTTConfigItemList(
        nodeNum = destNum,
        mqttConfig = state.moduleConfig.mqtt,
        enabled = state.connected,
        onSaveClicked = { mqttInput ->
            val config = moduleConfig { mqtt = mqttInput }
            viewModel.setModuleConfig(config)
        }
    )
}

@Composable
fun MQTTConfigItemList(
    nodeNum: Int? = 0,
    mqttConfig: MQTTConfig,
    enabled: Boolean,
    onSaveClicked: (MQTTConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var mqttInput by rememberSaveable { mutableStateOf(mqttConfig) }
    val sharedPrefs = LocalContext.current.getSharedPreferences(
        MapConsentPreferencesKey, Context.MODE_PRIVATE
    )
    if (!mqttInput.mapReportSettings.shouldReportLocation) {
        val settings = mqttInput.mapReportSettings.copy {
            this.shouldReportLocation = sharedPrefs.getBoolean(nodeNum.toString(), false)
        }
        mqttInput = mqttInput.copy { mapReportSettings = settings }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = stringResource(R.string.mqtt_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.mqtt_enabled),
                checked = mqttInput.enabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { this.enabled = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.address),
                value = mqttInput.address,
                maxSize = 63, // address max_size:64
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { address = it } }
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.username),
                value = mqttInput.username,
                maxSize = 63, // username max_size:64
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { username = it } }
            )
        }

        item {
            EditPasswordPreference(
                title = stringResource(R.string.password),
                value = mqttInput.password,
                maxSize = 63, // password max_size:64
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { password = it } }
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.encryption_enabled),
                checked = mqttInput.encryptionEnabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { encryptionEnabled = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.json_output_enabled),
                checked = mqttInput.jsonEnabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { jsonEnabled = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.tls_enabled),
                checked = mqttInput.tlsEnabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { tlsEnabled = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.root_topic),
                value = mqttInput.root,
                maxSize = 31, // root max_size:32
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { root = it } }
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.proxy_to_client_enabled),
                checked = mqttInput.proxyToClientEnabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { proxyToClientEnabled = it } }
            )
        }
        item { HorizontalDivider() }
        // mqtt map reporting opt in
        item { PreferenceCategory(text = stringResource(R.string.map_reporting)) }

        item {
            MapReportingPreference(
                mapReportingEnabled = mqttInput.mapReportingEnabled,
                onMapReportingEnabledChanged = {
                    mqttInput = mqttInput.copy { mapReportingEnabled = it }
                },
                shouldReportLocation = mqttInput.mapReportSettings.shouldReportLocation,
                onShouldReportLocationChanged = {
                    sharedPrefs.edit { putBoolean(nodeNum.toString(), it) }
                    val settings =
                        mqttInput.mapReportSettings.copy { this.shouldReportLocation = it }
                    mqttInput = mqttInput.copy {
                        mapReportSettings = settings
                    }
                },
                positionPrecision = mqttInput.mapReportSettings.positionPrecision,
                onPositionPrecisionChanged = {
                    val settings = mqttInput.mapReportSettings.copy { positionPrecision = it }
                    mqttInput = mqttInput.copy {
                        mapReportSettings = settings
                    }
                },
                publishIntervalSecs = mqttInput.mapReportSettings.publishIntervalSecs,
                onPublishIntervalSecsChanged = {
                    val settings = mqttInput.mapReportSettings.copy { publishIntervalSecs = it }
                    mqttInput = mqttInput.copy {
                        mapReportSettings = settings
                    }
                },
                enabled = enabled,
                focusManager = focusManager
            )
        }
        item { HorizontalDivider() }

        item {
            val consentValid = if (mqttInput.mapReportingEnabled) {
                mqttInput.mapReportSettings.shouldReportLocation &&
                        mqttConfig.mapReportSettings.publishIntervalSecs >= MinIntervalSecs
            } else {
                true
            }
            PreferenceFooter(
                enabled = enabled && mqttInput != mqttConfig && consentValid,
                onCancelClicked = {
                    focusManager.clearFocus()
                    mqttInput = mqttConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(mqttInput)
                }
            )
        }
    }
}

private const val MinIntervalSecs = 3600

@Preview(showBackground = true)
@Composable
private fun MQTTConfigPreview() {
    MQTTConfigItemList(
        mqttConfig = MQTTConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
