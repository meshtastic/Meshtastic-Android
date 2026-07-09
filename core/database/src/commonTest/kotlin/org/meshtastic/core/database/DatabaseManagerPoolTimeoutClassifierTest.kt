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

class DatabaseManagerPoolTimeoutClassifierTest {

    @Test
    fun `matches Room reader connection acquire timeout`() {
        val e = Exception("Error code: 5, message: Timed out attempting to acquire a reader connection.")
        assertTrue(DatabaseManager.isDbPoolAcquireTimeoutException(e))
    }

    @Test
    fun `matches Room writer connection acquire timeout`() {
        val e = Exception("Error code: 5, message: Timed out attempting to acquire a writer connection.")
        assertTrue(DatabaseManager.isDbPoolAcquireTimeoutException(e))
    }

    @Test
    fun `matches when wrapped in a causal chain`() {
        val root = Exception("Error code: 5, message: Timed out attempting to acquire a reader connection.")
        val wrapper = RuntimeException("DAO query failed", root)
        assertTrue(DatabaseManager.isDbPoolAcquireTimeoutException(wrapper))
    }

    @Test
    fun `rejects generic connection locked`() {
        val e = RuntimeException("connection locked by transport layer")
        assertFalse(DatabaseManager.isDbPoolAcquireTimeoutException(e))
    }

    @Test
    fun `rejects non-DB timeout`() {
        val e = RuntimeException("Timed out attempting to acquire a BLE GATT connection")
        assertFalse(DatabaseManager.isDbPoolAcquireTimeoutException(e))
    }

    @Test
    fun `rejects plain database locked without acquire timeout`() {
        val e = Exception("database is locked")
        assertFalse(DatabaseManager.isDbPoolAcquireTimeoutException(e))
    }
}
