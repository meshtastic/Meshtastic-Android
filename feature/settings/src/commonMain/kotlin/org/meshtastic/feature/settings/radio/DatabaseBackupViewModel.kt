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

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.nowInstant
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.database.DatabaseBackup
import org.meshtastic.core.database.DatabaseBackupOperations
import org.meshtastic.core.database.RestoreStageResult
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_database_backup_disconnect_confirmation
import org.meshtastic.core.resources.app_database_backup_failed
import org.meshtastic.core.resources.app_database_backup_saved
import org.meshtastic.core.resources.app_database_restore_confirmation
import org.meshtastic.core.resources.app_database_restore_corrupt
import org.meshtastic.core.resources.app_database_restore_failed
import org.meshtastic.core.resources.app_database_restore_not_backup
import org.meshtastic.core.resources.app_database_restore_staged
import org.meshtastic.core.resources.app_database_restore_wrong_version
import org.meshtastic.core.resources.are_you_sure
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.restore_app_database
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

/** Backs up the app database for the connected device, and stages a restore that applies on the next launch. */
@KoinViewModel
class DatabaseBackupViewModel(
    private val backupService: DatabaseBackupOperations,
    private val fileService: FileService,
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
    private val radioPrefs: RadioPrefs,
    private val alertManager: AlertManager,
) : ViewModel() {

    /** The database and device a backup or restore acts on, captured before disconnecting switches databases. */
    private class Target(val dbName: String, val deviceId: String?)

    private var target: Target? = null

    /** False until some device has a database, so there is nothing to back up or restore into. */
    val hasDeviceDatabase: StateFlow<Boolean> =
        radioController.connectionState
            .map { safeCatching { backupService.backupTargetName() != null }.getOrDefault(false) }
            .stateInWhileSubscribed(initialValue = false)

    private suspend fun captureTarget(): Target? =
        backupService.backupTargetName()?.let { Target(it, nodeRepository.myNodeInfo.value?.deviceId) }

    /** File name offered by the save picker: device id and today's date. */
    fun suggestedFileName(): String =
        DatabaseBackup.fileName(target?.deviceId ?: nodeRepository.myNodeInfo.value?.deviceId, nowInstant)

    /**
     * Runs [onReady] now when the radio is disconnected. Otherwise, including while it sleeps or connects, asks first
     * and, once confirmed, disconnects the radio the same way the Connections screen does, then runs [onReady]. The
     * backup or restore binds to the device database active before the disconnect or, already disconnected, to the last
     * device's; with no device database it does not proceed.
     */
    fun runWhenDisconnected(onReady: () -> Unit) {
        if (radioController.connectionState.value is ConnectionState.Disconnected) {
            safeLaunch(tag = "databaseBackupTarget") {
                target = captureTarget() ?: return@safeLaunch
                onReady()
            }
            return
        }
        alertManager.showAlert(
            titleRes = Res.string.are_you_sure,
            messageRes = Res.string.app_database_backup_disconnect_confirmation,
            confirmTextRes = Res.string.disconnect,
            onConfirm = {
                safeLaunch(tag = "databaseBackupDisconnect") {
                    val captured = captureTarget()
                    radioPrefs.setDevName(null)
                    radioController.setDeviceAddress(NO_DEVICE_SELECTED)
                    target = captured ?: return@safeLaunch
                    onReady()
                }
            },
        )
    }

    fun backup(uri: CommonUri) {
        val dbName = target?.dbName ?: return
        safeLaunch(tag = "databaseBackup") {
            val written = fileService.write(uri) { sink -> backupService.export(dbName, sink) }
            val message =
                getString(if (written) Res.string.app_database_backup_saved else Res.string.app_database_backup_failed)
            alertManager.showAlert(message = message)
        }
    }

    /** Asks for confirmation, then calls [onConfirmed] so the caller can open its file picker. */
    fun confirmRestore(onConfirmed: () -> Unit) {
        safeLaunch(tag = "databaseRestoreConfirm") {
            alertManager.showAlert(
                titleRes = Res.string.are_you_sure,
                message = getString(Res.string.app_database_restore_confirmation),
                confirmTextRes = Res.string.restore_app_database,
                onConfirm = onConfirmed,
            )
        }
    }

    fun restore(uri: CommonUri) {
        val dbName = target?.dbName ?: return
        safeLaunch(tag = "databaseRestore") {
            var result: RestoreStageResult = RestoreStageResult.Failed(IllegalStateException("file not readable"))
            fileService.read(uri) { source -> result = backupService.stageRestore(dbName, source) }
            alertManager.showAlert(message = restoreMessage(result))
        }
    }

    private suspend fun restoreMessage(result: RestoreStageResult): String = when (result) {
        RestoreStageResult.Staged -> getString(Res.string.app_database_restore_staged)

        is RestoreStageResult.Failed -> getString(Res.string.app_database_restore_failed)

        is RestoreStageResult.Invalid ->
            when (val reason = result.reason) {
                is DatabaseBackup.Validation.SchemaMismatch ->
                    getString(Res.string.app_database_restore_wrong_version, reason.found, reason.expected)

                DatabaseBackup.Validation.Corrupt -> getString(Res.string.app_database_restore_corrupt)

                else -> getString(Res.string.app_database_restore_not_backup)
            }
    }
}

/** The device address that means "no radio selected"; the same value `ScannerViewModel.disconnect` persists. */
private const val NO_DEVICE_SELECTED = "n"
