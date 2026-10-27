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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseManagerClassifierTest {

    @Test
    fun `treats a malformed database image as corrupt`() {
        val e = Exception("Error code: 11, message: database disk image is malformed")
        assertTrue(DatabaseManager.isDbCorruptException(RuntimeException("migration failed", e)))
    }

    @Test
    fun `treats a file that is not a database as corrupt`() {
        assertTrue(DatabaseManager.isDbCorruptException(Exception("Error code: 26, message: file is not a database")))
    }

    @Test
    fun `does not treat an FTS5 index desync as corrupt`() {
        val e = Exception("Error code: 267, message: database disk image is malformed")
        assertFalse(DatabaseManager.isDbCorruptException(e))
    }

    @Test
    fun `lists main database files only`() {
        assertTrue(DatabaseManager.isDatabaseFileName("meshtastic_database_abc"))
        assertTrue(DatabaseManager.isDatabaseFileName("meshtastic_database_abc.db"))
        assertFalse(DatabaseManager.isDatabaseFileName("meshtastic_database_abc-wal"))
        assertFalse(DatabaseManager.isDatabaseFileName("meshtastic_database_abc.corrupt-1700000000000"))
        assertFalse(DatabaseManager.isDatabaseFileName("meshtastic_database_abc-wal.corrupt-1700000000000"))
        assertFalse(DatabaseManager.isDatabaseFileName("other.db"))
    }

    @Test
    fun `does not treat a locked database as corrupt`() {
        assertFalse(DatabaseManager.isDbCorruptException(Exception("Error code: 5, message: database is locked")))
    }
}
