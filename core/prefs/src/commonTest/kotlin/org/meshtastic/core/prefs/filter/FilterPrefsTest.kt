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
package org.meshtastic.core.prefs.filter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.FilterPrefs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FilterPrefsTest {
    private lateinit var tmpDir: Path

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var filterPrefs: FilterPrefs
    private lateinit var dispatchers: CoroutineDispatchers

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "filterPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        filterPrefs = FilterPrefsImpl(dataStore, dispatchers)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test fun `filterEnabled defaults to false`() = testScope.runTest { assertFalse(filterPrefs.filterEnabled.value) }

    @Test
    fun `filterWords defaults to empty set`() =
        testScope.runTest { assertTrue(filterPrefs.filterWords.value.isEmpty()) }

    @Test
    fun `setting filterEnabled updates preference`() = testScope.runTest {
        filterPrefs.setFilterEnabled(true)
        assertTrue(filterPrefs.filterEnabled.value)
    }

    @Test
    fun `setting filterWords updates preference`() = testScope.runTest {
        val words = setOf("test", "word")
        filterPrefs.setFilterWords(words)
        assertEquals(words, filterPrefs.filterWords.value)
    }
}
