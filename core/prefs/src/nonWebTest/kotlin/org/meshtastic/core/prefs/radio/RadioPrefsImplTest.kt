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
package org.meshtastic.core.prefs.radio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.asRadioDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class RadioPrefsImplTest {
    private lateinit var tmpDir: Path
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var dispatchers: CoroutineDispatchers

    @BeforeTest
    fun setup() {
        // Standard, not Unconfined: the store must not have loaded when the first read happens.
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "radioPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = testScope, produceFile = { tmpDir / "radio.preferences_pb" })

    @Test
    fun `awaitDevAddr is null when no address was ever saved`() = testScope.runTest {
        val prefs = RadioPrefsImpl(store().asRadioDataStore(), dispatchers)

        assertNull(prefs.awaitDevAddr())
    }

    @Test
    fun `awaitDevAddr returns the saved address before devAddr has loaded`() = testScope.runTest {
        val radio = store()
        radio.edit { it[RadioPrefsImpl.KEY_DEV_ADDR_PREF] = "x00:11:22:33:44:55" }

        val prefs = RadioPrefsImpl(radio.asRadioDataStore(), dispatchers)

        assertNull(prefs.devAddr.value)
        assertEquals("x00:11:22:33:44:55", prefs.awaitDevAddr())
    }
}
