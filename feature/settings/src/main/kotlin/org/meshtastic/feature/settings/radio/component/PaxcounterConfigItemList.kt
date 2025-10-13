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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun PaxcounterConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val paxcounterConfig = state.moduleConfig.paxcounter
    val formState = rememberConfigState(initialValue = paxcounterConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.paxcounter),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { paxcounter = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(R.string.paxcounter_config)) {
                SwitchPreference(
                    title = stringResource(R.string.paxcounter_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val items = remember { IntervalConfiguration.PAX_COUNTER.allowedIntervals }
                DropDownPreference(
                    title = stringResource(R.string.update_interval_seconds),
                    selectedItem = formState.value.paxcounterUpdateInterval.toLong(),
                    enabled = state.connected,
                    items = items.map { it.value to it.toDisplayString() },
                    onItemSelected = {
                        formState.value = formState.value.copy { paxcounterUpdateInterval = it.toInt() }
                    },
                )
                HorizontalDivider()
                SignedIntegerEditTextPreference(
                    title = stringResource(R.string.wifi_rssi_threshold_defaults_to_80),
                    value = formState.value.wifiThreshold,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { wifiThreshold = it } },
                )
                HorizontalDivider()
                SignedIntegerEditTextPreference(
                    title = stringResource(R.string.ble_rssi_threshold_defaults_to_80),
                    value = formState.value.bleThreshold,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { bleThreshold = it } },
                )
            }
        }
    }
}
