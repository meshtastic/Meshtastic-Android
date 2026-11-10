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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.database.DatabaseBackup
import org.meshtastic.core.ui.util.rememberOpenFileLauncher
import org.meshtastic.core.ui.util.rememberSaveFileLauncher

/**
 * Picker-backed entry points for the Backup & Restore section's app database items. [available] is false until some
 * device has a database.
 */
class DatabaseBackupActions(val available: Boolean, val onBackup: () -> Unit, val onRestore: () -> Unit)

@Composable
fun rememberDatabaseBackupActions(): DatabaseBackupActions {
    val viewModel = koinViewModel<DatabaseBackupViewModel>()
    val available by viewModel.hasDeviceDatabase.collectAsStateWithLifecycle()
    val saveFile = rememberSaveFileLauncher { uri -> viewModel.backup(uri) }
    val openFile = rememberOpenFileLauncher { uri -> if (uri != null) viewModel.restore(uri) }
    return remember(viewModel, available, saveFile, openFile) {
        DatabaseBackupActions(
            available = available,
            onBackup = {
                viewModel.runWhenDisconnected { saveFile(viewModel.suggestedFileName(), DatabaseBackup.MIME_TYPE) }
            },
            onRestore = {
                viewModel.runWhenDisconnected { viewModel.confirmRestore { openFile(DatabaseBackup.MIME_TYPE) } }
            },
        )
    }
}
