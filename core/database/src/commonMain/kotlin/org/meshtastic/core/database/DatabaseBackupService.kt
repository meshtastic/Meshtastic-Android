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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

/** Outcome of [DatabaseBackupService.stageRestore]. */
sealed interface RestoreStageResult {
    /** The file is staged and replaces the device database the next time that database is opened. */
    data object Staged : RestoreStageResult

    data class Invalid(val reason: DatabaseBackup.Validation) : RestoreStageResult

    data class Failed(val cause: Throwable) : RestoreStageResult
}

/**
 * Copies the active device database out to a user-chosen sink, and stages a validated backup to replace it on the next
 * launch. A restore is never applied to an open database.
 */
@Single
class DatabaseBackupService(private val manager: DatabaseManager, private val dispatchers: CoroutineDispatchers) {
    private val fileSystem: FileSystem = getFileSystem()

    /** Checkpoints the WAL into the main file, then streams that file into [sink]. Returns the bytes written. */
    suspend fun export(sink: BufferedSink): Long {
        val dbName = manager.checkpointActiveDatabase()
        return withContext(dispatchers.io) {
            fileSystem.read(getDatabaseFile(dbName)) { sink.writeAll(this) }
        }
    }

    /**
     * Validates the header of [source], streams it beside the active database, then atomically renames it into place as
     * the staged restore. Nothing the live database reads changes.
     */
    suspend fun stageRestore(source: BufferedSource): RestoreStageResult = withContext(dispatchers.io) {
        val dbFile = getDatabaseFile(manager.activeDatabaseName)
        val temp = DatabaseBackup.stagingTempPath(dbFile)
        try {
            val header = DatabaseBackup.readHeader(source)
            val verdict = DatabaseBackup.validateHeader(header)
            if (verdict != DatabaseBackup.Validation.Valid) return@withContext RestoreStageResult.Invalid(verdict)
            fileSystem.write(temp) { writeAll(source) }
            val staged = DatabaseBackup.stagedPath(dbFile)
            fileSystem.delete(staged)
            fileSystem.atomicMove(temp, staged)
            RestoreStageResult.Staged
        } catch (e: IOException) {
            Logger.w(e) { "Staging database restore failed" }
            fileSystem.delete(temp)
            RestoreStageResult.Failed(e)
        }
    }
}

/**
 * Replaces the database file for [dbName] with its staged restore, if one exists and still validates. Runs before the
 * database is opened, so no connection holds the old file or its `-wal`/`-shm` companions.
 */
internal fun applyStagedRestore(dbName: String, dbFile: Path, fileSystem: FileSystem) {
    val staged = DatabaseBackup.stagedPath(dbFile)
    if (!fileSystem.exists(staged)) return
    try {
        val header = fileSystem.read(staged) { DatabaseBackup.readHeader(this) }
        if (DatabaseBackup.validateHeader(header) == DatabaseBackup.Validation.Valid) {
            fileSystem.delete(dbFile.parent!! / (dbFile.name + "-wal"))
            fileSystem.delete(dbFile.parent!! / (dbFile.name + "-shm"))
            fileSystem.delete(dbFile)
            fileSystem.atomicMove(staged, dbFile)
            Logger.i { "Applied staged restore to ${anonymizeDbName(dbName)}" }
        } else {
            fileSystem.delete(staged)
            Logger.w { "Discarded invalid staged restore for ${anonymizeDbName(dbName)}" }
        }
    } catch (e: IOException) {
        Logger.w(e) { "Applying staged restore failed for ${anonymizeDbName(dbName)}" }
    }
}
