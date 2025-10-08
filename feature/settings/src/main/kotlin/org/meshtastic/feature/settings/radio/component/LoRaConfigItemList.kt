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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.RegionInfo
import org.meshtastic.core.model.numChannels
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceDivider
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
fun LoRaConfigScreen(navController: NavController, viewModel: RadioConfigViewModel) {
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
        item {
            TitledCard(title = stringResource(R.string.options)) {
                DropDownPreference(
                    title = stringResource(R.string.region_frequency_plan),
                    summary = stringResource(id = R.string.config_lora_region_summary),
                    enabled = state.connected,
                    items = RegionInfo.entries.map { it.regionCode to it.description },
                    selectedItem = formState.value.region,
                    onItemSelected = { formState.value = formState.value.copy { region = it } },
                )

                PreferenceDivider()

                SwitchPreference(
                    title = stringResource(R.string.use_modem_preset),
                    checked = formState.value.usePreset,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { usePreset = it } },
                    containerColor = Color.Transparent,
                )

                PreferenceDivider()

                if (formState.value.usePreset) {
                    DropDownPreference(
                        title = stringResource(R.string.modem_preset),
                        summary = stringResource(id = R.string.config_lora_modem_preset_summary),
                        enabled = state.connected && formState.value.usePreset,
                        items = ChannelOption.entries.map { it.modemPreset to stringResource(it.labelRes) },
                        selectedItem = formState.value.modemPreset,
                        onItemSelected = { formState.value = formState.value.copy { modemPreset = it } },
                    )
                } else {
                    EditTextPreference(
                        title = stringResource(R.string.bandwidth),
                        value = formState.value.bandwidth,
                        enabled = state.connected && !formState.value.usePreset,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy { bandwidth = it } },
                    )

                    PreferenceDivider()

                    EditTextPreference(
                        title = stringResource(R.string.spread_factor),
                        value = formState.value.spreadFactor,
                        enabled = state.connected && !formState.value.usePreset,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy { spreadFactor = it } },
                    )

                    PreferenceDivider()

                    EditTextPreference(
                        title = stringResource(R.string.coding_rate),
                        value = formState.value.codingRate,
                        enabled = state.connected && !formState.value.usePreset,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy { codingRate = it } },
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            TitledCard(title = stringResource(R.string.advanced)) {
                SwitchPreference(
                    title = stringResource(R.string.ignore_mqtt),
                    checked = formState.value.ignoreMqtt,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { ignoreMqtt = it } },
                    containerColor = Color.Transparent,
                )

                PreferenceDivider()

                SwitchPreference(
                    title = stringResource(R.string.ok_to_mqtt),
                    checked = formState.value.configOkToMqtt,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { configOkToMqtt = it } },
                    containerColor = Color.Transparent,
                )

                PreferenceDivider()

                SwitchPreference(
                    title = stringResource(R.string.tx_enabled),
                    checked = formState.value.txEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { txEnabled = it } },
                    containerColor = Color.Transparent,
                )

                PreferenceDivider()

                EditTextPreference(
                    title = stringResource(R.string.hop_limit),
                    summary = stringResource(id = R.string.config_lora_hop_limit_summary),
                    value = formState.value.hopLimit,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { hopLimit = it } },
                )

                PreferenceDivider()

                var isFocusedSlot by remember { mutableStateOf(false) }
                EditTextPreference(
                    title = stringResource(R.string.frequency_slot),
                    summary = stringResource(id = R.string.config_lora_frequency_slot_summary),
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

                PreferenceDivider()

                SwitchPreference(
                    title = stringResource(R.string.sx126x_rx_boosted_gain),
                    checked = formState.value.sx126XRxBoostedGain,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { sx126XRxBoostedGain = it } },
                    containerColor = Color.Transparent,
                )

                PreferenceDivider()

                var isFocusedOverride by remember { mutableStateOf(false) }
                EditTextPreference(
                    title = stringResource(R.string.override_frequency_mhz),
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

                PreferenceDivider()

                SignedIntegerEditTextPreference(
                    title = stringResource(R.string.tx_power_dbm),
                    value = formState.value.txPower,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { txPower = it } },
                )

                if (viewModel.hasPaFan) {
                    SwitchPreference(
                        title = stringResource(R.string.pa_fan_disabled),
                        checked = formState.value.paFanDisabled,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy { paFanDisabled = it } },
                        containerColor = Color.Transparent,
                    )
                }
            }
        }
    }
}
