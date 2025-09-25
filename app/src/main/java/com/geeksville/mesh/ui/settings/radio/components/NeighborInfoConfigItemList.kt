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

package com.geeksville.mesh.ui.settings.radio.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R

@Composable
fun NeighborInfoConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val neighborInfoConfig = state.moduleConfig.neighborInfo
    val formState = rememberConfigState(initialValue = neighborInfoConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.neighbor_info),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { neighborInfo = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.neighbor_info_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.neighbor_info_enabled),
                checked = formState.value.enabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.update_interval_seconds),
                value = formState.value.updateInterval,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { updateInterval = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.transmit_over_lora),
                summary = stringResource(id = R.string.config_device_transmitOverLora_summary),
                checked = formState.value.transmitOverLora,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { transmitOverLora = it } },
            )
            HorizontalDivider()
        }
    }
}
