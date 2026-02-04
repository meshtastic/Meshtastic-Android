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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.ble_rssi_threshold_defaults_to_80
import org.meshtastic.core.strings.paxcounter
import org.meshtastic.core.strings.paxcounter_config
import org.meshtastic.core.strings.paxcounter_enabled
import org.meshtastic.core.strings.update_interval_seconds
import org.meshtastic.core.strings.wifi_rssi_threshold_defaults_to_80
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.ModuleConfig

@Composable
fun PaxcounterConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val paxcounterConfig = state.moduleConfig.paxcounter ?: ModuleConfig.PaxcounterConfig()
    val formState = rememberConfigState(initialValue = paxcounterConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.paxcounter),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(paxcounter = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.paxcounter_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.paxcounter_enabled),
                    checked = formState.value.enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val items = remember { IntervalConfiguration.PAX_COUNTER.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.update_interval_seconds),
                    selectedItem = (formState.value.paxcounter_update_interval ?: 0).toLong(),
                    enabled = state.connected,
                    items = items.map { it.value to it.toDisplayString() },
                    onItemSelected = {
                        formState.value = formState.value.copy(paxcounter_update_interval = it.toInt())
                    },
                )
                HorizontalDivider()
                SignedIntegerEditTextPreference(
                    title = stringResource(Res.string.wifi_rssi_threshold_defaults_to_80),
                    value = formState.value.wifi_threshold ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(wifi_threshold = it) },
                )
                HorizontalDivider()
                SignedIntegerEditTextPreference(
                    title = stringResource(Res.string.ble_rssi_threshold_defaults_to_80),
                    value = formState.value.ble_threshold ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(ble_threshold = it) },
                )
            }
        }
    }
}
