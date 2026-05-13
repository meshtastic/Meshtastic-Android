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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.traffic_management
import org.meshtastic.core.resources.traffic_management_config
import org.meshtastic.core.resources.traffic_management_drop_unknown_enabled
import org.meshtastic.core.resources.traffic_management_enabled
import org.meshtastic.core.resources.traffic_management_exhaust_hop_position
import org.meshtastic.core.resources.traffic_management_exhaust_hop_telemetry
import org.meshtastic.core.resources.traffic_management_nodeinfo_direct_response
import org.meshtastic.core.resources.traffic_management_nodeinfo_direct_response_max_hops
import org.meshtastic.core.resources.traffic_management_position_dedup
import org.meshtastic.core.resources.traffic_management_position_min_interval
import org.meshtastic.core.resources.traffic_management_position_precision
import org.meshtastic.core.resources.traffic_management_rate_limit_enabled
import org.meshtastic.core.resources.traffic_management_rate_limit_max_packets
import org.meshtastic.core.resources.traffic_management_rate_limit_window
import org.meshtastic.core.resources.traffic_management_router_preserve_hops
import org.meshtastic.core.resources.traffic_management_unknown_packet_threshold
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Suppress("LongMethod")
@Composable
fun TrafficManagementConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val tmConfig = state.moduleConfig.traffic_management ?: ModuleConfig.TrafficManagementConfig()
    val formState = rememberConfigState(initialValue = tmConfig)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(tmConfig) { formState.value = tmConfig }

    RadioConfigScreenList(
        title = stringResource(Res.string.traffic_management),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(traffic_management = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.traffic_management_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_position_dedup),
                    checked = formState.value.position_dedup_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(position_dedup_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.traffic_management_position_precision),
                    value = formState.value.position_precision_bits,
                    enabled = state.connected,
                    keyboardActions =
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                    ),
                    onValueChanged = { formState.value = formState.value.copy(position_precision_bits = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.traffic_management_position_min_interval),
                    value = formState.value.position_min_interval_secs,
                    enabled = state.connected,
                    keyboardActions =
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                    ),
                    onValueChanged = { formState.value = formState.value.copy(position_min_interval_secs = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_nodeinfo_direct_response),
                    checked = formState.value.nodeinfo_direct_response,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(nodeinfo_direct_response = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.traffic_management_nodeinfo_direct_response_max_hops),
                    value = formState.value.nodeinfo_direct_response_max_hops,
                    enabled = state.connected,
                    keyboardActions =
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                    ),
                    onValueChanged = { formState.value = formState.value.copy(nodeinfo_direct_response_max_hops = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_rate_limit_enabled),
                    checked = formState.value.rate_limit_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(rate_limit_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.traffic_management_rate_limit_window),
                    value = formState.value.rate_limit_window_secs,
                    enabled = state.connected,
                    keyboardActions =
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                    ),
                    onValueChanged = { formState.value = formState.value.copy(rate_limit_window_secs = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.traffic_management_rate_limit_max_packets),
                    value = formState.value.rate_limit_max_packets,
                    enabled = state.connected,
                    keyboardActions =
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                    ),
                    onValueChanged = { formState.value = formState.value.copy(rate_limit_max_packets = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_drop_unknown_enabled),
                    checked = formState.value.drop_unknown_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(drop_unknown_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.traffic_management_unknown_packet_threshold),
                    value = formState.value.unknown_packet_threshold,
                    enabled = state.connected,
                    keyboardActions =
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                    ),
                    onValueChanged = { formState.value = formState.value.copy(unknown_packet_threshold = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_exhaust_hop_telemetry),
                    checked = formState.value.exhaust_hop_telemetry,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(exhaust_hop_telemetry = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_exhaust_hop_position),
                    checked = formState.value.exhaust_hop_position,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(exhaust_hop_position = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.traffic_management_router_preserve_hops),
                    checked = formState.value.router_preserve_hops,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(router_preserve_hops = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
