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
package org.meshtastic.feature.settings.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.navigation.FirmwareRoute
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.administration
import org.meshtastic.core.resources.advanced_title
import org.meshtastic.core.resources.backup_restore
import org.meshtastic.core.resources.clean_node_database_title
import org.meshtastic.core.resources.debug_panel
import org.meshtastic.core.resources.device_configuration
import org.meshtastic.core.resources.export_configuration
import org.meshtastic.core.resources.factory_reset
import org.meshtastic.core.resources.firmware_update_title
import org.meshtastic.core.resources.ic_power_settings_new
import org.meshtastic.core.resources.ic_restart_alt
import org.meshtastic.core.resources.ic_restore
import org.meshtastic.core.resources.ic_storage
import org.meshtastic.core.resources.import_configuration
import org.meshtastic.core.resources.message_device_managed
import org.meshtastic.core.resources.module_settings
import org.meshtastic.core.resources.nodedb_reset
import org.meshtastic.core.resources.radio_configuration
import org.meshtastic.core.resources.reboot
import org.meshtastic.core.resources.shutdown
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.AdminPanelSettings
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.BugReport
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.CleaningServices
import org.meshtastic.core.ui.icon.Download
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.icon.SystemUpdate
import org.meshtastic.core.ui.icon.Upload
import org.meshtastic.feature.settings.component.ExpressiveSection
import org.meshtastic.feature.settings.navigation.ConfigRoute

@Composable
fun RadioConfigItemList(
    state: RadioConfigState,
    isManaged: Boolean,
    isOtaCapable: Boolean = false,
    onRouteClick: (Enum<*>) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    onNavigate: (Route) -> Unit,
) {
    val enabled = state.connected && !state.responseState.isWaiting() && !isManaged

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RadioConfigSection(isManaged, enabled, onRouteClick)
        DeviceConfigSection(isManaged, enabled, onNavigate)
        ModuleSettingsSection(isManaged, enabled, onNavigate)

        if (state.isLocal) {
            BackupRestoreSection(isManaged, enabled, onImport, onExport)
        }

        AdministrationSection(enabled, onNavigate)

        if (state.isLocal) {
            AdvancedSection(isManaged, isOtaCapable, enabled, onNavigate)
        }
    }
}

@Composable
private fun RadioConfigSection(isManaged: Boolean, enabled: Boolean, onRouteClick: (Enum<*>) -> Unit) {
    ExpressiveSection(title = stringResource(Res.string.radio_configuration)) {
        if (isManaged) {
            ManagedMessage()
        }
        ConfigRoute.radioConfigRoutes.forEach {
            ListItem(
                text = stringResource(it.title),
                leadingIcon = it.icon?.let { res -> vectorResource(res) },
                enabled = enabled,
            ) {
                onRouteClick(it)
            }
        }
    }
}

@Composable
private fun DeviceConfigSection(isManaged: Boolean, enabled: Boolean, onNavigate: (Route) -> Unit) {
    ExpressiveSection(title = stringResource(Res.string.device_configuration)) {
        if (isManaged) {
            ManagedMessage()
        }
        ListItem(
            text = stringResource(Res.string.device_configuration),
            leadingIcon = MeshtasticIcons.AppSettingsAlt,
            trailingIcon = MeshtasticIcons.ChevronRight,
            enabled = enabled,
        ) {
            onNavigate(SettingsRoute.DeviceConfiguration)
        }
    }
}

@Composable
private fun ModuleSettingsSection(isManaged: Boolean, enabled: Boolean, onNavigate: (Route) -> Unit) {
    ExpressiveSection(title = stringResource(Res.string.module_settings)) {
        if (isManaged) {
            ManagedMessage()
        }
        ListItem(
            text = stringResource(Res.string.module_settings),
            leadingIcon = MeshtasticIcons.Settings,
            trailingIcon = MeshtasticIcons.ChevronRight,
            enabled = enabled,
        ) {
            onNavigate(SettingsRoute.ModuleConfiguration)
        }
    }
}

@Composable
private fun BackupRestoreSection(isManaged: Boolean, enabled: Boolean, onImport: () -> Unit, onExport: () -> Unit) {
    ExpressiveSection(title = stringResource(Res.string.backup_restore)) {
        if (isManaged) {
            ManagedMessage()
        }

        ListItem(
            text = stringResource(Res.string.import_configuration),
            leadingIcon = MeshtasticIcons.Download,
            enabled = enabled,
            onClick = onImport,
        )
        ListItem(
            text = stringResource(Res.string.export_configuration),
            leadingIcon = MeshtasticIcons.Upload,
            enabled = enabled,
            onClick = onExport,
        )
    }
}

@Composable
private fun AdministrationSection(enabled: Boolean, onNavigate: (Route) -> Unit) {
    ExpressiveSection(title = stringResource(Res.string.administration)) {
        ListItem(
            text = stringResource(Res.string.administration),
            leadingIcon = MeshtasticIcons.AdminPanelSettings,
            trailingIcon = MeshtasticIcons.ChevronRight,
            leadingIconTint = MaterialTheme.colorScheme.error,
            textColor = MaterialTheme.colorScheme.error,
            trailingIconTint = MaterialTheme.colorScheme.error,
            enabled = enabled,
        ) {
            onNavigate(SettingsRoute.Administration)
        }
    }
}

@Composable
private fun AdvancedSection(isManaged: Boolean, isOtaCapable: Boolean, enabled: Boolean, onNavigate: (Route) -> Unit) {
    ExpressiveSection(title = stringResource(Res.string.advanced_title)) {
        if (isManaged) {
            ManagedMessage()
        }

        if (isOtaCapable) {
            ListItem(
                text = stringResource(Res.string.firmware_update_title),
                leadingIcon = MeshtasticIcons.SystemUpdate,
                enabled = enabled,
                onClick = { onNavigate(FirmwareRoute.FirmwareUpdate) },
            )
        }

        ListItem(
            text = stringResource(Res.string.clean_node_database_title),
            leadingIcon = MeshtasticIcons.CleaningServices,
            enabled = enabled,
            onClick = { onNavigate(SettingsRoute.CleanNodeDb) },
        )

        ListItem(
            text = stringResource(Res.string.debug_panel),
            leadingIcon = MeshtasticIcons.BugReport,
            enabled = enabled,
            onClick = { onNavigate(SettingsRoute.DebugPanel) },
        )
    }
}

enum class AdminRoute(val icon: DrawableResource, val title: StringResource) {
    REBOOT(Res.drawable.ic_restart_alt, Res.string.reboot),
    SHUTDOWN(Res.drawable.ic_power_settings_new, Res.string.shutdown),
    FACTORY_RESET(Res.drawable.ic_restore, Res.string.factory_reset),
    NODEDB_RESET(Res.drawable.ic_storage, Res.string.nodedb_reset),
}

@Composable
private fun ManagedMessage() {
    Text(
        text = stringResource(Res.string.message_device_managed),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
    )
}
