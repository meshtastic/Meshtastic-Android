/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.config_device_transmitOverLora_summary
import org.meshtastic.core.strings.neighbor_info
import org.meshtastic.core.strings.neighbor_info_config
import org.meshtastic.core.strings.neighbor_info_enabled
import org.meshtastic.core.strings.transmit_over_lora
import org.meshtastic.core.strings.update_interval_seconds
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Composable
fun NeighborInfoConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val neighborInfoConfig = state.moduleConfig.neighbor_info ?: ModuleConfig.NeighborInfoConfig()
    val formState = rememberConfigState(initialValue = neighborInfoConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.neighbor_info),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(neighbor_info = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.neighbor_info_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.neighbor_info_enabled),
                    checked = formState.value.enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.update_interval_seconds),
                    value = formState.value.update_interval ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(update_interval = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.transmit_over_lora),
                    summary = stringResource(Res.string.config_device_transmitOverLora_summary),
                    checked = formState.value.transmit_over_lora ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(transmit_over_lora = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
