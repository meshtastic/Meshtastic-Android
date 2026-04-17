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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.getColorFrom
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.repository.TakPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.export_tak_data_package
import org.meshtastic.core.resources.tak
import org.meshtastic.core.resources.tak_config
import org.meshtastic.core.resources.tak_role
import org.meshtastic.core.resources.tak_server_enabled
import org.meshtastic.core.resources.tak_server_enabled_desc
import org.meshtastic.core.resources.tak_team
import org.meshtastic.core.takserver.TAKDataPackageGenerator
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Share
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.tak.TakPermissionHandler
import org.meshtastic.feature.settings.tak.rememberDataPackageExporter
import org.meshtastic.proto.ModuleConfig

@Composable
fun TAKConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val takConfig = state.moduleConfig.tak ?: ModuleConfig.TAKConfig()
    val formState = rememberConfigState(initialValue = takConfig)

    val takPrefs: TakPrefs = koinInject()
    val isTakServerEnabled by takPrefs.isTakServerEnabled.collectAsStateWithLifecycle()

    val exportLauncher = rememberDataPackageExporter { TAKDataPackageGenerator.generateDataPackage() }

    LaunchedEffect(takConfig) { formState.value = takConfig }

    TakPermissionHandler(
        isTakServerEnabled = isTakServerEnabled,
        onPermissionResult = { granted ->
            if (!granted && isTakServerEnabled) {
                takPrefs.setTakServerEnabled(false)
            }
        },
    )

    RadioConfigScreenList(
        title = stringResource(Res.string.tak),
        onBack = onBack,
        actions = {
            IconButton(onClick = { exportLauncher("Meshtastic_TAK_Server.zip") }) {
                Icon(
                    imageVector = MeshtasticIcons.Share,
                    contentDescription = stringResource(Res.string.export_tak_data_package),
                )
            }
        },
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
            TAKConfigCard(
                formState = formState,
                isTakServerEnabled = isTakServerEnabled,
                isConnected = state.connected,
                onTakServerEnabledChange = { takPrefs.setTakServerEnabled(it) },
            )
        }
    }
}

@Composable
private fun TAKConfigCard(
    formState: ConfigState<ModuleConfig.TAKConfig>,
    isTakServerEnabled: Boolean,
    isConnected: Boolean,
    onTakServerEnabledChange: (Boolean) -> Unit,
) {
    TitledCard(title = stringResource(Res.string.tak_config)) {
        SwitchPreference(
            title = stringResource(Res.string.tak_server_enabled),
            summary = stringResource(Res.string.tak_server_enabled_desc),
            checked = isTakServerEnabled,
            enabled = true,
            onCheckedChange = onTakServerEnabledChange,
        )
        HorizontalDivider()
        DropDownPreference(
            title = stringResource(Res.string.tak_team),
            enabled = isConnected,
            selectedItem = formState.value.team,
            itemLabel = { stringResource(getStringResFrom(it)) },
            itemColor = { Color(getColorFrom(it)) },
            onItemSelected = { formState.value = formState.value.copy(team = it) },
        )
        HorizontalDivider()
        DropDownPreference(
            title = stringResource(Res.string.tak_role),
            enabled = isConnected,
            selectedItem = formState.value.role,
            itemLabel = { stringResource(getStringResFrom(it)) },
            onItemSelected = { formState.value = formState.value.copy(role = it) },
        )
    }
}
