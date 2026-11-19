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
package org.meshtastic.core.database

import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.database.DatabaseBackup.Validation

/** Outcome of [DatabaseBackupOperations.stageRestore]. */
sealed interface RestoreStageResult {
    /** The file is staged and replaces the device database the next time that database is opened. */
    data object Staged : RestoreStageResult

    data class Invalid(val reason: Validation) : RestoreStageResult

    data class Failed(val cause: Throwable) : RestoreStageResult
}

/** What the settings screen needs from a database backup, so a test can stand in for the file-backed service. */
interface DatabaseBackupOperations {
    /**
     * The device database to act on: the active one, or the last device's when none is selected. Null when no device
     * database exists. Capture it before anything switches databases.
     */
    suspend fun backupTargetName(): String?

    suspend fun export(dbName: String, sink: BufferedSink): Long

    suspend fun stageRestore(dbName: String, source: BufferedSource): RestoreStageResult
}
