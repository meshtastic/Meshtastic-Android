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

import androidx.compose.foundation.layout.Column
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
import org.meshtastic.core.resources.config_lora_modem_preset_licensed_summary
import org.meshtastic.core.resources.mesh_beacon
import org.meshtastic.core.resources.mesh_beacon_broadcast
import org.meshtastic.core.resources.mesh_beacon_broadcast_requires_preset
import org.meshtastic.core.resources.mesh_beacon_broadcast_summary
import org.meshtastic.core.resources.mesh_beacon_channel_number
import org.meshtastic.core.resources.mesh_beacon_interval
import org.meshtastic.core.resources.mesh_beacon_interval_error
import org.meshtastic.core.resources.mesh_beacon_listen
import org.meshtastic.core.resources.mesh_beacon_listen_summary
import org.meshtastic.core.resources.mesh_beacon_message
import org.meshtastic.core.resources.mesh_beacon_no_channels
import org.meshtastic.core.resources.mesh_beacon_offer_channel_name
import org.meshtastic.core.resources.mesh_beacon_on_preset
import org.meshtastic.core.resources.mesh_beacon_region_label
import org.meshtastic.core.resources.mesh_beacon_region_required
import org.meshtastic.core.resources.mesh_beacon_target
import org.meshtastic.core.resources.mesh_beacon_target_add
import org.meshtastic.core.resources.mesh_beacon_target_channel_index
import org.meshtastic.core.resources.mesh_beacon_target_default
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
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig

private const val MESSAGE_MAX_BYTES = 100
private val MIN_INTERVAL_SECS = FixedUpdateIntervals.ONE_HOUR.value.toInt()

// Sentinel DropDownItem values (design#140's never-render-blank rule): -1 marks a stale stored value that no longer
// matches a radio channel, -2 marks an explicit "no channels" placeholder row. Never a real channel_index.
private const val NO_CHANNELS_ITEM_VALUE = -2

private fun Int.withFlag(flag: Int, on: Boolean): Int = if (on) this or flag else this and flag.inv()

private fun Int.hasFlag(flag: Int): Boolean = (this and flag) != 0

/**
 * Editor for `ModuleConfig.MeshBeaconConfig` (design#140, Android issue #6931). Reads from the connect-time config sync
 * (there is no `ModuleConfigType` beacon value to request per-module) and writes via `AdminMessage.setModuleConfig`.
 * Flag edits are read-modify-write so unknown bits survive.
 *
 * The region and offered/transmit preset are never user-chosen here: the radio's own LoRa region and configured preset
 * are always stamped in on save (`stampBeaconConfigForSave`), so the beacon can never transmit region or preset
 * information the radio itself does not use. `broadcast_offer_*` is the invitation payload content shown to listeners
 * (what channel/preset they could join); the repeated `broadcast_targets` list ([BroadcastTargetsCard]) is the
 * separate, only, TX destination list -- which radio settings the beacon packet itself is actually transmitted on.
 * Firmware sends one beacon on the node's running preset and primary channel when this list is empty
 * (`MeshBeaconModule.cpp::sendBeacon`), so the editor seeds and floors the list at one row ([seedBeaconTargets],
 * [removeBeaconTarget]) rather than ever showing zero rows -- design#140 behavior 6 keeps that implicit default visible
 * and editable instead of hidden.
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

    val formState = rememberConfigState(initialValue = initialBeaconFormState(meshBeaconConfig))

    val listenFlag = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value
    val broadcastFlag = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
    // Only require a valid interval when broadcasting is actually on, otherwise a default (interval=0) config could
    // never be saved, blocking even a listen-only toggle.
    val broadcastEnabled = formState.value.flags.hasFlag(broadcastFlag)
    val intervalValid = !broadcastEnabled || formState.value.broadcast_interval_secs >= MIN_INTERVAL_SECS

    // Gates the BROADCAST half of the editor when the radio uses custom LoRa params (design#140 Q1): stamping a
    // stale modem_preset on save would mint a live on-air lie.
    val storedBroadcastEnabled = meshBeaconConfig.flags.hasFlag(broadcastFlag)
    val broadcastGate =
        remember(state.connected, radioLora.use_preset, storedBroadcastEnabled) {
            beaconBroadcastGate(state.connected, radioLora.use_preset, storedBroadcastEnabled)
        }

    // Same region->preset legality reuse as LoRaConfigItemList (R7), but always constrained (design#140 behavior 2):
    // a beacon never falls back to the raw unconstrained preset list.
    val capabilities = remember(state.metadata?.firmware_version) { Capabilities(state.metadata?.firmware_version) }
    val regionPresetMap = if (capabilities.supportsLoraRegionPresetMap) state.loraRegionPresetMap else null
    val presetConstraint: RegionPresetConstraint =
        remember(regionPresetMap, radioLora.region) { beaconPresetConstraint(regionPresetMap, radioLora.region) }
    val presetsGated = presetConstraint.isGated(state.localIsLicensed)

    // Placeholder secondary slots are excluded (design#140 Q2); the primary is always kept, even blank, since a
    // blank-name/zero-psk primary is a legal cleartext channel, not padding.
    val selectableChannels = remember(state.channelList) { selectableBeaconChannels(state.channelList) }
    // Display labels reuse Channel.name's empty-primary-name -> preset display name conversion (e.g. "LongFast").
    val channelItems =
        remember(selectableChannels, radioLora) {
            selectableChannels.map { (index, settings) -> DropDownItem(index, Channel(settings, radioLora).name) }
        }

    RadioConfigScreenList(
        modifier = modifier,
        title = stringResource(Res.string.mesh_beacon),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        // A beacon needs a channel to offer (behavior 3) and, when broadcasting, an interval of at least one hour;
        // both are moot when the broadcast half is gated off (design#140 Q1), since save then preserves the stored
        // broadcast fields verbatim.
        saveEnabled =
        meshBeaconSaveEnabled(state.connected, radioLora.use_preset, intervalValid, state.channelList.isNotEmpty()),
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val stamped = stampBeaconConfigForSave(it, meshBeaconConfig, radioLora, state.channelList)
            viewModel.setModuleConfig(ModuleConfig(mesh_beacon = stamped))
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.mesh_beacon)) {
                RegularPreference(
                    title = stringResource(Res.string.mesh_beacon_region_label),
                    subtitle =
                    RegionInfo.fromRegionCode(radioLora.region)?.description
                        ?: radioLora.region.name.replace('_', ' '),
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
                    enabled = broadcastGate.toggleEnabled,
                    onCheckedChange = {
                        formState.value =
                            formState.value.copy(flags = formState.value.flags.withFlag(broadcastFlag, it))
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (!radioLora.use_preset) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(Res.string.mesh_beacon_broadcast_requires_preset),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (broadcastGate.sectionsVisible) {
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.mesh_beacon_message),
                        value = formState.value.broadcast_message,
                        maxSize = MESSAGE_MAX_BYTES,
                        enabled = broadcastGate.sectionsEnabled,
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
                        enabled = broadcastGate.sectionsEnabled,
                        onItemSelected = {
                            formState.value = formState.value.copy(broadcast_interval_secs = it.toInt())
                        },
                    )
                }
            }
        }
        if (broadcastGate.sectionsVisible) {
            item {
                TitledCard(title = stringResource(Res.string.mesh_beacon_offer_channel_name)) {
                    OfferChannelPreference(
                        offerChannel = formState.value.broadcast_offer_channel,
                        channelList = state.channelList,
                        selectableChannels = selectableChannels,
                        radioLora = radioLora,
                        channelItems = channelItems,
                        enabled = broadcastGate.sectionsEnabled,
                        onChannelSelect = { chosen ->
                            formState.value = formState.value.copy(broadcast_offer_channel = chosen)
                        },
                    )
                }
            }
            item {
                BroadcastTargetsCard(
                    targets = formState.value.broadcast_targets,
                    enabled = broadcastGate.sectionsEnabled,
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
}

/**
 * Picker for the required offered channel (design#140 behavior 3): one of the radio's own channels, defaulting to the
 * primary when unset. Placeholder slots are excluded (design#140 Q2, [selectableChannels]); a stored channel the radio
 * no longer has (or that only matches a placeholder) is kept selected as a disabled fallback item, and an empty channel
 * list shows an explicit disabled placeholder row, so the picker never renders blank.
 */
@Composable
internal fun OfferChannelPreference(
    offerChannel: ChannelSettings?,
    channelList: List<ChannelSettings>,
    selectableChannels: List<Pair<Int, ChannelSettings>>,
    radioLora: Config.LoRaConfig,
    channelItems: List<DropDownItem<Int>>,
    enabled: Boolean,
    onChannelSelect: (ChannelSettings) -> Unit,
) {
    val matchedIndex =
        remember(offerChannel, selectableChannels) { beaconOfferChannelIndex(offerChannel, selectableChannels) }
    val noChannels = channelItems.isEmpty()
    val offerItems =
        when {
            offerChannel != null && matchedIndex == null -> {
                val staleLabel = offerChannel.name.ifBlank { Channel(offerChannel, radioLora).name }
                channelItems + DropDownItem(value = -1, label = staleLabel, enabled = false)
            }

            noChannels ->
                listOf(
                    DropDownItem(
                        value = NO_CHANNELS_ITEM_VALUE,
                        label = stringResource(Res.string.mesh_beacon_no_channels),
                        enabled = false,
                    ),
                )

            else -> channelItems
        }
    val selectedChannelIndex =
        when {
            matchedIndex != null -> matchedIndex
            offerChannel != null -> -1
            noChannels -> NO_CHANNELS_ITEM_VALUE
            else -> 0
        }
    DropDownPreference(
        title = stringResource(Res.string.mesh_beacon_offer_channel_name),
        items = offerItems,
        selectedItem = selectedChannelIndex,
        enabled = enabled,
        onItemSelected = { index -> channelList.getOrNull(index)?.let(onChannelSelect) },
    )
}

/**
 * Editor for the repeated `broadcast_targets` list: the beacon's actual TX destinations (design#140 behavior 6). Each
 * row picks one of the radio's own channels ([channelItems]) or the "Default" sentinel, and a preset filtered by
 * [presetConstraint] (design#140 behaviors 2 and 7) or "Default"; region is no longer a row concept (behavior 1), the
 * radio's own region applies to every target. Internal (not private): unit-testable directly, mirroring
 * [OfferChannelPreference].
 */
@Composable
internal fun BroadcastTargetsCard(
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
            BroadcastTargetRow(
                index = index,
                target = target,
                enabled = enabled,
                channelItems = channelItems,
                currentPreset = currentPreset,
                presetConstraint = presetConstraint,
                presetsGated = presetsGated,
                capabilities = capabilities,
                onChange = { updated -> onChange(targets.mapIndexed { i, t -> if (i == index) updated(t) else t }) },
                onRemove = { onChange(removeBeaconTarget(targets, index)) },
            )
        }
        HorizontalDivider()
        TextButton(onClick = { onChange(targets + MeshBeaconConfig.BroadcastTarget()) }, enabled = enabled) {
            Text(stringResource(Res.string.mesh_beacon_target_add))
        }
    }
}

/**
 * One row of [BroadcastTargetsCard]: a channel picker (fallback-safe, design#140 Q2) and a constrained preset picker.
 */
@Composable
private fun BroadcastTargetRow(
    index: Int,
    target: MeshBeaconConfig.BroadcastTarget,
    enabled: Boolean,
    channelItems: List<DropDownItem<Int>>,
    currentPreset: ModemPreset,
    presetConstraint: RegionPresetConstraint,
    presetsGated: Boolean,
    capabilities: Capabilities,
    onChange: (transform: (MeshBeaconConfig.BroadcastTarget) -> MeshBeaconConfig.BroadcastTarget) -> Unit,
    onRemove: () -> Unit,
) = Column {
    Text(
        text = stringResource(Res.string.mesh_beacon_target, index + 1),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
    )
    // Nullable value type throughout (design#140 behavior 6): `null` is the "Default" sentinel, matching the wire
    // format where an unset channel_index/preset falls back to the running config (module_config.proto's
    // BroadcastTarget). A row is never forced to a resolved value the way it was before this row supported "Default".
    val defaultLabel = stringResource(Res.string.mesh_beacon_target_default)
    val rowChannelIndex = target.channel_index
    val nullableChannelItems: List<DropDownItem<Int?>> =
        listOf(DropDownItem<Int?>(value = null, label = defaultLabel)) +
            channelItems.map { DropDownItem<Int?>(it.value, it.label, it.icon, it.color, it.enabled, it.testTag) }
    val rowChannelItems =
        if (rowChannelIndex != null && nullableChannelItems.none { it.value == rowChannelIndex }) {
            nullableChannelItems +
                DropDownItem<Int?>(
                    value = rowChannelIndex,
                    label = stringResource(Res.string.mesh_beacon_channel_number, rowChannelIndex),
                    enabled = false,
                )
        } else {
            nullableChannelItems
        }
    DropDownPreference(
        title = stringResource(Res.string.mesh_beacon_target_channel_index),
        items = rowChannelItems,
        selectedItem = rowChannelIndex,
        enabled = enabled,
        onItemSelected = { channelIndex -> onChange { selectBeaconTargetChannel(it, channelIndex, currentPreset) } },
    )
    val rowPreset = target.preset
    val presetItems =
        remember(presetConstraint, presetsGated, rowPreset, capabilities) {
            buildPresetItems(presetConstraint, presetsGated, rowPreset ?: presetConstraint.defaultPreset, capabilities)
        }
    val nullablePresetItems: List<DropDownItem<ModemPreset?>> =
        listOf(DropDownItem<ModemPreset?>(value = null, label = defaultLabel)) +
            presetItems.map {
                DropDownItem<ModemPreset?>(it.value, it.label, it.icon, it.color, it.enabled, it.testTag)
            }
    val presetSummary = if (presetsGated) stringResource(Res.string.config_lora_modem_preset_licensed_summary) else null
    DropDownPreference(
        title = stringResource(Res.string.mesh_beacon_on_preset),
        summary = presetSummary,
        items = nullablePresetItems,
        selectedItem = rowPreset,
        enabled = enabled,
        onItemSelected = { sel -> onChange { it.copy(preset = sel) } },
    )
    TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(Res.string.mesh_beacon_target_remove)) }
}
