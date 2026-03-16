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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.NotificationPrefs

class NotificationPrefsTest {
    @get:Rule val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var notificationPrefs: NotificationPrefs
    private lateinit var dispatchers: CoroutineDispatchers

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tmpFolder.newFile("test.preferences_pb") },
            )
        dispatchers = mockk { every { default } returns testDispatcher }
        notificationPrefs = NotificationPrefsImpl(dataStore, dispatchers)
    }

    @Test fun `messagesEnabled defaults to true`() = testScope.runTest { assertTrue(notificationPrefs.messagesEnabled.value) }

    @Test fun `nodeEventsEnabled defaults to true`() = testScope.runTest { assertTrue(notificationPrefs.nodeEventsEnabled.value) }

    @Test fun `lowBatteryEnabled defaults to true`() = testScope.runTest { assertTrue(notificationPrefs.lowBatteryEnabled.value) }

    @Test
    fun `setting messagesEnabled updates preference`() = testScope.runTest {
        notificationPrefs.setMessagesEnabled(false)
        assertFalse(notificationPrefs.messagesEnabled.value)
    }

    @Test
    fun `setting nodeEventsEnabled updates preference`() = testScope.runTest {
        notificationPrefs.setNodeEventsEnabled(false)
        assertFalse(notificationPrefs.nodeEventsEnabled.value)
    }

    @Test
    fun `setting lowBatteryEnabled updates preference`() = testScope.runTest {
        notificationPrefs.setLowBatteryEnabled(false)
        assertFalse(notificationPrefs.lowBatteryEnabled.value)
    }
}
