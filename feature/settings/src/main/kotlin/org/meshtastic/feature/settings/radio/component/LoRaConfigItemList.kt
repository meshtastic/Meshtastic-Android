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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.RegionInfo
import org.meshtastic.core.model.numChannels
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.advanced
import org.meshtastic.core.strings.bandwidth
import org.meshtastic.core.strings.coding_rate
import org.meshtastic.core.strings.config_lora_frequency_slot_summary
import org.meshtastic.core.strings.config_lora_hop_limit_summary
import org.meshtastic.core.strings.config_lora_modem_preset_summary
import org.meshtastic.core.strings.config_lora_region_summary
import org.meshtastic.core.strings.frequency_slot
import org.meshtastic.core.strings.hop_limit
import org.meshtastic.core.strings.ignore_mqtt
import org.meshtastic.core.strings.lora
import org.meshtastic.core.strings.modem_preset
import org.meshtastic.core.strings.ok_to_mqtt
import org.meshtastic.core.strings.options
import org.meshtastic.core.strings.override_duty_cycle
import org.meshtastic.core.strings.override_frequency_mhz
import org.meshtastic.core.strings.pa_fan_disabled
import org.meshtastic.core.strings.region_frequency_plan
import org.meshtastic.core.strings.spread_factor
import org.meshtastic.core.strings.sx126x_rx_boosted_gain
import org.meshtastic.core.strings.tx_enabled
import org.meshtastic.core.strings.tx_power_dbm
import org.meshtastic.core.strings.use_modem_preset
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.hopLimits
import org.meshtastic.proto.config
import org.meshtastic.proto.copy

@Composable
fun LoRaConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val loraConfig = state.radioConfig.lora
    val primarySettings = state.channelList.getOrNull(0) ?: return
    val formState = rememberConfigState(initialValue = loraConfig)

    val primaryChannel by remember(formState.value) { mutableStateOf(Channel(primarySettings, formState.value)) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.lora),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { lora = it }
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.options)) {
                DropDownPreference(
                    title = stringResource(Res.string.region_frequency_plan),
                    summary = stringResource(Res.string.config_lora_region_summary),
                    enabled = state.connected,
                    items = RegionInfo.entries.map { it.regionCode to it.description },
                    selectedItem = formState.value.region,
                    onItemSelected = { formState.value = formState.value.copy { region = it } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.use_modem_preset),
                    checked = formState.value.usePreset,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { usePreset = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                if (formState.value.usePreset) {
                    DropDownPreference(
                        title = stringResource(Res.string.modem_preset),
                        summary = stringResource(Res.string.config_lora_modem_preset_summary),
                        enabled = state.connected && formState.value.usePreset,
                        items = ChannelOption.entries.map { it.modemPreset to stringResource(it.labelRes) },
                        selectedItem = formState.value.modemPreset,
                        onItemSelected = { formState.value = formState.value.copy { modemPreset = it } },
                    )
                } else {
                    EditTextPreference(
                        title = stringResource(Res.string.bandwidth),
                        value = formState.value.bandwidth,
                        enabled = state.connected && !formState.value.usePreset,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy { bandwidth = it } },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.spread_factor),
                        value = formState.value.spreadFactor,
                        enabled = state.connected && !formState.value.usePreset,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy { spreadFactor = it } },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.coding_rate),
                        value = formState.value.codingRate,
                        enabled = state.connected && !formState.value.usePreset,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy { codingRate = it } },
                    )
                }
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.advanced)) {
                SwitchPreference(
                    title = stringResource(Res.string.ignore_mqtt),
                    checked = formState.value.ignoreMqtt,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { ignoreMqtt = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.ok_to_mqtt),
                    checked = formState.value.configOkToMqtt,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { configOkToMqtt = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.tx_enabled),
                    checked = formState.value.txEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { txEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.override_duty_cycle),
                    checked = formState.value.overrideDutyCycle,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { overrideDutyCycle = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val hopLimitItems = remember { hopLimits }
                DropDownPreference(
                    title = stringResource(Res.string.hop_limit),
                    summary = stringResource(Res.string.config_lora_hop_limit_summary),
                    items = hopLimitItems,
                    selectedItem = formState.value.hopLimit,
                    onItemSelected = { formState.value = formState.value.copy { hopLimit = it } },
                    enabled = state.connected,
                )
                HorizontalDivider()
                var isFocusedSlot by remember { mutableStateOf(false) }
                EditTextPreference(
                    title = stringResource(Res.string.frequency_slot),
                    summary = stringResource(Res.string.config_lora_frequency_slot_summary),
                    value =
                    if (isFocusedSlot || formState.value.channelNum != 0) {
                        formState.value.channelNum
                    } else {
                        primaryChannel.channelNum
                    },
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onFocusChanged = { isFocusedSlot = it.isFocused },
                    onValueChanged = {
                        if (it <= formState.value.numChannels) { // total num of LoRa channels
                            formState.value = formState.value.copy { channelNum = it }
                        }
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.sx126x_rx_boosted_gain),
                    checked = formState.value.sx126XRxBoostedGain,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { sx126XRxBoostedGain = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                var isFocusedOverride by remember { mutableStateOf(false) }
                EditTextPreference(
                    title = stringResource(Res.string.override_frequency_mhz),
                    value =
                    if (isFocusedOverride || formState.value.overrideFrequency != 0f) {
                        formState.value.overrideFrequency
                    } else {
                        primaryChannel.radioFreq
                    },
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onFocusChanged = { isFocusedOverride = it.isFocused },
                    onValueChanged = { formState.value = formState.value.copy { overrideFrequency = it } },
                )
                HorizontalDivider()
                SignedIntegerEditTextPreference(
                    title = stringResource(Res.string.tx_power_dbm),
                    value = formState.value.txPower,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { txPower = it } },
                )
                if (viewModel.hasPaFan) {
                    HorizontalDivider()
                    SwitchPreference(
                        title = stringResource(Res.string.pa_fan_disabled),
                        checked = formState.value.paFanDisabled,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy { paFanDisabled = it } },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                }
            }
        }
    }
}
