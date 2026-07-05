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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okio.ByteString.Companion.decodeBase64
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_beacon
import org.meshtastic.core.resources.mesh_beacon_broadcast
import org.meshtastic.core.resources.mesh_beacon_broadcast_summary
import org.meshtastic.core.resources.mesh_beacon_interval
import org.meshtastic.core.resources.mesh_beacon_interval_error
import org.meshtastic.core.resources.mesh_beacon_listen
import org.meshtastic.core.resources.mesh_beacon_listen_summary
import org.meshtastic.core.resources.mesh_beacon_message
import org.meshtastic.core.resources.mesh_beacon_offer_channel_key
import org.meshtastic.core.resources.mesh_beacon_offer_channel_name
import org.meshtastic.core.resources.mesh_beacon_offer_preset_setting
import org.meshtastic.core.resources.mesh_beacon_offer_region_setting
import org.meshtastic.core.resources.mesh_beacon_on_preset
import org.meshtastic.core.resources.mesh_beacon_on_region
import org.meshtastic.core.resources.mesh_beacon_send_as_node
import org.meshtastic.core.resources.mesh_beacon_target_add
import org.meshtastic.core.resources.mesh_beacon_target_channel_index
import org.meshtastic.core.resources.mesh_beacon_target_remove
import org.meshtastic.core.resources.mesh_beacon_targets
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig

private const val MESSAGE_MAX_BYTES = 100
private const val MIN_INTERVAL_SECS = 3600

private fun Int.withFlag(flag: Int, on: Boolean): Int = if (on) this or flag else this and flag.inv()

private fun Int.hasFlag(flag: Int): Boolean = (this and flag) != 0

/**
 * Editor for `ModuleConfig.MeshBeaconConfig` (Apple 014-mesh-beacons US2 / FR-009–FR-014). Reads from the connect-time
 * config sync (there is no `ModuleConfigType` beacon value to request per-module) and writes via
 * `AdminMessage.setModuleConfig`. Flag edits are read-modify-write so `FLAG_LEGACY_SPLIT` and any unknown bits survive.
 *
 * The repeated `broadcast_targets` list is editable ([BroadcastTargetsCard]); the single-target `broadcast_on_*`
 * scalars are shown only as the fallback when no targets are set (Apple parity). `broadcast_on_channel` is preserved
 * verbatim through the Wire `copy()` round-trip (no scalar editor — the targets list supersedes it).
 */
@Suppress("LongMethod")
@Composable
fun MeshBeaconConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val meshBeaconConfig = state.moduleConfig.mesh_beacon ?: MeshBeaconConfig()
    val formState = rememberConfigState(initialValue = meshBeaconConfig)

    val listenFlag = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value
    val broadcastFlag = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
    val intervalValid = formState.value.broadcast_interval_secs >= MIN_INTERVAL_SECS

    RadioConfigScreenList(
        modifier = modifier,
        title = stringResource(Res.string.mesh_beacon),
        onBack = onBack,
        configState = formState,
        // Block saving an out-of-range interval (FR-013); message length is capped inline by the field itself.
        enabled = state.connected && intervalValid,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            // Drop the offered channel when no name is set (a text-only beacon offers no channel).
            val offered = it.broadcast_offer_channel?.takeIf { c -> c.name.isNotBlank() }
            viewModel.setModuleConfig(ModuleConfig(mesh_beacon = it.copy(broadcast_offer_channel = offered)))
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.mesh_beacon)) {
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
                EditTextPreference(
                    title = stringResource(Res.string.mesh_beacon_interval),
                    value = formState.value.broadcast_interval_secs,
                    enabled = state.connected,
                    isError = !intervalValid,
                    keyboardActions = KeyboardActions.Default,
                    summary =
                    if (intervalValid) {
                        null
                    } else {
                        stringResource(Res.string.mesh_beacon_interval_error, MIN_INTERVAL_SECS)
                    },
                    onValueChanged = { formState.value = formState.value.copy(broadcast_interval_secs = it) },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.mesh_beacon_offer_channel_name)) {
                EditTextPreference(
                    title = stringResource(Res.string.mesh_beacon_offer_channel_name),
                    value = formState.value.broadcast_offer_channel?.name.orEmpty(),
                    maxSize = MESSAGE_MAX_BYTES,
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions.Default,
                    onValueChanged = {
                        val current = formState.value.broadcast_offer_channel ?: ChannelSettings()
                        formState.value = formState.value.copy(broadcast_offer_channel = current.copy(name = it))
                    },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.mesh_beacon_offer_channel_key),
                    value = formState.value.broadcast_offer_channel?.psk?.base64().orEmpty(),
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions.Default,
                    onValueChanged = { encoded ->
                        val psk = encoded.decodeBase64() ?: return@EditTextPreference
                        val current = formState.value.broadcast_offer_channel ?: ChannelSettings()
                        formState.value = formState.value.copy(broadcast_offer_channel = current.copy(psk = psk))
                    },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.mesh_beacon_offer_region_setting),
                    selectedItem = formState.value.broadcast_offer_region,
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(broadcast_offer_region = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.mesh_beacon_offer_preset_setting),
                    selectedItem = formState.value.broadcast_offer_preset ?: ModemPreset.LONG_FAST,
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(broadcast_offer_preset = it) },
                )
            }
        }
        item {
            BroadcastTargetsCard(
                targets = formState.value.broadcast_targets,
                enabled = state.connected,
                onChange = { formState.value = formState.value.copy(broadcast_targets = it) },
            )
        }
        item {
            TitledCard(title = stringResource(Res.string.mesh_beacon_on_region)) {
                // Single-target transmit scalars are the fallback used only when no multi-target list is set (Apple
                // shows these two only when broadcast_targets is empty). broadcast_send_as_node applies either way.
                if (formState.value.broadcast_targets.isEmpty()) {
                    DropDownPreference(
                        title = stringResource(Res.string.mesh_beacon_on_region),
                        selectedItem = formState.value.broadcast_on_region,
                        enabled = state.connected,
                        onItemSelected = { formState.value = formState.value.copy(broadcast_on_region = it) },
                    )
                    HorizontalDivider()
                    DropDownPreference(
                        title = stringResource(Res.string.mesh_beacon_on_preset),
                        selectedItem = formState.value.broadcast_on_preset ?: ModemPreset.LONG_FAST,
                        enabled = state.connected,
                        onItemSelected = { formState.value = formState.value.copy(broadcast_on_preset = it) },
                    )
                    HorizontalDivider()
                }
                SignedIntegerEditTextPreference(
                    title = stringResource(Res.string.mesh_beacon_send_as_node),
                    value = formState.value.broadcast_send_as_node,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions.Default,
                    onValueChanged = { formState.value = formState.value.copy(broadcast_send_as_node = it) },
                )
            }
        }
    }
}

/**
 * Editor for the repeated `broadcast_targets` list (Apple FR-014 multi-target). Each target carries its own
 * region/preset and an optional `channel_index`; rows can be added and removed. When the list is non-empty it
 * supersedes the single-target `broadcast_on_*` scalars.
 */
@Composable
private fun BroadcastTargetsCard(
    targets: List<MeshBeaconConfig.BroadcastTarget>,
    enabled: Boolean,
    onChange: (List<MeshBeaconConfig.BroadcastTarget>) -> Unit,
) {
    TitledCard(title = stringResource(Res.string.mesh_beacon_targets)) {
        targets.forEachIndexed { index, target ->
            if (index > 0) HorizontalDivider()
            DropDownPreference(
                title = stringResource(Res.string.mesh_beacon_on_region),
                selectedItem = target.region,
                enabled = enabled,
                onItemSelected = { sel ->
                    onChange(targets.mapIndexed { i, t -> if (i == index) t.copy(region = sel) else t })
                },
            )
            DropDownPreference(
                title = stringResource(Res.string.mesh_beacon_on_preset),
                selectedItem = target.preset ?: ModemPreset.LONG_FAST,
                enabled = enabled,
                onItemSelected = { sel ->
                    onChange(targets.mapIndexed { i, t -> if (i == index) t.copy(preset = sel) else t })
                },
            )
            SignedIntegerEditTextPreference(
                title = stringResource(Res.string.mesh_beacon_target_channel_index),
                value = target.channel_index ?: 0,
                enabled = enabled,
                keyboardActions = KeyboardActions.Default,
                onValueChanged = { v ->
                    onChange(targets.mapIndexed { i, t -> if (i == index) t.copy(channel_index = v) else t })
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
