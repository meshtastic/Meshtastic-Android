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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.RegionInfo
import org.meshtastic.core.model.RegionPresetConstraint
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_beacon
import org.meshtastic.core.resources.mesh_beacon_broadcast
import org.meshtastic.core.resources.mesh_beacon_broadcast_summary
import org.meshtastic.core.resources.mesh_beacon_interval
import org.meshtastic.core.resources.mesh_beacon_interval_error
import org.meshtastic.core.resources.mesh_beacon_listen
import org.meshtastic.core.resources.mesh_beacon_listen_summary
import org.meshtastic.core.resources.mesh_beacon_message
import org.meshtastic.core.resources.mesh_beacon_offer_channel_name
import org.meshtastic.core.resources.mesh_beacon_on_preset
import org.meshtastic.core.resources.mesh_beacon_region_label
import org.meshtastic.core.resources.mesh_beacon_region_required
import org.meshtastic.core.resources.mesh_beacon_target
import org.meshtastic.core.resources.mesh_beacon_target_add
import org.meshtastic.core.resources.mesh_beacon_target_channel_index
import org.meshtastic.core.resources.mesh_beacon_target_remove
import org.meshtastic.core.resources.mesh_beacon_targets
import org.meshtastic.core.resources.plurals_seconds
import org.meshtastic.core.ui.component.DropDownItem
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.RegularPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.FixedUpdateIntervals
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig

private const val MESSAGE_MAX_BYTES = 100
private val MIN_INTERVAL_SECS = FixedUpdateIntervals.ONE_HOUR.value.toInt()

private fun Int.withFlag(flag: Int, on: Boolean): Int = if (on) this or flag else this and flag.inv()

private fun Int.hasFlag(flag: Int): Boolean = (this and flag) != 0

/**
 * Editor for `ModuleConfig.MeshBeaconConfig` (design#140, Android issue #6931). Reads from the connect-time config sync
 * (there is no `ModuleConfigType` beacon value to request per-module) and writes via `AdminMessage.setModuleConfig`.
 * Flag edits are read-modify-write so unknown bits survive.
 *
 * The region and offered/transmit preset are never user-chosen here: the radio's own LoRa region and configured preset
 * are always stamped in on save (`stampBeaconConfigForSave`), so the beacon can never transmit region or preset
 * information the radio itself does not use. The repeated `broadcast_targets` list ([BroadcastTargetsCard]) is the only
 * way to name extra beacon destinations beyond the offered channel; an empty list sends one beacon on that channel
 * alone.
 */
@Suppress("LongMethod")
@Composable
fun MeshBeaconConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val meshBeaconConfig = state.moduleConfig.mesh_beacon ?: MeshBeaconConfig()
    val radioLora = state.radioConfig.lora

    if (radioLora == null || radioLora.region == RegionCode.UNSET) {
        RadioConfigScreenList(
            modifier = modifier,
            title = stringResource(Res.string.mesh_beacon),
            onBack = onBack,
            configState = rememberConfigState(initialValue = meshBeaconConfig),
            enabled = false,
            responseState = state.responseState,
            onDismissPacketResponse = viewModel::clearPacketResponse,
            onSave = {},
        ) {
            item {
                TitledCard(title = stringResource(Res.string.mesh_beacon)) {
                    Text(
                        text = stringResource(Res.string.mesh_beacon_region_required),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }

    val formState = rememberConfigState(initialValue = meshBeaconConfig)

    val listenFlag = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value
    val broadcastFlag = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
    // Only require a valid interval when broadcasting is actually on, otherwise a default (interval=0) config could
    // never be saved, blocking even a listen-only toggle.
    val broadcastEnabled = formState.value.flags.hasFlag(broadcastFlag)
    val intervalValid = !broadcastEnabled || formState.value.broadcast_interval_secs >= MIN_INTERVAL_SECS

    // Same region->preset legality reuse as LoRaConfigItemList (R7), but always constrained (design#140 behavior 2):
    // a beacon never falls back to the raw unconstrained preset list.
    val capabilities = remember(state.metadata?.firmware_version) { Capabilities(state.metadata?.firmware_version) }
    val regionPresetMap = if (capabilities.supportsLoraRegionPresetMap) state.loraRegionPresetMap else null
    val presetConstraint: RegionPresetConstraint =
        remember(regionPresetMap, radioLora.region) { beaconPresetConstraint(regionPresetMap, radioLora.region) }
    val presetsGated = presetConstraint.isGated(state.localIsLicensed)

    // Display labels reuse Channel.name's empty-primary-name -> preset display name conversion (e.g. "LongFast").
    val channelItems =
        remember(state.channelList, radioLora) {
            state.channelList.mapIndexed { index, settings -> DropDownItem(index, Channel(settings, radioLora).name) }
        }

    RadioConfigScreenList(
        modifier = modifier,
        title = stringResource(Res.string.mesh_beacon),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        // A beacon needs a channel to offer (behavior 3) and, when broadcasting, an interval of at least one hour.
        saveEnabled = state.connected && intervalValid && state.channelList.isNotEmpty(),
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            viewModel.setModuleConfig(
                ModuleConfig(mesh_beacon = stampBeaconConfigForSave(it, radioLora, state.channelList)),
            )
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.mesh_beacon)) {
                RegularPreference(
                    title = stringResource(Res.string.mesh_beacon_region_label),
                    subtitle = RegionInfo.fromRegionCode(radioLora.region)?.description.orEmpty(),
                    onClick = {},
                    enabled = false,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.mesh_beacon_listen),
                    summary = stringResource(Res.string.mesh_beacon_listen_summary),
                    checked = formState.value.flags.hasFlag(listenFlag),
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.copy(flags = formState.value.flags.withFlag(listenFlag, it))
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.mesh_beacon_broadcast),
                    summary = stringResource(Res.string.mesh_beacon_broadcast_summary),
                    checked = formState.value.flags.hasFlag(broadcastFlag),
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value =
                            formState.value.copy(flags = formState.value.flags.withFlag(broadcastFlag, it))
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.mesh_beacon_message),
                    value = formState.value.broadcast_message,
                    maxSize = MESSAGE_MAX_BYTES,
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions.Default,
                    onValueChanged = { formState.value = formState.value.copy(broadcast_message = it) },
                )
                HorizontalDivider()
                val intervalOptions = remember { IntervalConfiguration.MESH_BEACON_BROADCAST.allowedIntervals }
                val storedIntervalSecs = formState.value.broadcast_interval_secs.toLong()
                val intervalFallback =
                    remember(storedIntervalSecs, intervalOptions) {
                        beaconIntervalFallback(storedIntervalSecs, intervalOptions)
                    }
                val intervalItems =
                    intervalOptions.map { DropDownItem(value = it.value, label = it.toDisplayString()) } +
                        listOfNotNull(
                            intervalFallback?.let { fallbackSecs ->
                                DropDownItem(
                                    value = fallbackSecs,
                                    label =
                                    pluralStringResource(
                                        Res.plurals.plurals_seconds,
                                        fallbackSecs.toInt(),
                                        fallbackSecs.toInt(),
                                    ),
                                    enabled = false,
                                )
                            },
                        )
                DropDownPreference(
                    title = stringResource(Res.string.mesh_beacon_interval),
                    summary =
                    if (intervalValid) {
                        null
                    } else {
                        stringResource(Res.string.mesh_beacon_interval_error)
                    },
                    items = intervalItems,
                    selectedItem = storedIntervalSecs,
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(broadcast_interval_secs = it.toInt()) },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.mesh_beacon_offer_channel_name)) {
                val offerChannel = formState.value.broadcast_offer_channel
                val matchedIndex =
                    remember(offerChannel, state.channelList) {
                        beaconOfferChannelIndex(offerChannel, state.channelList)
                    }
                val offerItems =
                    if (offerChannel != null && matchedIndex == null) {
                        val staleLabel = offerChannel.name.ifBlank { Channel(offerChannel, radioLora).name }
                        channelItems + DropDownItem(value = -1, label = staleLabel, enabled = false)
                    } else {
                        channelItems
                    }
                val selectedChannelIndex = matchedIndex ?: if (offerChannel == null) 0 else -1
                DropDownPreference(
                    title = stringResource(Res.string.mesh_beacon_offer_channel_name),
                    items = offerItems,
                    selectedItem = selectedChannelIndex,
                    enabled = state.connected,
                    onItemSelected = { index ->
                        state.channelList.getOrNull(index)?.let { chosen ->
                            formState.value = formState.value.copy(broadcast_offer_channel = chosen)
                        }
                    },
                )
            }
        }
        item {
            BroadcastTargetsCard(
                targets = formState.value.broadcast_targets,
                enabled = state.connected,
                channelItems = channelItems,
                currentPreset = radioLora.modem_preset,
                presetConstraint = presetConstraint,
                presetsGated = presetsGated,
                capabilities = capabilities,
                onChange = { formState.value = formState.value.copy(broadcast_targets = it) },
            )
        }
    }
}

/**
 * Editor for the repeated `broadcast_targets` list: extra beacon destinations beyond the offered channel. Each row
 * picks one of the radio's own channels ([channelItems]) and a preset filtered by [presetConstraint] (design#140
 * behaviors 2 and 7); region is no longer a row concept (behavior 1), the radio's own region applies to every target.
 */
@Composable
private fun BroadcastTargetsCard(
    targets: List<MeshBeaconConfig.BroadcastTarget>,
    enabled: Boolean,
    channelItems: List<DropDownItem<Int>>,
    currentPreset: ModemPreset,
    presetConstraint: RegionPresetConstraint,
    presetsGated: Boolean,
    capabilities: Capabilities,
    onChange: (List<MeshBeaconConfig.BroadcastTarget>) -> Unit,
) {
    TitledCard(title = stringResource(Res.string.mesh_beacon_targets)) {
        targets.forEachIndexed { index, target ->
            if (index > 0) HorizontalDivider()
            Text(
                text = stringResource(Res.string.mesh_beacon_target, index + 1),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            val rowChannelIndex = target.channel_index ?: 0
            val rowChannelItems =
                if (channelItems.none { it.value == rowChannelIndex }) {
                    val fallback =
                        DropDownItem(value = rowChannelIndex, label = rowChannelIndex.toString(), enabled = false)
                    channelItems + fallback
                } else {
                    channelItems
                }
            DropDownPreference(
                title = stringResource(Res.string.mesh_beacon_target_channel_index),
                items = rowChannelItems,
                selectedItem = rowChannelIndex,
                enabled = enabled,
                onItemSelected = { channelIndex ->
                    onChange(
                        targets.mapIndexed { i, t ->
                            if (i == index) selectBeaconTargetChannel(t, channelIndex, currentPreset) else t
                        },
                    )
                },
            )
            val selectedPreset = target.preset ?: presetConstraint.defaultPreset
            val presetItems =
                remember(presetConstraint, presetsGated, selectedPreset, capabilities) {
                    buildPresetItems(presetConstraint, presetsGated, selectedPreset, capabilities)
                }
            DropDownPreference(
                title = stringResource(Res.string.mesh_beacon_on_preset),
                items = presetItems,
                selectedItem = selectedPreset,
                enabled = enabled,
                onItemSelected = { sel ->
                    onChange(targets.mapIndexed { i, t -> if (i == index) t.copy(preset = sel) else t })
                },
            )
            TextButton(onClick = { onChange(targets.filterIndexed { i, _ -> i != index }) }, enabled = enabled) {
                Text(stringResource(Res.string.mesh_beacon_target_remove))
            }
        }
        HorizontalDivider()
        TextButton(onClick = { onChange(targets + MeshBeaconConfig.BroadcastTarget()) }, enabled = enabled) {
            Text(stringResource(Res.string.mesh_beacon_target_add))
        }
    }
}
