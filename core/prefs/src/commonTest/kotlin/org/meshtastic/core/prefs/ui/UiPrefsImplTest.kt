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
package org.meshtastic.core.prefs.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class UiPrefsImplTest {
    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: UiPrefsImpl
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "uiPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        val dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        prefs = UiPrefsImpl(dataStore, dispatchers)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun `explicit selected connection transport wins over legacy booleans`() = testScope.runTest {
        dataStore.edit {
            it[UiPrefsImpl.KEY_SELECTED_CONNECTION_TRANSPORT] = DeviceType.USB.name
            it[UiPrefsImpl.KEY_SHOW_BLE_TRANSPORT] = true
            it[UiPrefsImpl.KEY_SHOW_NETWORK_TRANSPORT] = true
            it[UiPrefsImpl.KEY_SHOW_USB_TRANSPORT] = false
        }

        assertEquals(DeviceType.USB, prefs.selectedConnectionTransport.value)
    }

    @Test
    fun `invalid selected connection transport falls back to legacy booleans`() = testScope.runTest {
        dataStore.edit {
            it[UiPrefsImpl.KEY_SELECTED_CONNECTION_TRANSPORT] = "WIFI"
            it[UiPrefsImpl.KEY_SHOW_BLE_TRANSPORT] = false
            it[UiPrefsImpl.KEY_SHOW_NETWORK_TRANSPORT] = true
            it[UiPrefsImpl.KEY_SHOW_USB_TRANSPORT] = true
        }

        assertEquals(DeviceType.TCP, prefs.selectedConnectionTransport.value)
    }

    @Test
    fun `selected connection transport is null when no legacy transport keys exist`() =
        testScope.runTest { assertNull(prefs.selectedConnectionTransport.value) }

    @Test
    fun `legacy selected connection transport defaults to BLE when all transports are visible`() = testScope.runTest {
        dataStore.edit {
            it[UiPrefsImpl.KEY_SHOW_BLE_TRANSPORT] = true
            it[UiPrefsImpl.KEY_SHOW_NETWORK_TRANSPORT] = true
            it[UiPrefsImpl.KEY_SHOW_USB_TRANSPORT] = true
        }

        assertEquals(DeviceType.BLE, prefs.selectedConnectionTransport.value)
    }

    @Test
    fun `legacy selected connection transport chooses TCP when BLE is hidden`() = testScope.runTest {
        dataStore.edit {
            it[UiPrefsImpl.KEY_SHOW_BLE_TRANSPORT] = false
            it[UiPrefsImpl.KEY_SHOW_NETWORK_TRANSPORT] = true
            it[UiPrefsImpl.KEY_SHOW_USB_TRANSPORT] = true
        }

        assertEquals(DeviceType.TCP, prefs.selectedConnectionTransport.value)
    }

    @Test
    fun `legacy selected connection transport chooses USB when only USB is visible`() = testScope.runTest {
        dataStore.edit {
            it[UiPrefsImpl.KEY_SHOW_BLE_TRANSPORT] = false
            it[UiPrefsImpl.KEY_SHOW_NETWORK_TRANSPORT] = false
            it[UiPrefsImpl.KEY_SHOW_USB_TRANSPORT] = true
        }

        assertEquals(DeviceType.USB, prefs.selectedConnectionTransport.value)
    }

    @Test
    fun `firmware update notification keys persist without duplicates`() = testScope.runTest {
        prefs.recordFirmwareUpdateNotificationKey("firmware-update-notified:node:target:2.8.0")
        prefs.recordFirmwareUpdateNotificationKey("firmware-update-notified:node:target:2.8.0")
        prefs.recordFirmwareUpdateNotificationKey("firmware-update-notified:node:target:2.9.0")

        assertEquals(
            setOf("firmware-update-notified:node:target:2.8.0", "firmware-update-notified:node:target:2.9.0"),
            prefs.firmwareUpdateNotificationKeys.value,
        )
    }
}
