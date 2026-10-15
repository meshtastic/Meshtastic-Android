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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseBackup.Validation
import org.meshtastic.core.di.CoroutineDispatchers

/** Outcome of [DatabaseBackupService.stageRestore]. */
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

/**
 * Copies a device database out to a user-chosen sink, and stages a validated backup to replace it the next time that
 * database is opened. A restore is never applied to an open database.
 */
@Single
class DatabaseBackupService(private val manager: DatabaseManager, private val dispatchers: CoroutineDispatchers) :
    DatabaseBackupOperations {
    private val fileSystem: FileSystem = getFileSystem()

    override suspend fun backupTargetName(): String? = manager.backupTargetDatabaseName()

    /** Snapshots [dbName] with `VACUUM INTO`, then streams the snapshot into [sink]. Returns the bytes written. */
    override suspend fun export(dbName: String, sink: BufferedSink): Long = withContext(dispatchers.io) {
        val dbFile = getDatabaseFile(dbName)
        val snapshot = DatabaseBackup.exportTempPath(dbFile)
        fileSystem.delete(snapshot)
        try {
            vacuumInto(dbFile, snapshot)
            fileSystem.read(snapshot) { sink.writeAll(this) }
        } finally {
            fileSystem.delete(snapshot)
        }
    }

    /**
     * Streams [source] beside the database [dbName], checks it with SQLite, then atomically renames it into place as
     * the staged restore. Nothing the live database reads changes.
     */
    override suspend fun stageRestore(dbName: String, source: BufferedSource): RestoreStageResult =
        withContext(dispatchers.io) {
            val dbFile = getDatabaseFile(dbName)
            val temp = DatabaseBackup.stagingTempPath(dbFile)
            try {
                val headerVerdict = DatabaseBackup.validateHeader(DatabaseBackup.readHeader(source))
                if (headerVerdict != Validation.Valid) return@withContext RestoreStageResult.Invalid(headerVerdict)
                fileSystem.write(temp) { writeAll(source) }
                val verdict = validateBackupFile(temp, fileSystem)
                if (verdict != Validation.Valid) {
                    fileSystem.delete(temp)
                    return@withContext RestoreStageResult.Invalid(verdict)
                }
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
 * Writes a consistent single-file snapshot of the database at [dbFile], committed WAL frames included, to [target].
 * Uses a connection of its own, so the snapshot does not depend on which database is active.
 */
internal fun vacuumInto(dbFile: Path, target: Path) {
    BundledSQLiteDriver().open(dbFile.toString(), SQLITE_OPEN_READWRITE).use { connection ->
        connection.execSQL("PRAGMA busy_timeout = ${BusyTimeoutSQLiteDriver.DEFAULT_BUSY_TIMEOUT_MS}")
        connection.prepare("VACUUM INTO ?").use { statement ->
            statement.bindText(1, target.toString())
            statement.step()
        }
    }
}

/**
 * Checks that [file] is a whole, undamaged SQLite database at exactly [expectedVersion]. An older version is rejected
 * too: Room's destructive fallback would empty it on open wherever a migration is missing.
 */
internal fun validateBackupFile(
    file: Path,
    fileSystem: FileSystem,
    expectedVersion: Int = DatabaseBackup.SCHEMA_VERSION,
): Validation {
    val headerVerdict = DatabaseBackup.validateHeader(fileSystem.read(file) { DatabaseBackup.readHeader(this) })
    if (headerVerdict != Validation.Valid) return headerVerdict
    return try {
        BundledSQLiteDriver().open(file.toString(), SQLITE_OPEN_READONLY).use { connection ->
            val version = connection.firstColumn("PRAGMA user_version")?.toIntOrNull()
            when {
                version != expectedVersion -> Validation.SchemaMismatch(version ?: -1, expectedVersion)
                connection.firstColumn("PRAGMA integrity_check") == "ok" -> Validation.Valid
                else -> Validation.Corrupt
            }
        }
    } catch (e: SQLiteException) {
        Logger.w(e) { "Backup file failed to open as SQLite" }
        Validation.Corrupt
    } finally {
        fileSystem.delete(DatabaseBackup.sibling(file, "-wal"))
        fileSystem.delete(DatabaseBackup.sibling(file, "-shm"))
    }
}

private fun SQLiteConnection.firstColumn(sql: String): String? =
    prepare(sql).use { statement -> if (statement.step()) statement.getText(0) else null }

/**
 * Replaces the database file for [dbName] with its staged restore, if one exists and still validates. Runs before the
 * database is opened, so no connection holds the old file or its `-wal`/`-shm` companions. The old files move aside to
 * `.bak` and come back if the staged file cannot take their place.
 */
internal fun applyStagedRestore(dbName: String, dbFile: Path, fileSystem: FileSystem) {
    try {
        recoverInterruptedRestore(dbFile, fileSystem)
        val staged = DatabaseBackup.stagedPath(dbFile)
        if (!fileSystem.exists(staged)) return
        if (validateBackupFile(staged, fileSystem) == Validation.Valid) {
            swapInStagedRestore(staged, dbFile, fileSystem)
            Logger.i { "Applied staged restore to ${anonymizeDbName(dbName)}" }
        } else {
            fileSystem.delete(staged)
            Logger.w { "Discarded invalid staged restore for ${anonymizeDbName(dbName)}" }
        }
    } catch (e: IOException) {
        Logger.w(e) { "Applying staged restore failed for ${anonymizeDbName(dbName)}" }
    }
}

private fun swapInStagedRestore(staged: Path, dbFile: Path, fileSystem: FileSystem) {
    val movedAside = mutableListOf<Path>()
    try {
        for (file in DatabaseBackup.databaseFiles(dbFile)) {
            if (fileSystem.exists(file)) {
                fileSystem.atomicMove(file, DatabaseBackup.backupPath(file))
                movedAside += file
            }
        }
        fileSystem.atomicMove(staged, dbFile)
    } catch (e: IOException) {
        for (file in movedAside.asReversed()) fileSystem.atomicMove(DatabaseBackup.backupPath(file), file)
        throw e
    }
    for (file in movedAside) fileSystem.delete(DatabaseBackup.backupPath(file))
}

/**
 * Finishes a swap a crash interrupted: with the main file missing its `.bak` set goes back. With both present the
 * `.bak` set is deleted only when the live database holds a local node, so a live file Room just created empty after a
 * failed put-back never costs the user the original.
 */
private fun recoverInterruptedRestore(dbFile: Path, fileSystem: FileSystem) {
    val asideFiles = DatabaseBackup.databaseFiles(dbFile).filter { fileSystem.exists(DatabaseBackup.backupPath(it)) }
    if (asideFiles.isEmpty()) return
    val putBack = !fileSystem.exists(dbFile) && fileSystem.exists(DatabaseBackup.backupPath(dbFile))
    if (!putBack && !hasLocalNode(dbFile)) {
        Logger.w { "Keeping the .bak of an interrupted restore beside a database with no local node" }
        return
    }
    for (file in asideFiles) {
        val aside = DatabaseBackup.backupPath(file)
        if (putBack) fileSystem.atomicMove(aside, file) else fileSystem.delete(aside)
    }
}

/** True when the database at [dbFile] has its local node row, which a database Room just created never has. */
internal fun hasLocalNode(dbFile: Path): Boolean = try {
    BundledSQLiteDriver().open(dbFile.toString(), SQLITE_OPEN_READONLY).use { connection ->
        (connection.firstColumn("SELECT count(*) FROM my_node")?.toLongOrNull() ?: 0L) > 0L
    }
} catch (e: SQLiteException) {
    Logger.w(e) { "Could not read the local node from a restored database" }
    false
}
