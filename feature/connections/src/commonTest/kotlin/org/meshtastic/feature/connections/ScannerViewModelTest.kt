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
package org.meshtastic.feature.connections

import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val harness = ScannerViewModelHarness()
    private lateinit var viewModel: ScannerViewModel

    // Convenience aliases so the existing test bodies read unchanged.
    private val serviceRepository
        get() = harness.serviceRepository

    private val radioController
        get() = harness.radioController

    private val bleScanner
        get() = harness.bleScanner

    private val baseDevicesFlow
        get() = harness.baseDevicesFlow

    private val resolvedServicesFlow
        get() = harness.resolvedServicesFlow

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(harness.testDispatcher)

        serviceRepository.setConnectionProgress("")
        baseDevicesFlow.value = DiscoveredDevices()
        resolvedServicesFlow.value = emptyList()

        viewModel = harness.buildBase()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `connectionProgressText reflects connectionProgress`() = runTest {
        viewModel.connectionProgressText.test {
            assertEquals("", awaitItem())
            serviceRepository.setConnectionProgress("Connecting...")
            assertEquals("Connecting...", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startBleScan updates isBleScanning`() = runTest {
        every { bleScanner.scan(any(), any()) } returns kotlinx.coroutines.flow.emptyFlow()

        viewModel.isBleScanning.test {
            assertEquals(false, awaitItem())
            viewModel.startBleScan()
            assertEquals(true, awaitItem())

            viewModel.stopBleScan()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changeDeviceAddress calls radioController`() = runTest {
        viewModel.changeDeviceAddress("test_address")
        testScheduler.advanceUntilIdle()

        assertEquals("test_address", radioController.lastSetDeviceAddress)
    }

    @Test
    fun `usbDevicesForUi emits updates`() = runTest {
        viewModel.usbDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            val device =
                DeviceListEntry.Usb(
                    usbData = object : org.meshtastic.feature.connections.model.UsbDeviceData {},
                    name = "USB Device",
                    fullAddress = "usb_address",
                    bonded = true,
                )
            baseDevicesFlow.value = DiscoveredDevices(usbDevices = listOf(device))

            assertEquals(listOf(device), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isNetworkScanning defaults to false`() {
        assertEquals(false, viewModel.isNetworkScanning.value)
    }

    @Test
    fun `startNetworkScan updates isNetworkScanning`() = runTest {
        viewModel.isNetworkScanning.test {
            assertEquals(false, awaitItem())
            viewModel.startNetworkScan()
            assertEquals(true, awaitItem())
            viewModel.stopNetworkScan()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discoveredTcpDevicesForUi is empty when not scanning`() = runTest {
        resolvedServicesFlow.value =
            listOf(DiscoveredService(name = "NSD Device", hostAddress = "192.168.1.50", port = 4403))

        viewModel.discoveredTcpDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discoveredTcpDevicesForUi populates when scanning is active`() = runTest {
        resolvedServicesFlow.value =
            listOf(DiscoveredService(name = "NSD Device", hostAddress = "192.168.1.50", port = 4403))

        viewModel.discoveredTcpDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())
            viewModel.startNetworkScan()
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("t192.168.1.50", result[0].fullAddress)
            viewModel.stopNetworkScan()
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bleDevicesForUi shows bonded devices only once they are visible via scan`() = runTest {
        val device1 = FakeBleDevice(address = "01:02:03:04:05:06", name = "Node B", rssi = -50)
        val device2 = FakeBleDevice(address = "07:08:09:0A:0B:0C", name = "Node A", rssi = -30)
        val bondedBle = FakeBleDevice(address = "0D:0E:0F:10:11:12", name = "Bonded C", rssi = null)
        val bondedDevice = DeviceListEntry.Ble(device = bondedBle, bonded = true)

        val scanFlow = MutableStateFlow<org.meshtastic.core.ble.BleDevice?>(null)
        every { bleScanner.scan(any(), any()) } returns scanFlow.filterNotNull()

        viewModel.bleDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            // A system-bonded device that isn't advertising stays hidden — the list only shows what's nearby.
            baseDevicesFlow.value = DiscoveredDevices(bleDevices = listOf(bondedDevice))
            expectNoEvents()

            // 1. Scan finds Device 1 (Node B) — unbonded, appears and routes through bonding when tapped.
            viewModel.startBleScan()
            scanFlow.value = device1
            val afterDevice1 = awaitItem()
            assertEquals(1, afterDevice1.size)
            assertEquals(device1.address, (afterDevice1[0] as DeviceListEntry.Ble).address)
            assertEquals(false, afterDevice1[0].bonded)

            // 2. Scan finds Device 2 (Node A, -30dBm) - stronger signal but kept AFTER Device 1 per discovery order.
            scanFlow.value = device2
            val afterDevice2 = awaitItem()
            assertEquals(2, afterDevice2.size)
            assertEquals(device1.address, (afterDevice2[0] as DeviceListEntry.Ble).address)
            assertEquals(device2.address, (afterDevice2[1] as DeviceListEntry.Ble).address)

            // 3. The bonded device starts advertising — now it appears, flagged bonded and sorted first by name.
            scanFlow.value = bondedBle
            val afterBonded = awaitItem()
            assertEquals(3, afterBonded.size)
            assertEquals(bondedDevice.address, (afterBonded[0] as DeviceListEntry.Ble).address)
            assertEquals(true, afterBonded[0].bonded)
            assertEquals(device1.address, (afterBonded[1] as DeviceListEntry.Ble).address)
            assertEquals(device2.address, (afterBonded[2] as DeviceListEntry.Ble).address)

            // 4. Device 1 RSSI updates to -20dBm (strongest) - should NOT re-sort.
            scanFlow.value = FakeBleDevice(address = device1.address, name = device1.name, rssi = -20)
            val afterRssiUpdate = awaitItem()
            assertEquals(3, afterRssiUpdate.size)
            assertEquals(device1.address, (afterRssiUpdate[1] as DeviceListEntry.Ble).address)
            assertEquals(-20, (afterRssiUpdate[1] as DeviceListEntry.Ble).device.rssi)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bleDevicesForUi keeps the selected device visible even when not seen via scan`() = runTest {
        val bondedBle = FakeBleDevice(address = "0D:0E:0F:10:11:12", name = "Bonded C", rssi = null)
        val bondedDevice = DeviceListEntry.Ble(device = bondedBle, bonded = true)

        viewModel.bleDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            // The device is bonded and selected (e.g. auto-reconnect on launch); while connected it stops
            // advertising, so a scan never sees it — but it must stay visible so the user can disconnect.
            harness.currentDeviceAddressFlow.value = bondedDevice.fullAddress
            baseDevicesFlow.value = DiscoveredDevices(bleDevices = listOf(bondedDevice))

            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(bondedDevice.fullAddress, items[0].fullAddress)
            assertEquals(true, items[0].bonded)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stopBleScan does not clear scanned devices`() = runTest {
        val device = FakeBleDevice(address = "01:02:03:04:05:06", name = "Node", rssi = -50)
        val scanFlow = MutableStateFlow<org.meshtastic.core.ble.BleDevice?>(null)
        every { bleScanner.scan(any(), any()) } returns scanFlow.filterNotNull()

        viewModel.bleDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            viewModel.startBleScan()
            scanFlow.value = device
            assertEquals(1, awaitItem().size)

            viewModel.stopBleScan()
            // Should not emit a new empty list
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
