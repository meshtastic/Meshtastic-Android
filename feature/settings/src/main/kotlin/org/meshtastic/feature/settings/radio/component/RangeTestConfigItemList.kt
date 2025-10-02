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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun RangeTestConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val rangeTestConfig = state.moduleConfig.rangeTest
    val formState = rememberConfigState(initialValue = rangeTestConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.range_test),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { rangeTest = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.range_test_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.range_test_enabled),
                checked = formState.value.enabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.sender_message_interval_seconds),
                value = formState.value.sender,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { sender = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.save_csv_in_storage_esp32_only),
                checked = formState.value.save,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { save = it } },
            )
        }
        item { HorizontalDivider() }
    }
}
