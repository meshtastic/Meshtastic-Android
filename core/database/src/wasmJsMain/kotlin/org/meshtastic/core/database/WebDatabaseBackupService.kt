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

import androidx.room3.useWriterConnection
import co.touchlab.kermit.Logger
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException
import org.meshtastic.core.database.DatabaseBackup.Validation

/**
 * Web [DatabaseBackupOperations] over the `opfs-sahpool` pool. A restore is staged beside the database and the worker
 * swaps it in before that database next opens, which on web is the next page load.
 */
class WebDatabaseBackupService(private val provider: DatabaseProvider) : DatabaseBackupOperations {

    /** Web keeps one database for every device. */
    override suspend fun backupTargetName(): String = DatabaseConstants.DEFAULT_DB_NAME

    /** Holding Room's only connection keeps an open transaction from leaving the copied file half-written. */
    override suspend fun export(dbName: String, sink: BufferedSink): Long {
        val path = getDatabaseFile(dbName).toString()
        val bytes =
            provider.withDb { db -> db.useWriterConnection { SqliteWasmWorker.exportFile(path) } }
                ?: throw IOException("No web database at $path")
        sink.write(bytes)
        return bytes.size.toLong()
    }

    override suspend fun stageRestore(dbName: String, source: BufferedSource): RestoreStageResult {
        val headerVerdict = DatabaseBackup.validateHeader(DatabaseBackup.readHeader(source))
        if (headerVerdict != Validation.Valid) return RestoreStageResult.Invalid(headerVerdict)
        val dbFile = getDatabaseFile(dbName)
        val temp = DatabaseBackup.stagingTempPath(dbFile).toString()
        return try {
            val verdict = importAndValidate(temp, source.readByteArray())
            if (verdict == Validation.Valid) {
                SqliteWasmWorker.moveFile(temp, DatabaseBackup.stagedPath(dbFile).toString())
                RestoreStageResult.Staged
            } else {
                SqliteWasmWorker.deleteFileDetached(temp)
                RestoreStageResult.Invalid(verdict)
            }
        } catch (e: IOException) {
            Logger.w(e) { "Reading the web database restore failed" }
            RestoreStageResult.Failed(e)
        } catch (e: WebDatabaseFileException) {
            Logger.w(e) { "Staging the web database restore failed" }
            SqliteWasmWorker.deleteFileDetached(temp)
            RestoreStageResult.Failed(e)
        }
    }

    /** Copies [bytes] into the pool as [temp], then has SQLite check the schema version and integrity there. */
    private suspend fun importAndValidate(temp: String, bytes: ByteArray): Validation {
        val inspection =
            try {
                SqliteWasmWorker.importFile(temp, bytes)
                SqliteWasmWorker.inspectDatabase(temp)
            } catch (e: WebDatabaseFileException) {
                // sqlite-wasm refuses a file whose size is not whole pages, and SQLite one it cannot open.
                Logger.w(e) { "The web database restore is not a usable database" }
                null
            }
        return when {
            inspection == null -> Validation.Corrupt

            inspection.userVersion != DatabaseBackup.SCHEMA_VERSION ->
                Validation.SchemaMismatch(inspection.userVersion, DatabaseBackup.SCHEMA_VERSION)

            inspection.integrity == "ok" -> Validation.Valid

            else -> Validation.Corrupt
        }
    }
}
