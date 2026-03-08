/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.prefs.nodedisplay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.meshtastic.core.di.CoroutineDispatchers

class NodeDisplayNamePrefsTest {

    @get:Rule val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: NodeDisplayNamePrefsImpl
    private lateinit var dispatchers: CoroutineDispatchers

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tmpFolder.newFile("node_display_names.preferences_pb") },
            )
        dispatchers = mockk { every { default } returns testDispatcher }
        prefs = NodeDisplayNamePrefsImpl(dataStore, dispatchers)
    }

    @Test
    fun `displayNames defaults to empty map`() = testScope.runTest {
        assertEquals(emptyMap<Int, String>(), prefs.displayNames.value)
    }

    @Test
    fun `setDisplayName adds name and it is readable`() = testScope.runTest {
        prefs.setDisplayName(123, "My Node")
        assertEquals(mapOf(123 to "My Node"), prefs.displayNames.value)
    }

    @Test
    fun `setDisplayName null removes entry`() = testScope.runTest {
        prefs.setDisplayName(123, "My Node")
        prefs.setDisplayName(123, null)
        assertEquals(emptyMap<Int, String>(), prefs.displayNames.value)
    }

    @Test
    fun `setDisplayName blank removes entry`() = testScope.runTest {
        prefs.setDisplayName(456, "  ")
        assertEquals(emptyMap<Int, String>(), prefs.displayNames.value)
    }

    @Test
    fun `multiple nodes can have display names`() = testScope.runTest {
        prefs.setDisplayName(1, "Alice")
        prefs.setDisplayName(2, "Bob")
        assertEquals(mapOf(1 to "Alice", 2 to "Bob"), prefs.displayNames.value)
    }

    @Test
    fun `trimmed name is stored`() = testScope.runTest {
        prefs.setDisplayName(99, "  Trimmed  ")
        assertEquals(mapOf(99 to "Trimmed"), prefs.displayNames.value)
    }
}
