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
package org.meshtastic.core.prefs.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.prefs.di.asUiDataStore
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.repository.LiveUpdatePrefs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class LiveUpdatePrefsTest {
    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: LiveUpdatePrefs

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "liveUpdatePrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        val appScope = object : ApplicationCoroutineScope, CoroutineScope by CoroutineScope(testDispatcher) {}
        prefs = LiveUpdatePrefsImpl(dataStore.asUiDataStore(), appScope)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test fun `mode defaults to off`() = testScope.runTest { assertEquals(LiveUpdateMode.Off, prefs.mode.value) }

    @Test
    fun `each mode round trips`() = testScope.runTest {
        prefs.setMode(LiveUpdateMode.MeshStatus)
        assertEquals(LiveUpdateMode.MeshStatus, prefs.mode.value)

        prefs.setMode(LiveUpdateMode.TrackNode(-12345))
        assertEquals(LiveUpdateMode.TrackNode(-12345), prefs.mode.value)

        prefs.setMode(LiveUpdateMode.Off)
        assertEquals(LiveUpdateMode.Off, prefs.mode.value)
    }

    @Test
    fun `an unreadable stored value decodes to off`() {
        assertEquals(LiveUpdateMode.Off, LiveUpdateMode.decode("node:abc"))
        assertEquals(LiveUpdateMode.Off, LiveUpdateMode.decode("something"))
        assertEquals(LiveUpdateMode.Off, LiveUpdateMode.decode(null))
    }
}
