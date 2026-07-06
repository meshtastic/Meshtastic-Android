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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.backup_keys
import org.meshtastic.core.resources.backup_keys_confirmation
import org.meshtastic.core.resources.delete_key_backup
import org.meshtastic.core.resources.delete_key_backup_confirmation
import org.meshtastic.core.resources.restore_keys
import org.meshtastic.core.resources.restore_keys_confirmation
import org.meshtastic.core.ui.component.MeshtasticResourceDialog
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Save
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.Config

@Composable
actual fun SecurityKeyBackupActions(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    securityConfig: Config.SecurityConfig,
) {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val hasBackup = remember(refreshTrigger) { viewModel.securityKeyBackupExists() }

    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showBackupDialog) {
        MeshtasticResourceDialog(
            titleRes = Res.string.backup_keys,
            messageRes = Res.string.backup_keys_confirmation,
            onDismiss = { showBackupDialog = false },
            onConfirm = {
                showBackupDialog = false
                viewModel.backupSecurityKeys(securityConfig) { refreshTrigger++ }
            },
        )
    }
    if (showRestoreDialog) {
        MeshtasticResourceDialog(
            titleRes = Res.string.restore_keys,
            messageRes = Res.string.restore_keys_confirmation,
            onDismiss = { showRestoreDialog = false },
            onConfirm = {
                showRestoreDialog = false
                viewModel.restoreSecurityKeys()
            },
        )
    }
    if (showDeleteDialog) {
        MeshtasticResourceDialog(
            titleRes = Res.string.delete_key_backup,
            messageRes = Res.string.delete_key_backup_confirmation,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteSecurityKeyBackup { refreshTrigger++ }
            },
        )
    }

    HorizontalDivider()
    NodeActionButton(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = stringResource(Res.string.backup_keys),
        enabled = enabled,
        icon = MeshtasticIcons.Save,
        onClick = { showBackupDialog = true },
    )
    HorizontalDivider()
    NodeActionButton(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = stringResource(Res.string.restore_keys),
        enabled = enabled && hasBackup,
        icon = MeshtasticIcons.Refresh,
        onClick = { showRestoreDialog = true },
    )
    HorizontalDivider()
    NodeActionButton(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = stringResource(Res.string.delete_key_backup),
        enabled = enabled && hasBackup,
        icon = MeshtasticIcons.Delete,
        onClick = { showDeleteDialog = true },
    )
}
