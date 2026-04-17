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
package org.meshtastic.feature.firmware

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FirmwareReleaseRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration-style tests that wire a real [FirmwareUpdateViewModel] to fake/mock collaborators and verify end-to-end
 * state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareUpdateIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    private val firmwareReleaseRepository: FirmwareReleaseRepository = mock(MockMode.autofill)
    private val deviceHardwareRepository: DeviceHardwareRepository = mock(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val radioController = FakeRadioController()
    private val radioPrefs: RadioPrefs = mock(MockMode.autofill)
    private val bootloaderWarningDataSource: BootloaderWarningDataSource = mock(MockMode.autofill)
    private val firmwareUpdateManager: FirmwareUpdateManager = mock(MockMode.autofill)
    private val usbManager: FirmwareUsbManager = mock(MockMode.autofill)
    private val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)

    private val stableRelease = FirmwareRelease(id = "1", title = "2.5.0", zipUrl = "url", releaseNotes = "")
    private val hardware = DeviceHardware(hwModel = 1, architecture = "esp32", platformioTarget = "tbeam")

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { firmwareReleaseRepository.stableRelease } returns flowOf(stableRelease)
        every { firmwareReleaseRepository.alphaRelease } returns flowOf(stableRelease)
        every { radioPrefs.devAddr } returns MutableStateFlow("!1234abcd")
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)
        everySuspend { bootloaderWarningDataSource.isDismissed(any()) } returns false
        every { fileHandler.cleanupAllTemporaryFiles() } returns Unit
        everySuspend { fileHandler.deleteFile(any()) } returns Unit

        nodeRepository.setMyNodeInfo(
            TestDataFactory.createMyNodeInfo(myNodeNum = 123, firmwareVersion = "2.4.0", pioEnv = "tbeam"),
        )
        nodeRepository.setOurNode(
            TestDataFactory.createTestNode(
                num = 123,
                userId = "!1234abcd",
                hwModel = org.meshtastic.proto.HardwareModel.TLORA_V2,
            ),
        )
    }

    private fun createViewModel() = FirmwareUpdateViewModel(
        firmwareReleaseRepository,
        deviceHardwareRepository,
        nodeRepository,
        radioController,
        radioPrefs,
        bootloaderWarningDataSource,
        firmwareUpdateManager,
        usbManager,
        fileHandler,
        TestApplicationCoroutineScope(testDispatcher),
    )

    @Test
    fun `ViewModel initialises to Ready with release and device info`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertTrue(state.release != null, "Release should be available")
        assertTrue(state.currentFirmwareVersion != null, "Firmware version should be available")
    }

    @Test
    fun `startUpdate transitions through Updating to Success when manager succeeds`() = runTest {
        everySuspend { firmwareUpdateManager.startUpdate(any(), any(), any(), any()) }
            .calls {
                @Suppress("UNCHECKED_CAST")
                val updateState = it.args[3] as (FirmwareUpdateState) -> Unit
                updateState(FirmwareUpdateState.Updating(ProgressState()))
                updateState(FirmwareUpdateState.Success)
                null
            }

        val vm = createViewModel()
        advanceUntilIdle()
        vm.startUpdate()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            state is FirmwareUpdateState.Success ||
                state is FirmwareUpdateState.Verifying ||
                state is FirmwareUpdateState.VerificationFailed,
            "Expected post-success state, got: $state",
        )
    }

    @Test
    fun `startUpdate sets Error state when manager reports failure`() = runTest {
        everySuspend { firmwareUpdateManager.startUpdate(any(), any(), any(), any()) }
            .calls {
                @Suppress("UNCHECKED_CAST")
                val updateState = it.args[3] as (FirmwareUpdateState) -> Unit
                updateState(
                    FirmwareUpdateState.Error(org.meshtastic.core.resources.UiText.DynamicString("Transfer failed")),
                )
                null
            }

        val vm = createViewModel()
        advanceUntilIdle()
        vm.startUpdate()
        advanceUntilIdle()

        assertIs<FirmwareUpdateState.Error>(vm.state.value)
    }

    @Test
    fun `cancelUpdate returns ViewModel to Ready state`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.cancelUpdate()
        advanceUntilIdle()

        assertIs<FirmwareUpdateState.Ready>(vm.state.value)
    }
}
