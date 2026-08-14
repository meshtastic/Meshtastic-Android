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
package org.meshtastic.core.prefs.mesh

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.MeshDataStore
import org.meshtastic.core.prefs.di.asMeshDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class MeshPrefsImplTest {
    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testScope: TestScope
    private lateinit var dispatchers: CoroutineDispatchers

    @BeforeTest
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "meshPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun `await device address waits for persisted data instead of returning the flow default`() = testScope.runTest {
        val persistedAddress = "xAA:BB:CC:DD:EE:FF"
        dataStore.edit { preferences -> preferences[MeshPrefsImpl.KEY_DEVICE_ADDRESS_PREF] = persistedAddress }
        val loadGate = CompletableDeferred<Unit>()
        val delegate = dataStore.asMeshDataStore()
        val delayedDataStore =
            object : MeshDataStore by delegate {
                override val data = delegate.data.onStart { loadGate.await() }
            }
        val prefs = MeshPrefsImpl(delayedDataStore, dispatchers)

        assertEquals("n", prefs.deviceAddress.value)
        val snapshot = async { prefs.awaitDeviceAddress() }
        assertFalse(snapshot.isCompleted)

        loadGate.complete(Unit)

        assertEquals(persistedAddress, snapshot.await())
    }
}
