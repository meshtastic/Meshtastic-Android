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
package org.meshtastic.feature.settings.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.navigation.FirmwareRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
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
import org.meshtastic.core.resources.import_configuration
import org.meshtastic.core.resources.message_device_managed
import org.meshtastic.core.resources.module_settings
import org.meshtastic.core.resources.nodedb_reset
import org.meshtastic.core.resources.radio_configuration
import org.meshtastic.core.resources.reboot
import org.meshtastic.core.resources.shutdown
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.feature.settings.navigation.ConfigRoute

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RadioConfigItemList(
    state: RadioConfigState,
    isManaged: Boolean,
    node: Node? = null,
    isOtaCapable: Boolean = false,
    onPreserveFavoritesToggle: (Boolean) -> Unit = {},
    onRouteClick: (Enum<*>) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    onNavigate: (Route) -> Unit,
) {
    val enabled = state.connected && !state.responseState.isWaiting() && !isManaged

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExpressiveSection(title = stringResource(Res.string.radio_configuration)) {
            if (isManaged) {
                ManagedMessage()
            }
            ConfigRoute.radioConfigRoutes.forEach {
                ListItem(text = stringResource(it.title), leadingIcon = it.icon, enabled = enabled) { onRouteClick(it) }
            }
        }

        ExpressiveSection(title = stringResource(Res.string.device_configuration)) {
            if (isManaged) {
                ManagedMessage()
            }
            ListItem(
                text = stringResource(Res.string.device_configuration),
                leadingIcon = Icons.Rounded.AppSettingsAlt,
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                enabled = enabled,
            ) {
                onNavigate(SettingsRoutes.DeviceConfiguration)
            }
        }

        ExpressiveSection(title = stringResource(Res.string.module_settings)) {
            if (isManaged) {
                ManagedMessage()
            }
            ListItem(
                text = stringResource(Res.string.module_settings),
                leadingIcon = Icons.Rounded.Settings,
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                enabled = enabled,
            ) {
                onNavigate(SettingsRoutes.ModuleConfiguration)
            }
        }

        if (state.isLocal) {
            ExpressiveSection(title = stringResource(Res.string.backup_restore)) {
                if (isManaged) {
                    ManagedMessage()
                }

                ListItem(
                    text = stringResource(Res.string.import_configuration),
                    leadingIcon = Icons.Rounded.Download,
                    enabled = enabled,
                    onClick = onImport,
                )
                ListItem(
                    text = stringResource(Res.string.export_configuration),
                    leadingIcon = Icons.Rounded.Upload,
                    enabled = enabled,
                    onClick = onExport,
                )
            }
        }

        ExpressiveSection(
            title = stringResource(Res.string.administration),
        ) {
            ListItem(
                text = stringResource(Res.string.administration),
                leadingIcon = Icons.Rounded.AdminPanelSettings,
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                enabled = enabled,
            ) {
                onNavigate(SettingsRoutes.Administration)
            }
        }

        if (state.isLocal) {
            ExpressiveSection(title = stringResource(Res.string.advanced_title)) {
                if (isManaged) {
                    ManagedMessage()
                }

                if (isOtaCapable) {
                    ListItem(
                        text = stringResource(Res.string.firmware_update_title),
                        leadingIcon = Icons.Rounded.SystemUpdate,
                        enabled = enabled,
                        onClick = { onNavigate(FirmwareRoutes.FirmwareUpdate) },
                    )
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
    }
}

@Composable
fun ExpressiveSection(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = titleColor,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            content = content,
        )
    }
}

enum class AdminRoute(val icon: ImageVector, val title: StringResource) {
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
