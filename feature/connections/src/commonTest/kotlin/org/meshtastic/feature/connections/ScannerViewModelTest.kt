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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private lateinit var viewModel: ScannerViewModel
    private val serviceRepository = FakeServiceRepository()
    private val radioController = FakeRadioController()
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val radioPrefs: RadioPrefs = mock(MockMode.autofill)
    private val recentAddressesDataSource: RecentAddressesDataSource = mock(MockMode.autofill)
    private val networkRepository: NetworkRepository = mock(MockMode.autofill)
    private val bleScanner: org.meshtastic.core.ble.BleScanner = mock(MockMode.autofill)
    private val uiPrefs = org.meshtastic.core.testing.FakeUiPrefs()

    private val resolvedServicesFlow = MutableStateFlow<List<DiscoveredService>>(emptyList())
    private val baseDevicesFlow = MutableStateFlow(DiscoveredDevices())

    /**
     * A fake [GetDiscoveredDevicesUseCase] that mirrors the real behavior: it combines the provided [resolvedList] with
     * base device data so tests can verify NSD gating.
     */
    private val getDiscoveredDevicesUseCase =
        object : GetDiscoveredDevicesUseCase {
            override fun invoke(
                showMock: Boolean,
                resolvedList: Flow<List<DiscoveredService>>,
            ): Flow<DiscoveredDevices> = combine(baseDevicesFlow, resolvedList) { base, resolved ->
                val tcpDevices =
                    resolved.map { DeviceListEntry.Tcp(name = it.name, fullAddress = "t${it.hostAddress}") }
                base.copy(discoveredTcpDevices = tcpDevices)
            }
        }

    @BeforeTest
    fun setUp() {
        every { radioInterfaceService.isMockTransport() } returns false
        every { radioInterfaceService.currentDeviceAddressFlow } returns MutableStateFlow(null)

        every { recentAddressesDataSource.recentAddresses } returns MutableStateFlow(emptyList())
        every { networkRepository.resolvedList } returns resolvedServicesFlow
        every { networkRepository.networkAvailable } returns flowOf(true)

        serviceRepository.setConnectionProgress("")
        baseDevicesFlow.value = DiscoveredDevices()
        resolvedServicesFlow.value = emptyList()

        viewModel =
            ScannerViewModel(
                serviceRepository = serviceRepository,
                radioController = radioController,
                radioInterfaceService = radioInterfaceService,
                radioPrefs = radioPrefs,
                recentAddressesDataSource = recentAddressesDataSource,
                getDiscoveredDevicesUseCase = getDiscoveredDevicesUseCase,
                networkRepository = networkRepository,
                dispatchers =
                org.meshtastic.core.di.CoroutineDispatchers(
                    io = UnconfinedTestDispatcher(),
                    main = UnconfinedTestDispatcher(),
                    default = UnconfinedTestDispatcher(),
                ),
                uiPrefs = uiPrefs,
                bleScanner = bleScanner,
            )
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
    fun `changeDeviceAddress calls radioController`() {
        viewModel.changeDeviceAddress("test_address")

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
}
