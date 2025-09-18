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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.RegionInfo
import com.geeksville.mesh.model.numChannels
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SignedIntegerEditTextPreference
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel

@Composable
fun LoRaConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    LoRaConfigItemList(
        loraConfig = state.radioConfig.lora,
        primarySettings = state.channelList.getOrNull(0) ?: return,
        enabled = state.connected,
        onSaveClicked = { loraInput ->
            val config = config { lora = loraInput }
            viewModel.setConfig(config)
        },
        hasPaFan = viewModel.hasPaFan,
    )
}

@Suppress("LongMethod")
@Composable
fun LoRaConfigItemList(
    loraConfig: LoRaConfig,
    primarySettings: ChannelSettings,
    enabled: Boolean,
    onSaveClicked: (LoRaConfig) -> Unit,
    hasPaFan: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    var loraInput by rememberSaveable { mutableStateOf(loraConfig) }
    val primaryChannel by remember(loraInput) { mutableStateOf(Channel(primarySettings, loraInput)) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceCategory(text = stringResource(R.string.options)) }
        item {
            DropDownPreference(
                title = stringResource(R.string.region_frequency_plan),
                summary = stringResource(id = R.string.config_lora_region_summary),
                enabled = enabled,
                items = RegionInfo.entries.map { it.regionCode to it.description },
                selectedItem = loraInput.region,
                onItemSelected = { loraInput = loraInput.copy { region = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.use_modem_preset),
                checked = loraInput.usePreset,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { usePreset = it } },
            )
        }
        item { HorizontalDivider() }

        if (loraInput.usePreset) {
            item {
                DropDownPreference(
                    title = stringResource(R.string.modem_preset),
                    summary = stringResource(id = R.string.config_lora_modem_preset_summary),
                    enabled = enabled && loraInput.usePreset,
                    items =
                    LoRaConfig.ModemPreset.entries
                        .filter { it != LoRaConfig.ModemPreset.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = loraInput.modemPreset,
                    onItemSelected = { loraInput = loraInput.copy { modemPreset = it } },
                )
            }
            item { HorizontalDivider() }
        } else {
            item {
                EditTextPreference(
                    title = stringResource(R.string.bandwidth),
                    value = loraInput.bandwidth,
                    enabled = enabled && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { bandwidth = it } },
                )
            }

            item {
                EditTextPreference(
                    title = stringResource(R.string.spread_factor),
                    value = loraInput.spreadFactor,
                    enabled = enabled && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { spreadFactor = it } },
                )
            }

            item {
                EditTextPreference(
                    title = stringResource(R.string.coding_rate),
                    value = loraInput.codingRate,
                    enabled = enabled && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { codingRate = it } },
                )
            }
        }
        item { PreferenceCategory(text = stringResource(R.string.advanced)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.ignore_mqtt),
                checked = loraInput.ignoreMqtt,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { ignoreMqtt = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.ok_to_mqtt),
                checked = loraInput.configOkToMqtt,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { configOkToMqtt = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.tx_enabled),
                checked = loraInput.txEnabled,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { txEnabled = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            EditTextPreference(
                title = stringResource(R.string.hop_limit),
                summary = stringResource(id = R.string.config_lora_hop_limit_summary),
                value = loraInput.hopLimit,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { hopLimit = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            var isFocused by remember { mutableStateOf(false) }
            EditTextPreference(
                title = stringResource(R.string.frequency_slot),
                summary = stringResource(id = R.string.config_lora_frequency_slot_summary),
                value = if (isFocused || loraInput.channelNum != 0) loraInput.channelNum else primaryChannel.channelNum,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onFocusChanged = { isFocused = it.isFocused },
                onValueChanged = {
                    if (it <= loraInput.numChannels) { // total num of LoRa channels
                        loraInput = loraInput.copy { channelNum = it }
                    }
                },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.sx126x_rx_boosted_gain),
                checked = loraInput.sx126XRxBoostedGain,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { sx126XRxBoostedGain = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            var isFocused by remember { mutableStateOf(false) }
            EditTextPreference(
                title = stringResource(R.string.override_frequency_mhz),
                value =
                if (isFocused || loraInput.overrideFrequency != 0f) {
                    loraInput.overrideFrequency
                } else {
                    primaryChannel.radioFreq
                },
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onFocusChanged = { isFocused = it.isFocused },
                onValueChanged = { loraInput = loraInput.copy { overrideFrequency = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SignedIntegerEditTextPreference(
                title = stringResource(R.string.tx_power_dbm),
                value = loraInput.txPower,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { txPower = it } },
            )
        }

        if (hasPaFan) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.pa_fan_disabled),
                    checked = loraInput.paFanDisabled,
                    enabled = enabled,
                    onCheckedChange = { loraInput = loraInput.copy { paFanDisabled = it } },
                )
            }
            item { HorizontalDivider() }
        }

        item {
            PreferenceFooter(
                enabled = enabled && loraInput != loraConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    loraInput = loraConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(loraInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoRaConfigPreview() {
    LoRaConfigItemList(
        loraConfig = Channel.default.loraConfig,
        primarySettings = Channel.default.settings,
        enabled = true,
        onSaveClicked = {},
    )
}
