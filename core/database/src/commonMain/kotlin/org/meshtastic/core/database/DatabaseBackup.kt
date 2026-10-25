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

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.BufferedSource
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Instant

/** Pure rules for backing up and restoring the app database: file naming, header validation and staging paths. */
object DatabaseBackup {
    /** Must equal `@Database(version)` on [MeshtasticDatabase]; a jvmTest opens a real database and checks it. */
    const val SCHEMA_VERSION: Int = 66

    const val FILE_NAME_PREFIX: String = "meshtastic_backup"
    const val FILE_EXTENSION: String = "db"
    const val MIME_TYPE: String = "application/octet-stream"
    const val STAGED_SUFFIX: String = ".restore"
    const val STAGING_TEMP_SUFFIX: String = ".restore.tmp"
    const val EXPORT_TEMP_SUFFIX: String = ".export.tmp"
    const val BACKUP_SUFFIX: String = ".bak"

    private const val UNKNOWN_DEVICE: String = "device"
    private const val SQLITE_MAGIC: String = "SQLite format 3\u0000"
    private const val USER_VERSION_OFFSET: Int = 60
    private const val BYTES_PER_INT: Int = 4
    private const val BITS_PER_BYTE: Int = 8
    private const val BYTE_MASK: Int = 0xFF
    private const val MONTH_DIGITS: Int = 2

    /** Bytes of the SQLite file header needed to read the magic string and `user_version`. */
    const val HEADER_SIZE: Int = 100

    sealed interface Validation {
        data object Valid : Validation

        data object NotSqlite : Validation

        data class SchemaMismatch(val found: Int, val expected: Int) : Validation

        /** SQLite cannot open the file, or `PRAGMA integrity_check` reports damage such as a truncated file. */
        data object Corrupt : Validation
    }

    /**
     * Name for a backup of the database belonging to [deviceId], dated from [now]. A device id that is not the hex form
     * current ingestion produces is replaced by a fixed word so the name never carries arbitrary characters.
     */
    fun fileName(deviceId: String?, now: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val id = validDeviceIdOrNull(deviceId)?.lowercase() ?: UNKNOWN_DEVICE
        val date = now.toLocalDateTime(timeZone).date
        val month = date.month.ordinal + 1
        val stamp =
            "${date.year}-${month.toString().padStart(MONTH_DIGITS, '0')}-" +
                date.day.toString().padStart(MONTH_DIGITS, '0')
        return "${FILE_NAME_PREFIX}_${id}_$stamp.$FILE_EXTENSION"
    }

    /** Checks the SQLite magic string and that `user_version` (where Room stores the schema version) matches. */
    fun validateHeader(header: ByteArray, expectedVersion: Int = SCHEMA_VERSION): Validation {
        val isSqlite =
            header.size >= HEADER_SIZE && header.copyOfRange(0, SQLITE_MAGIC.length).decodeToString() == SQLITE_MAGIC
        if (!isSqlite) return Validation.NotSqlite
        var found = 0
        for (i in 0 until BYTES_PER_INT) {
            found = (found shl BITS_PER_BYTE) or (header[USER_VERSION_OFFSET + i].toInt() and BYTE_MASK)
        }
        return if (found == expectedVersion) Validation.Valid else Validation.SchemaMismatch(found, expectedVersion)
    }

    /** Reads up to [HEADER_SIZE] bytes without consuming [source]. */
    internal fun readHeader(source: BufferedSource): ByteArray {
        val peeked = source.peek()
        peeked.request(HEADER_SIZE.toLong())
        return peeked.readByteArray(minOf(peeked.buffer.size, HEADER_SIZE.toLong()))
    }

    /** Where a validated restore for the database at [dbFile] waits until that database is next opened. */
    fun stagedPath(dbFile: Path): Path = sibling(dbFile, STAGED_SUFFIX)

    internal fun stagingTempPath(dbFile: Path): Path = sibling(dbFile, STAGING_TEMP_SUFFIX)

    internal fun exportTempPath(dbFile: Path): Path = sibling(dbFile, EXPORT_TEMP_SUFFIX)

    /** The main, `-wal` and `-shm` files of the database at [dbFile], the set a restore moves as one. */
    internal fun databaseFiles(dbFile: Path): List<Path> =
        listOf(dbFile, sibling(dbFile, "-wal"), sibling(dbFile, "-shm"))

    /** Where [file] is kept while a staged restore replaces it. */
    internal fun backupPath(file: Path): Path = sibling(file, BACKUP_SUFFIX)

    /** The file next to [dbFile] named after it plus [suffix]; a parentless path resolves in the working directory. */
    internal fun sibling(dbFile: Path, suffix: String): Path {
        val name = dbFile.name + suffix
        return dbFile.parent?.div(name) ?: name.toPath()
    }
}
