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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.NotificationPrefs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NotificationPrefsTest {
    private lateinit var tmpDir: Path

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var notificationPrefs: NotificationPrefs
    private lateinit var dispatchers: CoroutineDispatchers

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "notificationPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        notificationPrefs = NotificationPrefsImpl(dataStore, dispatchers)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun `messagesEnabled defaults to true`() = testScope.runTest { assertTrue(notificationPrefs.messagesEnabled.value) }

    @Test
    fun `nodeEventsEnabled defaults to true`() =
        testScope.runTest { assertTrue(notificationPrefs.nodeEventsEnabled.value) }

    @Test
    fun `lowBatteryEnabled defaults to true`() =
        testScope.runTest { assertTrue(notificationPrefs.lowBatteryEnabled.value) }

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

    @Test
    fun `geofenceAlertOptIns defaults to empty`() =
        testScope.runTest { assertTrue(notificationPrefs.geofenceAlertOptIns.value.isEmpty()) }

    @Test
    fun `geofenceAlertOptIns adds and removes waypoint ids`() = testScope.runTest {
        notificationPrefs.setGeofenceAlertOptIn(42, enabled = true)
        notificationPrefs.setGeofenceAlertOptIn(7, enabled = true)
        assertEquals(setOf(7, 42), notificationPrefs.geofenceAlertOptIns.value)

        notificationPrefs.setGeofenceAlertOptIn(42, enabled = false)
        assertEquals(setOf(7), notificationPrefs.geofenceAlertOptIns.value)
    }

    @Test
    fun `geofenceAlertOptIns caps size and evicts oldest`() = testScope.runTest {
        val max = NotificationPrefsImpl.MAX_GEOFENCE_OPT_INS
        (1..max + 2).forEach { notificationPrefs.setGeofenceAlertOptIn(it, enabled = true) }

        val ids = notificationPrefs.geofenceAlertOptIns.value
        assertEquals(max, ids.size)
        assertFalse(1 in ids) // oldest two evicted
        assertFalse(2 in ids)
        assertTrue(max + 2 in ids) // newest kept
    }

    @Test
    fun `geofenceAlertOptIns retoggle refreshes eviction order`() = testScope.runTest {
        val max = NotificationPrefsImpl.MAX_GEOFENCE_OPT_INS
        (1..max).forEach { notificationPrefs.setGeofenceAlertOptIn(it, enabled = true) } // 1 = oldest

        notificationPrefs.setGeofenceAlertOptIn(
            1,
            enabled = true,
        ) // re-toggle → 1 becomes most-recent, 2 now oldest
        notificationPrefs.setGeofenceAlertOptIn(max + 1, enabled = true) // forces one eviction

        val ids = notificationPrefs.geofenceAlertOptIns.value
        assertEquals(max, ids.size)
        assertTrue(1 in ids) // retoggled id survived
        assertFalse(2 in ids) // 2 became the oldest and was evicted
        assertTrue(max + 1 in ids)
    }
}
