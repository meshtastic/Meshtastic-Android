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
package org.meshtastic.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.datastore.di.asCorePreferencesDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class BootloaderWarningDataSourceTest {
    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataSource: BootloaderWarningDataSource
    private lateinit var logs: CapturingLogWriter

    private val testScope = TestScope(UnconfinedTestDispatcher())

    @BeforeTest
    fun setup() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bootloaderWarningTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        dataSource = BootloaderWarningDataSource(dataStore.asCorePreferencesDataStore())
        logs = CapturingLogWriter.install()
    }

    @AfterTest
    fun tearDown() {
        CapturingLogWriter.uninstall()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun `a dismissed address reads back as dismissed`() = testScope.runTest {
        dataSource.dismiss("AA:BB:CC:00:00:01")

        assertTrue(dataSource.isDismissed("AA:BB:CC:00:00:01"))
        assertFalse(dataSource.isDismissed("AA:BB:CC:00:00:02"))
    }

    @Test
    fun `unreadable stored value counts as not dismissed and is never logged`() = testScope.runTest {
        dataStore.edit { it[stringPreferencesKey("dismissed-bootloader-addresses")] = """["AA:BB:CC:11:22:33",""" }

        assertFalse(dataSource.isDismissed("AA:BB:CC:11:22:33"))
        logs.assertNotLogged("AA:BB:CC:11:22:33")
    }

    @Test
    fun `dismiss replaces an unreadable stored value with a readable one`() = testScope.runTest {
        dataStore.edit { it[stringPreferencesKey("dismissed-bootloader-addresses")] = """["AA:BB:CC:11:22:33",""" }

        dataSource.dismiss("AA:BB:CC:00:00:01")

        assertTrue(dataSource.isDismissed("AA:BB:CC:00:00:01"))
    }
}
