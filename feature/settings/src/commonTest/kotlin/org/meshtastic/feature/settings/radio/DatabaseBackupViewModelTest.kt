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
package org.meshtastic.feature.settings.radio

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.database.DatabaseBackupOperations
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.ui.util.AlertManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseBackupViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val radioController = FakeRadioController()
    private val radioPrefs = FakeRadioPrefs()
    private val alertManager = AlertManager()
    private val backupService: DatabaseBackupOperations = mock(MockMode.autofill)
    private val fileService: FileService = mock(MockMode.autofill)

    private lateinit var viewModel: DatabaseBackupViewModel
    private var readyCount = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel =
            DatabaseBackupViewModel(
                backupService = backupService,
                fileService = fileService,
                nodeRepository = FakeNodeRepository(),
                radioController = radioController,
                radioPrefs = radioPrefs,
                alertManager = alertManager,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `proceeds immediately without a dialog when disconnected`() {
        radioController.setConnectionState(ConnectionState.Disconnected)

        viewModel.runWhenDisconnected { readyCount++ }

        assertEquals(1, readyCount)
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun `connected shows a dialog and does nothing until it is confirmed`() {
        radioController.setConnectionState(ConnectionState.Connected)

        viewModel.runWhenDisconnected { readyCount++ }

        assertNotNull(alertManager.currentAlert.value)
        assertEquals(0, readyCount)
        assertNull(radioController.lastSetDeviceAddress)
    }

    @Test
    fun `confirming disconnects the radio and then proceeds`() {
        radioController.setConnectionState(ConnectionState.Connected)
        radioPrefs.setDevName("Meshtastic_1234")
        var addressWhenReady: String? = null
        viewModel.runWhenDisconnected {
            readyCount++
            addressWhenReady = radioController.lastSetDeviceAddress
        }

        alertManager.currentAlert.value?.onConfirm?.invoke()

        assertEquals("n", addressWhenReady)
        assertEquals(1, readyCount)
        assertNull(radioPrefs.devName.value)
    }

    @Test
    fun `dismissing the dialog leaves the radio connected and does not proceed`() {
        radioController.setConnectionState(ConnectionState.Connected)
        viewModel.runWhenDisconnected { readyCount++ }

        alertManager.currentAlert.value?.onDismiss?.invoke()

        assertEquals(0, readyCount)
        assertNull(radioController.lastSetDeviceAddress)
    }
}
