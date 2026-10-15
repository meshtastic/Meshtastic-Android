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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.DatabaseBackupOperations
import org.meshtastic.core.database.RestoreStageResult
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
    private val backupService = FakeBackupOperations(radioController)
    private val fileService = FakeFileService()

    /** Reports the device database until the radio is deselected, as the real switch does, and records targets. */
    private class FakeBackupOperations(private val radioController: FakeRadioController) : DatabaseBackupOperations {
        val exported = mutableListOf<String>()
        val staged = mutableListOf<String>()

        override val activeDatabaseName: String
            get() = if (radioController.lastSetDeviceAddress == "n") DEFAULT_DB else DEVICE_DB

        override suspend fun export(dbName: String, sink: BufferedSink): Long {
            exported += dbName
            return 0
        }

        override suspend fun stageRestore(dbName: String, source: BufferedSource): RestoreStageResult {
            staged += dbName
            return RestoreStageResult.Staged
        }
    }

    private class FakeFileService : FileService {
        override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean {
            block(Buffer())
            return true
        }

        override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean {
            block(Buffer())
            return true
        }
    }

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

    /** Result messages load string resources off the test dispatcher, so wait in real time for the alert. */
    private suspend fun awaitResultAlert() = withContext(Dispatchers.Default) {
        withTimeout(RESULT_TIMEOUT_MS) {
            while (alertManager.currentAlert.value?.message == null) delay(POLL_MS)
        }
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
    fun `a sleeping or connecting radio asks to disconnect before proceeding`() {
        for (state in listOf(ConnectionState.DeviceSleep, ConnectionState.Connecting)) {
            alertManager.dismissAlert()
            radioController.setConnectionState(state)

            viewModel.runWhenDisconnected { readyCount++ }

            assertNotNull(alertManager.currentAlert.value, "no dialog for $state")
            assertEquals(0, readyCount)
            assertNull(radioController.lastSetDeviceAddress)
        }
    }

    @Test
    fun `backup and restore after disconnecting target the database selected before it`() = runTest {
        val uri = CommonUri.parse("file:///backup.db")
        radioController.setConnectionState(ConnectionState.Connected)
        viewModel.runWhenDisconnected { readyCount++ }
        alertManager.currentAlert.value?.onConfirm?.invoke()

        alertManager.dismissAlert()
        viewModel.backup(uri)
        awaitResultAlert()
        alertManager.dismissAlert()
        viewModel.restore(uri)
        awaitResultAlert()

        assertEquals("n", radioController.lastSetDeviceAddress)
        assertEquals(listOf(DEVICE_DB), backupService.exported)
        assertEquals(listOf(DEVICE_DB), backupService.staged)
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

private const val DEVICE_DB = "meshtastic_database_device"
private const val DEFAULT_DB = "meshtastic_database"
private const val RESULT_TIMEOUT_MS = 5_000L
private const val POLL_MS = 10L
