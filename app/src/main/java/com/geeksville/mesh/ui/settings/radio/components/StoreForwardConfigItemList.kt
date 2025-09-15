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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.StoreForwardConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel

@Composable
fun StoreForwardConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    StoreForwardConfigItemList(
        storeForwardConfig = state.moduleConfig.storeForward,
        enabled = state.connected,
        onSaveClicked = { storeForwardInput ->
            val config = moduleConfig { storeForward = storeForwardInput }
            viewModel.setModuleConfig(config)
        },
    )
}

@Composable
fun StoreForwardConfigItemList(
    storeForwardConfig: StoreForwardConfig,
    enabled: Boolean,
    onSaveClicked: (StoreForwardConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var storeForwardInput by rememberSaveable { mutableStateOf(storeForwardConfig) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceCategory(text = stringResource(R.string.store_forward_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.store_forward_enabled),
                checked = storeForwardInput.enabled,
                enabled = enabled,
                onCheckedChange = { storeForwardInput = storeForwardInput.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.heartbeat),
                checked = storeForwardInput.heartbeat,
                enabled = enabled,
                onCheckedChange = { storeForwardInput = storeForwardInput.copy { heartbeat = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.number_of_records),
                value = storeForwardInput.records,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { storeForwardInput = storeForwardInput.copy { records = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.history_return_max),
                value = storeForwardInput.historyReturnMax,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { storeForwardInput = storeForwardInput.copy { historyReturnMax = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.history_return_window),
                value = storeForwardInput.historyReturnWindow,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { storeForwardInput = storeForwardInput.copy { historyReturnWindow = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.server),
                checked = storeForwardInput.isServer,
                enabled = enabled,
                onCheckedChange = { storeForwardInput = storeForwardInput.copy { isServer = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            PreferenceFooter(
                enabled = enabled && storeForwardInput != storeForwardConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    storeForwardInput = storeForwardConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(storeForwardInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StoreForwardConfigPreview() {
    StoreForwardConfigItemList(
        storeForwardConfig = StoreForwardConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = {},
    )
}
