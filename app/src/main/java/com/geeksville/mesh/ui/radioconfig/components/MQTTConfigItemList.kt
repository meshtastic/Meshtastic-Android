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

package com.geeksville.mesh.ui.radioconfig.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.MQTTConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.EditPasswordPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PositionPrecisionMax
import com.geeksville.mesh.ui.common.components.PositionPrecisionMin
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.common.components.precisionBitsToMeters
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
import com.geeksville.mesh.util.DistanceUnit
import com.geeksville.mesh.util.toDistanceString
import kotlin.math.roundToInt

@Composable
fun MQTTConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    MQTTConfigItemList(
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
    mqttConfig: MQTTConfig,
    enabled: Boolean,
    onSaveClicked: (MQTTConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var mqttInput by rememberSaveable { mutableStateOf(mqttConfig) }
    var showMapReportingWarning by rememberSaveable { mutableStateOf(true) }

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
            SwitchPreference(
                title = stringResource(R.string.map_reporting),
                summary = stringResource(R.string.map_reporting_summary),
                checked = showMapReportingWarning,
                enabled = enabled,
                onCheckedChange = { checked ->
                    showMapReportingWarning = checked
                }
            )
        }
        if (showMapReportingWarning || mqttInput.mapReportingEnabled) {
            item {
                MapReporting(
                    mqttConfig = mqttInput,
                    onMQTTConfigChanged = { mqttInput = it },
                    enabled = enabled,
                    focusManager = focusManager
                )
            }
            item { HorizontalDivider() }
        }

        item {
            PreferenceFooter(
                enabled = enabled && mqttInput != mqttConfig,
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

@Composable
private fun MapReporting(
    mqttConfig: MQTTConfig,
    onMQTTConfigChanged: (MQTTConfig) -> Unit,
    enabled: Boolean,
    focusManager: FocusManager
) {
    var mqttInput by remember { mutableStateOf(mqttConfig) }
    Card(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.map_reporting_consent_header),
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        Text(
            stringResource(R.string.map_reporting_consent_text),
            modifier = Modifier.padding(16.dp)
        )

        SwitchPreference(
            title = stringResource(R.string.i_agree),
            summary = stringResource(R.string.i_agree_to_share_my_location),
            checked = mqttInput.mapReportSettings.shouldReportLocation,
            enabled = enabled,
            onCheckedChange = { checked ->
                val settings = mqttInput.mapReportSettings.copy { shouldReportLocation = checked }
                mqttInput = mqttInput.copy {
                    mapReportingEnabled = checked
                    mapReportSettings = settings
                }
                onMQTTConfigChanged(
                    mqttInput
                )
            },
            containerColor = CardDefaults.cardColors().containerColor,
        )
        val unit = remember { DistanceUnit.getFromLocale() }
        val value = mqttInput.mapReportSettings.positionPrecision
        val onValueChanged = { newValue: Int ->
            val settings = mqttInput.mapReportSettings.copy { positionPrecision = newValue }
            mqttInput = mqttInput.copy { mapReportSettings = settings }
            onMQTTConfigChanged(mqttInput)
        }
        if(mqttInput.mapReportSettings.shouldReportLocation && mqttInput.mapReportingEnabled) {

            Slider(
                modifier = Modifier.padding(horizontal = 16.dp),
                value = value.toFloat(),
                onValueChange = { onValueChanged(it.roundToInt()) },
                enabled = enabled,
                valueRange = PositionPrecisionMin.toFloat()..PositionPrecisionMax.toFloat(),
                steps = PositionPrecisionMax - PositionPrecisionMin - 1,
            )

            val precisionMeters = precisionBitsToMeters(value).toInt()
            Text(
                text = precisionMeters.toDistanceString(unit),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            EditTextPreference(
                title = stringResource(R.string.map_reporting_interval_seconds),
                value = mqttInput.mapReportSettings.publishIntervalSecs,
                enabled = enabled && mqttConfig.mapReportingEnabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val settings = mqttInput.mapReportSettings.copy { publishIntervalSecs = it }
                    mqttInput = mqttInput.copy {
                        mapReportSettings = settings
                    }
                    onMQTTConfigChanged(mqttInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MQTTConfigPreview() {
    MQTTConfigItemList(
        mqttConfig = MQTTConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
