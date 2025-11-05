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

package org.meshtastic.feature.settings.radio

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.radio.component.WarningDialog
import org.meshtastic.core.strings.R as Res

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RadioConfigItemList(
    state: RadioConfigState,
    isManaged: Boolean,
    excludedModulesUnlocked: Boolean = false,
    onRouteClick: (Enum<*>) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    onNavigate: (Route) -> Unit,
) {
    val enabled = state.connected && !state.responseState.isWaiting() && !isManaged
    var modules by remember { mutableStateOf(ModuleRoute.filterExcludedFrom(state.metadata)) }

    LaunchedEffect(excludedModulesUnlocked) {
        if (excludedModulesUnlocked) {
            modules = ModuleRoute.entries
        } else {
            modules = ModuleRoute.filterExcludedFrom(state.metadata)
        }
    }

    Column {
        TitledCard(title = stringResource(Res.string.radio_configuration)) {
            if (isManaged) {
                ManagedMessage()
            }
            ConfigRoute.radioConfigRoutes.forEach {
                ListItem(text = stringResource(it.title), leadingIcon = it.icon, enabled = enabled) { onRouteClick(it) }
            }
        }

        TitledCard(title = stringResource(Res.string.device_configuration), modifier = Modifier.padding(top = 16.dp)) {
            if (isManaged) {
                ManagedMessage()
            }
            ConfigRoute.deviceConfigRoutes(state.metadata).forEach {
                ListItem(text = stringResource(it.title), leadingIcon = it.icon, enabled = enabled) { onRouteClick(it) }
            }
        }

        TitledCard(title = stringResource(Res.string.module_settings), modifier = Modifier.padding(top = 16.dp)) {
            if (isManaged) {
                ManagedMessage()
            }

            modules.forEach {
                ListItem(text = stringResource(it.title), leadingIcon = it.icon, enabled = enabled) { onRouteClick(it) }
            }
        }
    }

    if (state.isLocal) {
        TitledCard(title = stringResource(Res.string.backup_restore), modifier = Modifier.padding(top = 16.dp)) {
            if (isManaged) {
                ManagedMessage()
            }

            ListItem(
                text = stringResource(Res.string.import_configuration),
                leadingIcon = Icons.Default.Download,
                enabled = enabled,
                onClick = onImport,
            )
            ListItem(
                text = stringResource(Res.string.export_configuration),
                leadingIcon = Icons.Default.Upload,
                enabled = enabled,
                onClick = onExport,
            )
        }
    }

    TitledCard(title = stringResource(Res.string.administration), modifier = Modifier.padding(top = 16.dp)) {
        AdminRoute.entries.forEach { route ->
            var showDialog by remember { mutableStateOf(false) }
            if (showDialog) {
                WarningDialog(
                    title = "${stringResource(route.title)}?",
                    onDismiss = { showDialog = false },
                    onConfirm = { onRouteClick(route) },
                )
            }

            ListItem(
                enabled = enabled,
                text = stringResource(route.title),
                leadingIcon = route.icon,
                trailingIcon = null,
            ) {
                showDialog = true
            }
        }
    }

    TitledCard(title = stringResource(Res.string.advanced_title), modifier = Modifier.padding(top = 16.dp)) {
        if (isManaged) {
            ManagedMessage()
        }

        ListItem(
            text = stringResource(Res.string.clean_node_database_title),
            leadingIcon = Icons.Rounded.CleaningServices,
            enabled = enabled,
            onClick = { onNavigate(SettingsRoutes.CleanNodeDb) },
        )

        ListItem(
            text = stringResource(Res.string.debug_panel),
            leadingIcon = Icons.Rounded.BugReport,
            enabled = enabled,
            onClick = { onNavigate(SettingsRoutes.DebugPanel) },
        )
    }
}

enum class AdminRoute(val icon: ImageVector, @StringRes val title: Int) {
    REBOOT(Icons.Rounded.RestartAlt, Res.string.reboot),
    SHUTDOWN(Icons.Rounded.PowerSettingsNew, Res.string.shutdown),
    FACTORY_RESET(Icons.Rounded.Restore, Res.string.factory_reset),
    NODEDB_RESET(Icons.Rounded.Storage, Res.string.nodedb_reset),
}

@Preview(showBackground = true)
@Composable
private fun RadioSettingsScreenPreview() = AppTheme {
    RadioConfigItemList(
        state = RadioConfigState(isLocal = true, connected = true),
        isManaged = false,
        onNavigate = { _ -> },
    )
}

@Composable
private fun ManagedMessage() {
    Text(
        text = stringResource(Res.string.message_device_managed),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.StatusRed,
    )
}

@Preview(showBackground = true)
@Composable
private fun RadioSettingsScreenManagedPreview() = AppTheme {
    RadioConfigItemList(
        state = RadioConfigState(isLocal = true, connected = true),
        isManaged = true,
        onNavigate = { _ -> },
    )
}
