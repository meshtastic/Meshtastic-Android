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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DatabaseBackupTest {
    private val deviceId = "0123456789ABCDEF0123456789ABCDEF"

    private fun header(version: Int, magic: String = "SQLite format 3\u0000", size: Int = 100): ByteArray {
        val bytes = ByteArray(size)
        magic.encodeToByteArray().copyInto(bytes)
        if (size >= 64) {
            bytes[60] = (version shr 24).toByte()
            bytes[61] = (version shr 16).toByte()
            bytes[62] = (version shr 8).toByte()
            bytes[63] = version.toByte()
        }
        return bytes
    }

    @Test
    fun `file name carries the lowercase device id and the injected date`() {
        val now = Instant.parse("2026-03-07T12:00:00Z")
        assertEquals(
            "meshtastic_backup_0123456789abcdef0123456789abcdef_2026-03-07.db",
            DatabaseBackup.fileName(deviceId, now, TimeZone.UTC),
        )
    }

    @Test
    fun `file name date follows the supplied time zone`() {
        val now = Instant.parse("2026-12-31T23:30:00Z")
        assertEquals(
            "meshtastic_backup_device_2027-01-01.db",
            DatabaseBackup.fileName(null, now, TimeZone.of("Pacific/Auckland")),
        )
    }

    @Test
    fun `file name replaces an id that is not hex so no arbitrary characters reach the path`() {
        val now = Instant.parse("2026-03-07T12:00:00Z")
        assertEquals(
            "meshtastic_backup_device_2026-03-07.db",
            DatabaseBackup.fileName("../../etc/passwd", now, TimeZone.UTC),
        )
    }

    @Test
    fun `header with the current schema version is valid`() {
        assertEquals(
            DatabaseBackup.Validation.Valid,
            DatabaseBackup.validateHeader(header(DatabaseBackup.SCHEMA_VERSION)),
        )
    }

    @Test
    fun `header from another schema version reports both versions`() {
        assertEquals(
            DatabaseBackup.Validation.SchemaMismatch(found = 63, expected = DatabaseBackup.SCHEMA_VERSION),
            DatabaseBackup.validateHeader(header(63)),
        )
    }

    @Test
    fun `header without the SQLite magic is rejected`() {
        assertEquals(
            DatabaseBackup.Validation.NotSqlite,
            DatabaseBackup.validateHeader(header(DatabaseBackup.SCHEMA_VERSION, magic = "not a database!!")),
        )
    }

    @Test
    fun `a file shorter than the header is rejected`() {
        assertEquals(DatabaseBackup.Validation.NotSqlite, DatabaseBackup.validateHeader(header(1, size = 40)))
    }
}
