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

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.getColorFrom
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.tak
import org.meshtastic.core.resources.tak_config
import org.meshtastic.core.resources.tak_role
import org.meshtastic.core.resources.tak_team
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Composable
fun TAKConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val takConfig = state.moduleConfig.tak ?: ModuleConfig.TAKConfig()
    val formState = rememberConfigState(initialValue = takConfig)

    LaunchedEffect(takConfig) { formState.value = takConfig }

    RadioConfigScreenList(
        title = stringResource(Res.string.tak),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(tak = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.tak_config)) {
                DropDownPreference(
                    title = stringResource(Res.string.tak_team),
                    enabled = state.connected,
                    selectedItem = formState.value.team,
                    itemLabel = { stringResource(getStringResFrom(it)) },
                    itemColor = { Color(getColorFrom(it)) },
                    onItemSelected = { formState.value = formState.value.copy(team = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.tak_role),
                    enabled = state.connected,
                    selectedItem = formState.value.role,
                    itemLabel = { stringResource(getStringResFrom(it)) },
                    onItemSelected = { formState.value = formState.value.copy(role = it) },
                )
            }
        }
    }
}
