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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.backup_keys
import org.meshtastic.core.resources.backup_keys_confirmation
import org.meshtastic.core.resources.delete_key_backup
import org.meshtastic.core.resources.delete_key_backup_confirmation
import org.meshtastic.core.resources.delete_key_backup_pick
import org.meshtastic.core.resources.key_backup_choice
import org.meshtastic.core.resources.restore_keys
import org.meshtastic.core.resources.restore_keys_confirmation
import org.meshtastic.core.resources.restore_keys_pick_backup
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.MeshtasticResourceDialog
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Save
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.SecurityKeyBackupOption
import org.meshtastic.proto.Config

@Composable
actual fun SecurityKeyBackupActions(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    securityConfig: Config.SecurityConfig,
) {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val destNodeNum = viewModel.destNode.collectAsStateWithLifecycle().value?.num
    val backups = remember(refreshTrigger, destNodeNum) { viewModel.securityKeyBackups() }

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
        KeyBackupDialog(
            backups = backups,
            titleRes = Res.string.restore_keys,
            confirmRes = Res.string.restore_keys_confirmation,
            pickRes = Res.string.restore_keys_pick_backup,
            onPick = { viewModel.restoreSecurityKeys(it) },
            onDismiss = { showRestoreDialog = false },
        )
    }
    if (showDeleteDialog) {
        KeyBackupDialog(
            backups = backups,
            titleRes = Res.string.delete_key_backup,
            confirmRes = Res.string.delete_key_backup_confirmation,
            pickRes = Res.string.delete_key_backup_pick,
            onPick = { viewModel.deleteSecurityKeyBackup(it) { refreshTrigger++ } },
            onDismiss = { showDeleteDialog = false },
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
        enabled = enabled && backups.isNotEmpty(),
        icon = MeshtasticIcons.Refresh,
        onClick = { showRestoreDialog = true },
    )
    HorizontalDivider()
    NodeActionButton(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = stringResource(Res.string.delete_key_backup),
        enabled = enabled && backups.isNotEmpty(),
        icon = MeshtasticIcons.Delete,
        onClick = { showDeleteDialog = true },
    )
}

/**
 * Confirms acting on this node's own backup when it is the only one stored; otherwise offers every backup to pick from,
 * since a reset or new key leaves the node's older backups under its old number.
 */
@Composable
private fun KeyBackupDialog(
    backups: List<SecurityKeyBackupOption>,
    titleRes: StringResource,
    confirmRes: StringResource,
    pickRes: StringResource,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val only = backups.singleOrNull()
    if (only != null && only.isCurrentNode) {
        MeshtasticResourceDialog(
            titleRes = titleRes,
            messageRes = confirmRes,
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                onPick(only.nodeNum)
            },
        )
        return
    }
    val choices = backups.associate { backup ->
        val id = NodeAddress.numToDefaultId(backup.nodeNum)
        val name = backup.longName?.let { "$it ($id)" } ?: id
        stringResource(Res.string.key_backup_choice, name, DateFormatter.formatDateTimeShort(backup.timestamp)) to
            {
                onPick(backup.nodeNum)
            }
    }
    MeshtasticDialog(titleRes = titleRes, messageRes = pickRes, choices = choices, onDismiss = onDismiss)
}
