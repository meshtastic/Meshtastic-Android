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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.state.ExcludedModulesUnlock
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FirmwareReleaseRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_battery_low
import org.meshtastic.core.resources.firmware_update_unknown_hardware
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [FirmwareUpdateViewModel] covering initialization, update methods, error paths, and bootloader warnings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareUpdateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val firmwareReleaseRepository: FirmwareReleaseRepository = mock(MockMode.autofill)
    private val deviceHardwareRepository: DeviceHardwareRepository = mock(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val radioController = FakeRadioController()
    private val radioPrefs: RadioPrefs = mock(MockMode.autofill)
    private val bootloaderWarningDataSource: BootloaderWarningDataSource = mock(MockMode.autofill)
    private val firmwareRecoveryDataSource: FirmwareRecoveryDataSource = mock(MockMode.autofill)
    private val firmwareUpdateManager: FirmwareUpdateManager = mock(MockMode.autofill)
    private val usbManager: FirmwareUsbManager = mock(MockMode.autofill)
    private val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)

    private lateinit var viewModel: FirmwareUpdateViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Setup default mocks
        val release = FirmwareRelease(id = "1", title = "1.0.0", zipUrl = "url", releaseNotes = "notes")
        every { firmwareReleaseRepository.stableRelease } returns flowOf(release)
        every { firmwareReleaseRepository.alphaRelease } returns flowOf(release)

        every { radioPrefs.devAddr } returns MutableStateFlow("!1234abcd")

        val hardware = DeviceHardware(hwModel = 1, architecture = "esp32", platformioTarget = "tbeam")
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)

        everySuspend { bootloaderWarningDataSource.isDismissed(any()) } returns false

        // No stranded-device recovery record by default; recovery tests override this.
        every { firmwareRecoveryDataSource.pending } returns flowOf(null)

        // Setup node info
        nodeRepository.setMyNodeInfo(
            TestDataFactory.createMyNodeInfo(myNodeNum = 123, firmwareVersion = "0.9.0", pioEnv = "tbeam"),
        )
        val node =
            TestDataFactory.createTestNode(
                num = 123,
                userId = "!1234abcd",
                hwModel = org.meshtastic.proto.HardwareModel.TLORA_V2,
            )
        nodeRepository.setOurNode(node)

        // Setup file handler
        every { fileHandler.cleanupAllTemporaryFiles() } returns Unit
        everySuspend { fileHandler.deleteFile(any()) } returns Unit

        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = FirmwareUpdateViewModel(
        firmwareReleaseRepository,
        deviceHardwareRepository,
        nodeRepository,
        radioController,
        radioPrefs,
        bootloaderWarningDataSource,
        firmwareRecoveryDataSource,
        firmwareUpdateManager,
        usbManager,
        fileHandler,
        TestApplicationCoroutineScope(testDispatcher),
        ExcludedModulesUnlock(),
    )

    @Test
    fun `initialization checks for updates and transitions to Ready`() = runTest {
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is FirmwareUpdateState.Ready)
        assertEquals("1.0.0", state.release?.title)
        assertEquals("1234abcd", state.address) // drop(1)
        assertEquals("0.9.0", state.currentFirmwareVersion)
    }

    @Test
    fun `setReleaseType updates release flow`() = runTest {
        advanceUntilIdle() // let init finish

        val alphaRelease = FirmwareRelease(id = "2", title = "2.0.0-alpha", zipUrl = "url", releaseNotes = "notes")
        every { firmwareReleaseRepository.alphaRelease } returns flowOf(alphaRelease)

        viewModel.setReleaseType(FirmwareReleaseType.ALPHA)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is FirmwareUpdateState.Ready)
        assertEquals("2.0.0-alpha", state.release?.title)
    }

    @Test
    fun `startUpdate sets error if battery is too low`() = runTest {
        val node =
            TestDataFactory.createTestNode(
                num = 123,
                userId = "!1234abcd",
                hwModel = org.meshtastic.proto.HardwareModel.TLORA_V2,
                batteryLevel = 5,
            )
        nodeRepository.setOurNode(node)
        advanceUntilIdle()

        val currentState = viewModel.state.value
        assertTrue(currentState is FirmwareUpdateState.Ready, "Expected Ready state but was $currentState")

        viewModel.startUpdate()
        advanceUntilIdle()

        val errorState = viewModel.state.value
        assertTrue(errorState is FirmwareUpdateState.Error, "Expected Error state but was $errorState")
        val error = errorState.error
        assertTrue(error is UiText.Resource)
        assertEquals(Res.string.firmware_update_battery_low, error.res)
    }

    @Test
    fun `startUpdate transitions to Success if manager returns Success`() = runTest {
        advanceUntilIdle()

        // Mock with 4 arguments
        everySuspend { firmwareUpdateManager.startUpdate(any(), any(), any(), any()) }
            .calls {
                @Suppress("UNCHECKED_CAST")
                val updateState = it.args[3] as (FirmwareUpdateState) -> Unit
                updateState(FirmwareUpdateState.Success())
                null
            }

        viewModel.startUpdate()
        advanceUntilIdle()

        // Wait for verifyUpdateResult to hit its timeout and go to VerificationFailed
        val state = viewModel.state.value
        assertTrue(
            state is FirmwareUpdateState.Success ||
                state is FirmwareUpdateState.Verifying ||
                state is FirmwareUpdateState.VerificationFailed,
            "Final state was $state",
        )
    }

    @Test
    fun `cancelUpdate goes back to Ready`() = runTest {
        advanceUntilIdle()
        viewModel.cancelUpdate()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is FirmwareUpdateState.Ready)
    }

    @Test
    fun `dismissBootloaderWarningForCurrentDevice updates state`() = runTest {
        val hardware =
            DeviceHardware(
                hwModel = 1,
                architecture = "nrf52",
                platformioTarget = "tbeam",
                requiresBootloaderUpgradeForOta = true,
            )
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)

        // isBle() checks devAddr.value?.startsWith("x"), so use BLE-prefixed address
        every { radioPrefs.devAddr } returns MutableStateFlow("x1234abcd")

        nodeRepository.setMyNodeInfo(
            TestDataFactory.createMyNodeInfo(myNodeNum = 123, firmwareVersion = "0.9.0", pioEnv = "tbeam"),
        )

        everySuspend { bootloaderWarningDataSource.isDismissed(any()) } returns false
        everySuspend { bootloaderWarningDataSource.dismiss(any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        val readyState = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(readyState)
        assertTrue(readyState.showBootloaderWarning, "Bootloader warning should be shown for nrf52 over BLE")

        viewModel.dismissBootloaderWarningForCurrentDevice()
        advanceUntilIdle()

        val dismissedState = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(dismissedState)
        assertFalse(dismissedState.showBootloaderWarning, "Bootloader warning should be dismissed")
    }

    @Test
    fun `bootloader warning not shown for non-BLE connections`() = runTest {
        val hardware =
            DeviceHardware(
                hwModel = 1,
                architecture = "nrf52",
                platformioTarget = "tbeam",
                requiresBootloaderUpgradeForOta = true,
            )
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)

        // TCP prefix: isBle() returns false
        every { radioPrefs.devAddr } returns MutableStateFlow("t192.168.1.1")

        nodeRepository.setMyNodeInfo(
            TestDataFactory.createMyNodeInfo(myNodeNum = 123, firmwareVersion = "0.9.0", pioEnv = "tbeam"),
        )

        everySuspend { bootloaderWarningDataSource.isDismissed(any()) } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertFalse(state.showBootloaderWarning, "Bootloader warning should not show over TCP")
    }

    @Test
    fun `checkForUpdates sets error when address is null`() = runTest {
        every { radioPrefs.devAddr } returns MutableStateFlow(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertIs<FirmwareUpdateState.Error>(viewModel.state.value)
    }

    @Test
    fun `checkForUpdates sets error when myNodeInfo is null`() = runTest {
        nodeRepository.setMyNodeInfo(null)
        nodeRepository.setOurNode(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertIs<FirmwareUpdateState.Error>(viewModel.state.value)
    }

    @Test
    fun `checkForUpdates sets error when hardware lookup fails`() = runTest {
        advanceUntilIdle()
        assertIs<FirmwareUpdateState.Ready>(viewModel.state.value)
        assertEquals("1.0.0", viewModel.selectedRelease.value?.title)
        assertIs<DeviceHardware>(viewModel.deviceHardware.value)
        assertEquals("0.9.0", viewModel.currentFirmwareVersion.value)

        every { firmwareReleaseRepository.stableRelease } returns flow { error("cache read failed") }
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.failure(IllegalStateException("Unknown hardware"))

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedRelease.value)
        assertEquals(null, viewModel.deviceHardware.value)
        assertEquals(null, viewModel.currentFirmwareVersion.value)
        val errorState = assertIs<FirmwareUpdateState.Error>(viewModel.state.value)
        val error = assertIs<UiText.Resource>(errorState.error)
        assertEquals(Res.string.firmware_update_unknown_hardware, error.res)
    }

    @Test
    fun `update method is BLE for BLE-prefixed address`() = runTest {
        every { radioPrefs.devAddr } returns MutableStateFlow("x1234abcd")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertIs<FirmwareUpdateMethod.Ble>(state.updateMethod)
    }

    @Test
    fun `update method is Wifi for TCP-prefixed address`() = runTest {
        val hardware = DeviceHardware(hwModel = 1, architecture = "esp32", platformioTarget = "tbeam")
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)
        every { radioPrefs.devAddr } returns MutableStateFlow("t192.168.1.1")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertIs<FirmwareUpdateMethod.Wifi>(state.updateMethod)
    }

    @Test
    fun `update method is Usb for serial-prefixed nrf52 address`() = runTest {
        val hardware = DeviceHardware(hwModel = 1, architecture = "nrf52", platformioTarget = "tbeam")
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)
        every { radioPrefs.devAddr } returns MutableStateFlow("s/dev/ttyUSB0")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertIs<FirmwareUpdateMethod.Usb>(state.updateMethod)
    }

    @Test
    fun `update method is Unknown for serial ESP32`() = runTest {
        // ESP32 over serial is not supported — should yield Unknown
        val hardware = DeviceHardware(hwModel = 1, architecture = "esp32", platformioTarget = "tbeam")
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)
        every { radioPrefs.devAddr } returns MutableStateFlow("s/dev/ttyUSB0")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertIs<FirmwareUpdateMethod.Unknown>(state.updateMethod)
    }

    @Test
    fun `update method is Unknown for TCP nrf52`() = runTest {
        // WiFi OTA is ESP32-only — nRF52 over TCP has no update path, so the method must be Unknown (the screen then
        // shows an unsupported message instead of an Update button that would only throw on press).
        val hardware = DeviceHardware(hwModel = 1, architecture = "nrf52", platformioTarget = "tbeam")
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.success(hardware)
        every { radioPrefs.devAddr } returns MutableStateFlow("t192.168.1.1")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertIs<FirmwareUpdateMethod.Unknown>(state.updateMethod)
    }

    @Test
    fun `setReleaseType LOCAL produces null release in Ready`() = runTest {
        advanceUntilIdle()

        viewModel.setReleaseType(FirmwareReleaseType.LOCAL)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertEquals(null, state.release)
    }

    @Test
    fun `checkForUpdates enters recovery mode when disconnected with a saved record`() = runTest {
        // Disconnected (no live device) but a stranded-bootloader record exists → recovery Ready, not "no device".
        every { radioPrefs.devAddr } returns MutableStateFlow(null)
        every { firmwareRecoveryDataSource.pending } returns
            flowOf(
                PendingFirmwareRecovery(
                    fullAddress = "x1234abcd",
                    hwModel = 1,
                    pioEnv = "tbeam",
                    releaseType = "STABLE",
                    deviceName = "My Node",
                ),
            )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<FirmwareUpdateState.Ready>(state)
        assertTrue(state.isRecovery, "Expected recovery Ready but was $state")
        assertEquals("1234abcd", state.address) // fullAddress.drop(1)
        assertIs<FirmwareUpdateMethod.Ble>(state.updateMethod)
    }

    @Test
    fun `recovery hardware lookup failure clears stale device metadata`() = runTest {
        advanceUntilIdle()
        assertIs<FirmwareUpdateState.Ready>(viewModel.state.value)
        assertEquals("1.0.0", viewModel.selectedRelease.value?.title)
        assertIs<DeviceHardware>(viewModel.deviceHardware.value)
        assertEquals("0.9.0", viewModel.currentFirmwareVersion.value)

        every { radioPrefs.devAddr } returns MutableStateFlow(null)
        every { firmwareRecoveryDataSource.pending } returns
            flowOf(
                PendingFirmwareRecovery(
                    fullAddress = "x1234abcd",
                    hwModel = 999,
                    pioEnv = "unknown",
                    releaseType = "STABLE",
                    deviceName = "Stale Node",
                ),
            )
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any(), any()) } returns
            Result.failure(IllegalStateException("Unknown hardware"))

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedRelease.value)
        assertEquals(null, viewModel.deviceHardware.value)
        assertEquals(null, viewModel.currentFirmwareVersion.value)
        val errorState = assertIs<FirmwareUpdateState.Error>(viewModel.state.value)
        val error = assertIs<UiText.Resource>(errorState.error)
        assertEquals(Res.string.firmware_update_unknown_hardware, error.res)
    }

    @Test
    fun `BLE post-update reconnect requests GATT cache invalidation`() = runTest {
        advanceUntilIdle()

        everySuspend { firmwareUpdateManager.startUpdate(any(), any(), any(), any()) }
            .calls {
                @Suppress("UNCHECKED_CAST")
                val updateState = it.args[3] as (FirmwareUpdateState) -> Unit
                updateState(FirmwareUpdateState.Success())
                null
            }

        viewModel.startUpdate()
        advanceUntilIdle()

        assertTrue(
            radioController.gattCacheInvalidationRequested,
            "BLE post-update reconnect should request GATT cache invalidation",
        )
    }

    @Test
    fun `TCP post-update reconnect does not request GATT cache invalidation`() = runTest {
        advanceUntilIdle()

        every { radioPrefs.devAddr } returns MutableStateFlow("t192.168.1.100")

        everySuspend { firmwareUpdateManager.startUpdate(any(), any(), any(), any()) }
            .calls {
                @Suppress("UNCHECKED_CAST")
                val updateState = it.args[3] as (FirmwareUpdateState) -> Unit
                updateState(FirmwareUpdateState.Success())
                null
            }

        viewModel.startUpdate()
        advanceUntilIdle()

        assertFalse(
            radioController.gattCacheInvalidationRequested,
            "TCP post-update reconnect should not request GATT cache invalidation",
        )
    }

    @Test
    fun `BLE-prefixed post-update reconnect requests GATT cache invalidation`() = runTest {
        advanceUntilIdle()

        every { radioPrefs.devAddr } returns MutableStateFlow("xAA:BB:CC:DD:EE:FF")

        everySuspend { firmwareUpdateManager.startUpdate(any(), any(), any(), any()) }
            .calls {
                @Suppress("UNCHECKED_CAST")
                val updateState = it.args[3] as (FirmwareUpdateState) -> Unit
                updateState(FirmwareUpdateState.Success())
                null
            }

        viewModel.startUpdate()
        advanceUntilIdle()

        assertTrue(
            radioController.gattCacheInvalidationRequested,
            "BLE-prefixed (x) post-update reconnect should request GATT cache invalidation",
        )
    }
}
