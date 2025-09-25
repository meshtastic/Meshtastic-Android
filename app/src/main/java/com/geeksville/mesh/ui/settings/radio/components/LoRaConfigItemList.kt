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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.RegionInfo
import org.meshtastic.core.model.numChannels
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference

@Composable
fun LoRaConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val loraConfig = state.radioConfig.lora
    val primarySettings = state.channelList.getOrNull(0) ?: return
    val formState = rememberConfigState(initialValue = loraConfig)

    val primaryChannel by remember(formState.value) { mutableStateOf(Channel(primarySettings, formState.value)) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.lora),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { lora = it }
            viewModel.setConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.options)) }
        item {
            DropDownPreference(
                title = stringResource(R.string.region_frequency_plan),
                summary = stringResource(id = R.string.config_lora_region_summary),
                enabled = state.connected,
                items = RegionInfo.entries.map { it.regionCode to it.description },
                selectedItem = formState.value.region,
                onItemSelected = { formState.value = formState.value.copy { region = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.use_modem_preset),
                checked = formState.value.usePreset,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { usePreset = it } },
            )
        }
        item { HorizontalDivider() }

        if (formState.value.usePreset) {
            item {
                DropDownPreference(
                    title = stringResource(R.string.modem_preset),
                    summary = stringResource(id = R.string.config_lora_modem_preset_summary),
                    enabled = state.connected && formState.value.usePreset,
                    items =
                    LoRaConfig.ModemPreset.entries
                        .filter { it != LoRaConfig.ModemPreset.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.modemPreset,
                    onItemSelected = { formState.value = formState.value.copy { modemPreset = it } },
                )
            }
            item { HorizontalDivider() }
        } else {
            item {
                EditTextPreference(
                    title = stringResource(R.string.bandwidth),
                    value = formState.value.bandwidth,
                    enabled = state.connected && !formState.value.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { bandwidth = it } },
                )
            }

            item {
                EditTextPreference(
                    title = stringResource(R.string.spread_factor),
                    value = formState.value.spreadFactor,
                    enabled = state.connected && !formState.value.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { spreadFactor = it } },
                )
            }

            item {
                EditTextPreference(
                    title = stringResource(R.string.coding_rate),
                    value = formState.value.codingRate,
                    enabled = state.connected && !formState.value.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { codingRate = it } },
                )
            }
        }
        item { PreferenceCategory(text = stringResource(R.string.advanced)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.ignore_mqtt),
                checked = formState.value.ignoreMqtt,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { ignoreMqtt = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.ok_to_mqtt),
                checked = formState.value.configOkToMqtt,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { configOkToMqtt = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.tx_enabled),
                checked = formState.value.txEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { txEnabled = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            EditTextPreference(
                title = stringResource(R.string.hop_limit),
                summary = stringResource(id = R.string.config_lora_hop_limit_summary),
                value = formState.value.hopLimit,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { hopLimit = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            var isFocused by remember { mutableStateOf(false) }
            EditTextPreference(
                title = stringResource(R.string.frequency_slot),
                summary = stringResource(id = R.string.config_lora_frequency_slot_summary),
                value =
                if (isFocused || formState.value.channelNum != 0) {
                    formState.value.channelNum
                } else {
                    primaryChannel.channelNum
                },
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onFocusChanged = { isFocused = it.isFocused },
                onValueChanged = {
                    if (it <= formState.value.numChannels) { // total num of LoRa channels
                        formState.value = formState.value.copy { channelNum = it }
                    }
                },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.sx126x_rx_boosted_gain),
                checked = formState.value.sx126XRxBoostedGain,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { sx126XRxBoostedGain = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            var isFocused by remember { mutableStateOf(false) }
            EditTextPreference(
                title = stringResource(R.string.override_frequency_mhz),
                value =
                if (isFocused || formState.value.overrideFrequency != 0f) {
                    formState.value.overrideFrequency
                } else {
                    primaryChannel.radioFreq
                },
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onFocusChanged = { isFocused = it.isFocused },
                onValueChanged = { formState.value = formState.value.copy { overrideFrequency = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SignedIntegerEditTextPreference(
                title = stringResource(R.string.tx_power_dbm),
                value = formState.value.txPower,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { txPower = it } },
            )
        }

        if (viewModel.hasPaFan) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.pa_fan_disabled),
                    checked = formState.value.paFanDisabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { paFanDisabled = it } },
                )
            }
            item { HorizontalDivider() }
        }
    }
}
