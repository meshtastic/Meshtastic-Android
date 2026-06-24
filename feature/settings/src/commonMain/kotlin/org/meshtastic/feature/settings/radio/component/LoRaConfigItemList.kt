/*
 * Copyright (c) 2026 Meshtastic LLC
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
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.RegionInfo
import org.meshtastic.core.model.RegionPresetConstraint
import org.meshtastic.core.model.constraintFor
import org.meshtastic.core.model.numChannels
import org.meshtastic.core.model.repairPresetFor
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.advanced
import org.meshtastic.core.resources.bandwidth
import org.meshtastic.core.resources.coding_rate
import org.meshtastic.core.resources.config_lora_frequency_slot_summary
import org.meshtastic.core.resources.config_lora_hop_limit_summary
import org.meshtastic.core.resources.config_lora_modem_preset_licensed_summary
import org.meshtastic.core.resources.config_lora_modem_preset_summary
import org.meshtastic.core.resources.config_lora_region_summary
import org.meshtastic.core.resources.frequency_slot
import org.meshtastic.core.resources.hop_limit
import org.meshtastic.core.resources.ignore_mqtt
import org.meshtastic.core.resources.lora
import org.meshtastic.core.resources.modem_preset
import org.meshtastic.core.resources.ok_to_mqtt
import org.meshtastic.core.resources.options
import org.meshtastic.core.resources.override_duty_cycle
import org.meshtastic.core.resources.override_frequency_mhz
import org.meshtastic.core.resources.pa_fan_disabled
import org.meshtastic.core.resources.region_frequency_plan
import org.meshtastic.core.resources.spread_factor
import org.meshtastic.core.resources.sx126x_rx_boosted_gain
import org.meshtastic.core.resources.tx_enabled
import org.meshtastic.core.resources.tx_power_dbm
import org.meshtastic.core.resources.use_modem_preset
import org.meshtastic.core.ui.component.DropDownItem
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.hopLimits
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset

private val SPREAD_FACTOR_RANGE = 7..12
private val CODING_RATE_RANGE = 5..8

/**
 * Builds the modem-preset dropdown items: hide the 2.8-only TINY presets on firmware without
 * [Capabilities.supportsLoraRegionPresetMap], restrict to the region's legal presets (R7), then always keep the current
 * selection present (disabled) so the field is never blank when the device's preset is illegal for the region.
 */
private fun buildPresetItems(
    presetConstraint: RegionPresetConstraint?,
    presetsGated: Boolean,
    selectedPreset: ModemPreset,
    capabilities: Capabilities,
): List<DropDownItem<ModemPreset>> {
    val items =
        ChannelOption.entries
            .filter { option ->
                capabilities.supportsLoraRegionPresetMap ||
                    (option != ChannelOption.TINY_FAST && option != ChannelOption.TINY_SLOW)
            }
            .filter { option -> presetConstraint == null || option.modemPreset in presetConstraint.presets }
            .map { option ->
                DropDownItem(value = option.modemPreset, label = option.modemPreset.name, enabled = !presetsGated)
            }
    return if (items.any { it.value == selectedPreset }) {
        items
    } else {
        items + DropDownItem(value = selectedPreset, label = selectedPreset.name, enabled = false)
    }
}

@Composable
fun LoRaConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val loraConfig = state.radioConfig.lora ?: Config.LoRaConfig()
    val primarySettings = state.channelList.getOrNull(0)

    if (primarySettings == null) {
        RadioConfigScreenList(
            title = stringResource(Res.string.lora),
            onBack = onBack,
            configState = rememberConfigState(initialValue = loraConfig),
            enabled = false,
            responseState = state.responseState,
            onDismissPacketResponse = viewModel::clearPacketResponse,
            onSave = {},
        ) {}
        return
    }

    val formState = rememberConfigState(initialValue = loraConfig)

    val primaryChannel = remember(formState.value) { Channel(primarySettings, formState.value) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.lora),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = Config(lora = it)
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.options)) {
                // The region→preset legality map is a function of firmware version + region, not of the device
                // instance, so the locally-cached map (from our own handshake) is reused for remote admin too. Gated
                // on the *target* node's firmware capability (metadata is per-target): pre-2.8 nodes don't get the
                // map or the new TINY presets, which also keeps older remotes unconstrained.
                val capabilities =
                    remember(state.metadata?.firmware_version) { Capabilities(state.metadata?.firmware_version) }
                val regionPresetMap = if (capabilities.supportsLoraRegionPresetMap) state.loraRegionPresetMap else null
                val presetConstraint =
                    remember(regionPresetMap, formState.value.region) {
                        regionPresetMap.constraintFor(formState.value.region)
                    }
                val presetsGated = presetConstraint?.isGated(state.localIsLicensed) == true
                DropDownPreference(
                    title = stringResource(Res.string.region_frequency_plan),
                    summary = stringResource(Res.string.config_lora_region_summary),
                    enabled = state.connected,
                    items = RegionInfo.entries.map { it.regionCode to it.description },
                    selectedItem = formState.value.region,
                    onItemSelected = { region ->
                        // When the region changes, snap the preset to the region's default if the current one is
                        // no longer legal there (R7); a no-op when the region is unconstrained.
                        val repaired = regionPresetMap.repairPresetFor(region, formState.value.modem_preset)
                        formState.value = formState.value.copy(region = region, modem_preset = repaired)
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.use_modem_preset),
                    checked = formState.value.use_preset,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(use_preset = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                if (formState.value.use_preset) {
                    // Restrict the preset list to those legal in the selected region (R7); a null constraint means
                    // unconstrained, so show every preset. Licensed-only regions disable their presets unless the
                    // device is flagged as a licensed operator (R8).
                    val selectedPreset = formState.value.modem_preset
                    val presetItems =
                        remember(presetConstraint, presetsGated, selectedPreset, capabilities) {
                            buildPresetItems(presetConstraint, presetsGated, selectedPreset, capabilities)
                        }
                    DropDownPreference(
                        title = stringResource(Res.string.modem_preset),
                        summary =
                        if (presetsGated) {
                            stringResource(Res.string.config_lora_modem_preset_licensed_summary)
                        } else {
                            stringResource(Res.string.config_lora_modem_preset_summary)
                        },
                        enabled = state.connected,
                        items = presetItems,
                        selectedItem = formState.value.modem_preset,
                        onItemSelected = { formState.value = formState.value.copy(modem_preset = it) },
                    )
                } else {
                    ManualModemSettings(
                        config = formState.value,
                        enabled = state.connected,
                        focusManager = focusManager,
                        onConfigChange = { formState.value = it },
                    )
                }
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.advanced)) {
                SwitchPreference(
                    title = stringResource(Res.string.ignore_mqtt),
                    checked = formState.value.ignore_mqtt,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(ignore_mqtt = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.ok_to_mqtt),
                    checked = formState.value.config_ok_to_mqtt,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(config_ok_to_mqtt = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.tx_enabled),
                    checked = formState.value.tx_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(tx_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.override_duty_cycle),
                    checked = formState.value.override_duty_cycle,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(override_duty_cycle = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val hopLimitItems = remember { hopLimits }
                DropDownPreference(
                    title = stringResource(Res.string.hop_limit),
                    summary = stringResource(Res.string.config_lora_hop_limit_summary),
                    items = hopLimitItems,
                    selectedItem = formState.value.hop_limit,
                    onItemSelected = { formState.value = formState.value.copy(hop_limit = it) },
                    enabled = state.connected,
                )
                HorizontalDivider()
                var isFocusedSlot by remember { mutableStateOf(false) }
                EditTextPreference(
                    title = stringResource(Res.string.frequency_slot),
                    summary = stringResource(Res.string.config_lora_frequency_slot_summary),
                    value =
                    if (isFocusedSlot || formState.value.channel_num != 0) {
                        formState.value.channel_num
                    } else {
                        primaryChannel.channelNum
                    },
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onFocusChanged = { isFocusedSlot = it.isFocused },
                    onValueChanged = {
                        if (it <= formState.value.numChannels) { // total num of LoRa channels
                            formState.value = formState.value.copy(channel_num = it)
                        }
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.sx126x_rx_boosted_gain),
                    checked = formState.value.sx126x_rx_boosted_gain,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(sx126x_rx_boosted_gain = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                var isFocusedOverride by remember { mutableStateOf(false) }
                EditTextPreference(
                    title = stringResource(Res.string.override_frequency_mhz),
                    value =
                    if (isFocusedOverride || formState.value.override_frequency != 0f) {
                        formState.value.override_frequency
                    } else {
                        primaryChannel.radioFreq
                    },
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onFocusChanged = { isFocusedOverride = it.isFocused },
                    onValueChanged = { formState.value = formState.value.copy(override_frequency = it) },
                )
                HorizontalDivider()
                SignedIntegerEditTextPreference(
                    title = stringResource(Res.string.tx_power_dbm),
                    value = formState.value.tx_power,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(tx_power = it) },
                )
                if (viewModel.hasPaFan) {
                    HorizontalDivider()
                    SwitchPreference(
                        title = stringResource(Res.string.pa_fan_disabled),
                        checked = formState.value.pa_fan_disabled,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy(pa_fan_disabled = it) },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualModemSettings(
    config: Config.LoRaConfig,
    enabled: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onConfigChange: (Config.LoRaConfig) -> Unit,
) {
    androidx.compose.foundation.layout.Column {
        EditTextPreference(
            title = stringResource(Res.string.bandwidth),
            value = config.bandwidth,
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = { onConfigChange(config.copy(bandwidth = it)) },
        )
        HorizontalDivider()
        EditTextPreference(
            title = stringResource(Res.string.spread_factor),
            value = config.spread_factor,
            enabled = enabled,
            isError = config.spread_factor !in SPREAD_FACTOR_RANGE,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = {
                if (it in SPREAD_FACTOR_RANGE) {
                    onConfigChange(config.copy(spread_factor = it))
                }
            },
        )
        HorizontalDivider()
        EditTextPreference(
            title = stringResource(Res.string.coding_rate),
            value = config.coding_rate,
            enabled = enabled,
            isError = config.coding_rate !in CODING_RATE_RANGE,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = {
                if (it in CODING_RATE_RANGE) {
                    onConfigChange(config.copy(coding_rate = it))
                }
            },
        )
    }
}
