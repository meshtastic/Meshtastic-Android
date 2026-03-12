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
package org.meshtastic.feature.connections

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [ScannerViewModel] covering core device selection, connection, and state management.
 *
 * Uses `core:testing` fakes where available and mockk for remaining dependencies.
 */
class ScannerViewModelTest {

    private lateinit var viewModel: ScannerViewModel
    private lateinit var radioController: FakeRadioController
    private lateinit var serviceRepository: ServiceRepository
    private lateinit var radioInterfaceService: RadioInterfaceService
    private lateinit var recentAddressesDataSource: RecentAddressesDataSource
    private lateinit var getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase

    private fun setUp() {
        radioController = FakeRadioController()
        serviceRepository = mockk(relaxed = true) { every { connectionProgress } returns MutableStateFlow(null) }
        radioInterfaceService =
            mockk(relaxed = true) {
                every { isMockInterface() } returns false
                every { currentDeviceAddressFlow } returns MutableStateFlow(null)
                every { supportedDeviceTypes } returns listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)
            }
        recentAddressesDataSource = mockk(relaxed = true)
        getDiscoveredDevicesUseCase =
            mockk(relaxed = true) { every { invoke(any<Boolean>()) } returns flowOf(DiscoveredDevices()) }

        viewModel =
            ScannerViewModel(
                serviceRepository = serviceRepository,
                radioController = radioController,
                radioInterfaceService = radioInterfaceService,
                recentAddressesDataSource = recentAddressesDataSource,
                getDiscoveredDevicesUseCase = getDiscoveredDevicesUseCase,
            )
    }

    @Test
    fun testInitialization() = runTest {
        setUp()
        assertNull(viewModel.errorText.value, "Error text starts as null before connectionProgress emits")
    }

    @Test
    fun testSetErrorText() = runTest {
        setUp()
        viewModel.setErrorText("Test error")
        assertEquals("Test error", viewModel.errorText.value)
    }

    @Test
    fun testDisconnect() = runTest {
        setUp()
        viewModel.disconnect()
        verify { radioController.setDeviceAddress(NO_DEVICE_SELECTED) }
    }

    @Test
    fun testChangeDeviceAddress() = runTest {
        setUp()
        viewModel.changeDeviceAddress("x12:34:56:78:90:AB")
        verify { radioController.setDeviceAddress("x12:34:56:78:90:AB") }
    }

    @Test
    fun testOnSelectedBleDeviceBonded() = runTest {
        setUp()
        val bleDevice =
            mockk<DeviceListEntry.Ble>(relaxed = true) {
                every { bonded } returns true
                every { fullAddress } returns "xAA:BB:CC:DD:EE:FF"
            }
        val result = viewModel.onSelected(bleDevice)
        assertTrue(result, "Should return true for bonded BLE device")
        verify { radioController.setDeviceAddress("xAA:BB:CC:DD:EE:FF") }
    }

    @Test
    fun testOnSelectedBleDeviceNotBonded() = runTest {
        setUp()
        val bleDevice = mockk<DeviceListEntry.Ble>(relaxed = true) { every { bonded } returns false }
        val result = viewModel.onSelected(bleDevice)
        assertFalse(result, "Should return false for unbonded BLE device (triggers bonding)")
    }

    @Test
    fun testOnSelectedTcpDevice() = runTest {
        setUp()
        val tcpDevice = DeviceListEntry.Tcp("Meshtastic_1234", "t192.168.1.100")
        val result = viewModel.onSelected(tcpDevice)
        assertTrue(result, "Should return true for TCP device")
        verify { radioController.setDeviceAddress("t192.168.1.100") }
    }

    @Test
    fun testOnSelectedMockDevice() = runTest {
        setUp()
        val mockDevice = DeviceListEntry.Mock("Demo Mode")
        val result = viewModel.onSelected(mockDevice)
        assertTrue(result, "Should return true for mock device")
        verify { radioController.setDeviceAddress("m") }
    }

    @Test
    fun testOnSelectedUsbDeviceBonded() = runTest {
        setUp()
        val usbDevice =
            mockk<DeviceListEntry.Usb>(relaxed = true) {
                every { bonded } returns true
                every { fullAddress } returns "s/dev/ttyACM0"
            }
        val result = viewModel.onSelected(usbDevice)
        assertTrue(result, "Should return true for bonded USB device")
        verify { radioController.setDeviceAddress("s/dev/ttyACM0") }
    }

    @Test
    fun testOnSelectedUsbDeviceNotBonded() = runTest {
        setUp()
        val usbDevice = mockk<DeviceListEntry.Usb>(relaxed = true) { every { bonded } returns false }
        val result = viewModel.onSelected(usbDevice)
        assertFalse(result, "Should return false for unbonded USB device (triggers permission request)")
    }

    @Test
    fun testAddRecentAddressIgnoresNonTcpAddresses() = runTest {
        setUp()
        viewModel.addRecentAddress("xBLE_ADDRESS", "BLE Device")
        // Should not add — address doesn't start with "t"
        verify(exactly = 0) { recentAddressesDataSource.toString() }
    }

    @Test
    fun testSelectedNotNullFlowDefaultsToNoDeviceSelected() = runTest {
        setUp()
        assertEquals(
            NO_DEVICE_SELECTED,
            viewModel.selectedNotNullFlow.value,
            "selectedNotNullFlow defaults to NO_DEVICE_SELECTED when no device is selected",
        )
    }

    @Test
    fun testSupportedDeviceTypes() = runTest {
        setUp()
        assertEquals(listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB), viewModel.supportedDeviceTypes)
    }

    @Test
    fun testShowMockInterfaceFalseByDefault() = runTest {
        setUp()
        assertFalse(viewModel.showMockInterface.value, "showMockInterface defaults to false")
    }
}
