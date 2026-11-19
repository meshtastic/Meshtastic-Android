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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseManagerBackupTargetTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    private val dbA = buildDbName("addrA")
    private val dbB = buildDbName("addrB")

    private suspend fun setLastUsed(vararg usage: Pair<String, Long>) {
        armableDs.edit { prefs -> usage.forEach { (name, at) -> prefs[longPreferencesKey("db_last_used:$name")] = at } }
    }

    @Test
    fun `a selected device is its own backup target`() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")

        assertEquals(dbA, manager.backupTargetDatabaseName())
    }

    @Test
    fun `with no device selected the most recently used device database is the target`() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        manager.switchActiveDatabase("addrB")
        manager.switchActiveDatabase(null)
        manager.existingDbNamesForTest = listOf(DatabaseConstants.DEFAULT_DB_NAME, dbA, dbB)

        setLastUsed(dbA to 1_000L, dbB to 2_000L)
        assertEquals(dbB, manager.backupTargetDatabaseName())

        setLastUsed(dbA to 3_000L)
        assertEquals(dbA, manager.backupTargetDatabaseName())
    }

    @Test
    fun `with no device database there is no target`() = runTest(testDispatcher) {
        manager.switchActiveDatabase(null)
        manager.existingDbNamesForTest = listOf(DatabaseConstants.DEFAULT_DB_NAME)

        assertNull(manager.backupTargetDatabaseName())
    }
}
