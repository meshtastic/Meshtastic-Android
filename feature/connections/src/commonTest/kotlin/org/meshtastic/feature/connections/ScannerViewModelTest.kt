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

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private lateinit var viewModel: ScannerViewModel
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val radioController = FakeRadioController()
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val recentAddressesDataSource: RecentAddressesDataSource = mock(MockMode.autofill)
    private val getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase = mock(MockMode.autofill)
    private val bleScanner: org.meshtastic.core.ble.BleScanner = mock(MockMode.autofill)

    private val connectionProgressFlow = MutableStateFlow<String?>(null)
    private val discoveredDevicesFlow = MutableStateFlow(DiscoveredDevices())

    @BeforeTest
    fun setUp() {
        every { radioInterfaceService.isMockInterface() } returns false
        every { radioInterfaceService.currentDeviceAddressFlow } returns MutableStateFlow(null)
        every { radioInterfaceService.supportedDeviceTypes } returns emptyList()

        every { serviceRepository.connectionProgress } returns connectionProgressFlow
        every { getDiscoveredDevicesUseCase.invoke(any()) } returns discoveredDevicesFlow
        every { recentAddressesDataSource.recentAddresses } returns MutableStateFlow(emptyList())

        connectionProgressFlow.value = null
        discoveredDevicesFlow.value = DiscoveredDevices()

        viewModel =
            ScannerViewModel(
                serviceRepository = serviceRepository,
                radioController = radioController,
                radioInterfaceService = radioInterfaceService,
                recentAddressesDataSource = recentAddressesDataSource,
                getDiscoveredDevicesUseCase = getDiscoveredDevicesUseCase,
                dispatchers = org.meshtastic.core.di.CoroutineDispatchers(
                    io = UnconfinedTestDispatcher(),
                    main = UnconfinedTestDispatcher(),
                    default = UnconfinedTestDispatcher(),
                ),
                bleScanner = bleScanner,
            )
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `errorText reflects connectionProgress`() = runTest {
        viewModel.errorText.test {
            assertEquals(null, awaitItem())
            connectionProgressFlow.value = "Connecting..."
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
    fun `changeDeviceAddress calls radioController`() {
        viewModel.changeDeviceAddress("test_address")

        assertEquals("test_address", radioController.lastSetDeviceAddress)
    }

    @Test
    fun `usbDevicesForUi emits updates`() = runTest {
        viewModel.usbDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            val device =
                org.meshtastic.feature.connections.model.DeviceListEntry.Usb(
                    usbData = object : org.meshtastic.feature.connections.model.UsbDeviceData {},
                    name = "USB Device",
                    fullAddress = "usb_address",
                    bonded = true,
                )
            discoveredDevicesFlow.value = DiscoveredDevices(usbDevices = listOf(device))

            assertEquals(listOf(device), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
