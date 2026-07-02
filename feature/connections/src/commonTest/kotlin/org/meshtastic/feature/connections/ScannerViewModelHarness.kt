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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

/**
 * Reusable construction harness for [ScannerViewModel] (and its platform subclasses).
 *
 * Bundles the ~10 collaborators the ViewModel needs — hand-written fakes where assertions are made ([radioController],
 * [serviceRepository], [bluetoothRepository]) and Mokkery autofill mocks for the peripheral ones — plus the common
 * init-time stubbing, so a test only writes the bits it actually asserts on. Lives in `commonTest` so both the
 * platform-neutral test and the `androidHostTest` subclass tests can reuse it (the KMP default hierarchy makes
 * `androidHostTest` depend on `commonTest`).
 *
 * Usage: construct the harness, `Dispatchers.setMain(harness.testDispatcher)`, then [buildBase] (platform-neutral) or
 * build a platform subclass from the exposed collaborators (see `AndroidScannerViewModelBondingTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelHarness(val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()) {

    val serviceRepository = FakeServiceRepository()
    val radioController = FakeRadioController()
    val bluetoothRepository = FakeBluetoothRepository()
    val uiPrefs = FakeUiPrefs()

    val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    val radioPrefs: RadioPrefs = mock(MockMode.autofill)
    val recentAddressesDataSource: RecentAddressesDataSource = mock(MockMode.autofill)
    val firmwareRecoveryDataSource: FirmwareRecoveryDataSource = mock(MockMode.autofill)
    val networkRepository: NetworkRepository = mock(MockMode.autofill)
    val bleScanner: BleScanner = mock(MockMode.autofill)

    /** Drives the base device list (bonded BLE / USB / TCP) for tests that exercise the discovery flows. */
    val baseDevicesFlow = MutableStateFlow(DiscoveredDevices())

    /** NSD-resolved services, gated by the network-scan flag in the ViewModel. */
    val resolvedServicesFlow = MutableStateFlow<List<DiscoveredService>>(emptyList())

    /**
     * Currently-selected device address (the `fullAddress`), backing `radioInterfaceService.currentDeviceAddressFlow`.
     */
    val currentDeviceAddressFlow = MutableStateFlow<String?>(null)

    val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    /**
     * A fake [GetDiscoveredDevicesUseCase] that mirrors the real behavior: it combines [baseDevicesFlow] with the
     * provided resolved list so tests can verify NSD gating.
     */
    val getDiscoveredDevicesUseCase =
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

    init {
        every { radioInterfaceService.isMockTransport() } returns false
        every { radioInterfaceService.currentDeviceAddressFlow } returns currentDeviceAddressFlow
        every { recentAddressesDataSource.recentAddresses } returns MutableStateFlow(emptyList())
        every { firmwareRecoveryDataSource.pending } returns flowOf(null)
        every { networkRepository.resolvedList } returns resolvedServicesFlow
        every { networkRepository.networkAvailable } returns flowOf(true)
        // Default: a non-completing scan flow so the BLE scan stays "active" until explicitly cancelled.
        // Under UnconfinedTestDispatcher an emptyFlow().collect{} returns immediately and would flip
        // _isBleScanning back to false before startBleScan() returns. Tests that need specific emissions
        // (e.g. device-list updates) override this stub.
        every { bleScanner.scan(any(), any()) } returns flow<BleDevice> { awaitCancellation() }
    }

    /**
     * Build the platform-neutral [ScannerViewModel]. Call only after `Dispatchers.setMain(testDispatcher)` because the
     * ViewModel's `init` launches work on `viewModelScope` (Main).
     */
    fun buildBase(): ScannerViewModel = ScannerViewModel(
        serviceRepository = serviceRepository,
        radioController = radioController,
        radioInterfaceService = radioInterfaceService,
        radioPrefs = radioPrefs,
        recentAddressesDataSource = recentAddressesDataSource,
        getDiscoveredDevicesUseCase = getDiscoveredDevicesUseCase,
        networkRepository = networkRepository,
        dispatchers = dispatchers,
        uiPrefs = uiPrefs,
        firmwareRecoveryDataSource = firmwareRecoveryDataSource,
        bleScanner = bleScanner,
    )

    companion object {
        /** A scanned-but-unbonded BLE entry — the input that routes through `requestBonding`. */
        fun unbondedBleEntry(address: String, name: String = "Node"): DeviceListEntry.Ble =
            DeviceListEntry.Ble(device = FakeBleDevice(address = address, name = name), bonded = false)
    }
}
