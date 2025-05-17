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

package com.geeksville.mesh.ui.radioconfig.components

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.RemoteHardwareConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.components.EditListPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel

@Composable
fun RemoteHardwareConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    RemoteHardwareConfigItemList(
        remoteHardwareConfig = state.moduleConfig.remoteHardware,
        enabled = state.connected,
        onSaveClicked = { remoteHardwareInput ->
            val config = moduleConfig { remoteHardware = remoteHardwareInput }
            viewModel.setModuleConfig(config)
        }
    )
}

@Composable
fun RemoteHardwareConfigItemList(
    remoteHardwareConfig: RemoteHardwareConfig,
    enabled: Boolean,
    onSaveClicked: (RemoteHardwareConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var remoteHardwareInput by rememberSaveable { mutableStateOf(remoteHardwareConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = stringResource(R.string.remote_hardware_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.remote_hardware_enabled),
                checked = remoteHardwareInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    remoteHardwareInput = remoteHardwareInput.copy { this.enabled = it }
                }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.allow_undefined_pin_access),
                checked = remoteHardwareInput.allowUndefinedPinAccess,
                enabled = enabled,
                onCheckedChange = {
                    remoteHardwareInput = remoteHardwareInput.copy { allowUndefinedPinAccess = it }
                }
            )
        }
        item { HorizontalDivider() }

        item {
            EditListPreference(
                title = stringResource(R.string.available_pins),
                list = remoteHardwareInput.availablePinsList,
                maxCount = 4, // available_pins max_count:4
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValuesChanged = { list ->
                    remoteHardwareInput = remoteHardwareInput.copy {
                        availablePins.clear()
                        availablePins.addAll(list)
                    }
                }
            )
        }

        item {
            PreferenceFooter(
                enabled = enabled && remoteHardwareInput != remoteHardwareConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    remoteHardwareInput = remoteHardwareConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(remoteHardwareInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RemoteHardwareConfigPreview() {
    RemoteHardwareConfigItemList(
        remoteHardwareConfig = RemoteHardwareConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
