/*
 * Copyright (c) 2026 Meshtastic LLC
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.node_status_summary
import org.meshtastic.core.resources.status_message
import org.meshtastic.core.resources.status_message_config
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
fun StatusMessageConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()

    // Use the config value if present, otherwise fall back to the node's current status message from telemetry
    val statusMessageConfig =
        remember(state.moduleConfig.statusmessage, destNode?.nodeStatus) {
            val config = state.moduleConfig.statusmessage ?: org.meshtastic.proto.ModuleConfig.StatusMessageConfig()
            val currentStatus = destNode?.nodeStatus ?: ""
            if (config.node_status.isBlank() && currentStatus.isNotBlank()) {
                config.copy(node_status = currentStatus)
            } else {
                config
            }
        }

    val formState = rememberConfigState(initialValue = statusMessageConfig)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(statusMessageConfig) { formState.value = statusMessageConfig }

    RadioConfigScreenList(
        title = stringResource(Res.string.status_message),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = org.meshtastic.proto.ModuleConfig(statusmessage = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.status_message_config)) {
                EditTextPreference(
                    title = stringResource(Res.string.node_status_summary),
                    value = formState.value.node_status,
                    maxSize = 80, // status_message max_size:80
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(node_status = it) },
                    trailingIcon = {
                        if (formState.value.node_status.isNotEmpty()) {
                            IconButton(onClick = { formState.value = formState.value.copy(node_status = "") }) {
                                Icon(
                                    imageVector = MeshtasticIcons.Close,
                                    contentDescription = stringResource(Res.string.clear),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
