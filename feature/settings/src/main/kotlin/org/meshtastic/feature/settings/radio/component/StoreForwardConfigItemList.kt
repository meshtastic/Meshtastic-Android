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
import org.meshtastic.core.strings.heartbeat
import org.meshtastic.core.strings.history_return_max
import org.meshtastic.core.strings.history_return_window
import org.meshtastic.core.strings.number_of_records
import org.meshtastic.core.strings.server
import org.meshtastic.core.strings.store_forward
import org.meshtastic.core.strings.store_forward_config
import org.meshtastic.core.strings.store_forward_enabled
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Composable
fun StoreForwardConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val storeForwardConfig = state.moduleConfig.store_forward ?: ModuleConfig.StoreForwardConfig()
    val formState = rememberConfigState(initialValue = storeForwardConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.store_forward),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(store_forward = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.store_forward_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.store_forward_enabled),
                    checked = formState.value.enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.heartbeat),
                    checked = formState.value.heartbeat ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(heartbeat = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.number_of_records),
                    value = formState.value.records ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(records = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.history_return_max),
                    value = formState.value.history_return_max ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(history_return_max = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.history_return_window),
                    value = formState.value.history_return_window ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(history_return_window = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.server),
                    checked = formState.value.is_server ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(is_server = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
