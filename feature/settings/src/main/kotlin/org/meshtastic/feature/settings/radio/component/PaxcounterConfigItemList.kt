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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.meshtastic.core.model.FixedUpdateIntervals
import org.meshtastic.core.model.IntervalConfiguration
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.component.SliderPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

private fun FixedUpdateIntervals.toDisplayString(): String =
    name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

@Composable
fun PaxcounterConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val paxcounterConfig = state.moduleConfig.paxcounter
    val formState = rememberConfigState(initialValue = paxcounterConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.paxcounter),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { paxcounter = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.paxcounter_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.paxcounter_enabled),
                checked = formState.value.enabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            val items = remember { IntervalConfiguration.PAX_COUNTER.allowedIntervals }
            SliderPreference(
                title = stringResource(R.string.update_interval_seconds),
                selectedValue = formState.value.paxcounterUpdateInterval.toLong(),
                enabled = state.connected,
                items = items.map { it.value to it.toDisplayString() },
                onValueChange = { formState.value = formState.value.copy { paxcounterUpdateInterval = it.toInt() } },
            )
        }

        item {
            SignedIntegerEditTextPreference(
                title = stringResource(R.string.wifi_rssi_threshold_defaults_to_80),
                value = formState.value.wifiThreshold,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { wifiThreshold = it } },
            )
        }

        item {
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
