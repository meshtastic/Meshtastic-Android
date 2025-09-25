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
fun StoreForwardConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val storeForwardConfig = state.moduleConfig.storeForward
    val formState = rememberConfigState(initialValue = storeForwardConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.store_forward),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { storeForward = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.store_forward_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.store_forward_enabled),
                checked = formState.value.enabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.heartbeat),
                checked = formState.value.heartbeat,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { heartbeat = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.number_of_records),
                value = formState.value.records,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { records = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.history_return_max),
                value = formState.value.historyReturnMax,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { historyReturnMax = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.history_return_window),
                value = formState.value.historyReturnWindow,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { historyReturnWindow = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.server),
                checked = formState.value.isServer,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { isServer = it } },
            )
        }
        item { HorizontalDivider() }
    }
}
