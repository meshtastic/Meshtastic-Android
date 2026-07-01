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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.getString
import org.junit.runner.RunWith
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bonding_failed_retry
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.failBondAfterRecording
import org.meshtastic.core.testing.failBondWith
import org.meshtastic.core.testing.failBondWithSecurityException
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for [AndroidScannerViewModel.requestBonding]: only a successful bond arms the transport. Bond failures
 * surface a warning and wait for an explicit retry so the Android pairing flow does not immediately re-enter through
 * transport-side bonding.
 *
 * Robolectric is used only because the class under test lives in `androidMain`; the bonding outcomes themselves are
 * injected via [org.meshtastic.core.testing.FakeBluetoothRepository], keeping each assertion deterministic. The real
 * `AndroidBluetoothRepository.bond()` branches are covered separately by `AndroidBluetoothRepositoryBondTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidScannerViewModelBondingTest {

    private val mac = "AA:BB:CC:DD:EE:FF"
    private val expectedFullAddress = "x$mac"

    private lateinit var harness: ScannerViewModelHarness
    private lateinit var viewModel: AndroidScannerViewModel

    @BeforeTest
    fun setUp() {
        harness = ScannerViewModelHarness()
        Dispatchers.setMain(harness.testDispatcher)
        viewModel =
            AndroidScannerViewModel(
                serviceRepository = harness.serviceRepository,
                radioController = harness.radioController,
                radioInterfaceService = harness.radioInterfaceService,
                radioPrefs = harness.radioPrefs,
                recentAddressesDataSource = harness.recentAddressesDataSource,
                getDiscoveredDevicesUseCase = harness.getDiscoveredDevicesUseCase,
                networkRepository = harness.networkRepository,
                dispatchers = harness.dispatchers,
                bluetoothRepository = harness.bluetoothRepository,
                usbRepository = inertUsbRepository(),
                uiPrefs = harness.uiPrefs,
                firmwareRecoveryDataSource = harness.firmwareRecoveryDataSource,
                bleScanner = harness.bleScanner,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A real [UsbRepository] (it is `final` and cross-module, so Mokkery can't mock it) made inert for this test: a
     * DESTROYED [Lifecycle] means its `init` launch and eager `stateIn` never run, so its (cyclic) lazies are never
     * evaluated. The USB path is irrelevant to bonding — the ViewModel only stores this collaborator.
     */
    private fun inertUsbRepository(): UsbRepository = UsbRepository(
        application = RuntimeEnvironment.getApplication(),
        dispatchers = harness.dispatchers,
        processLifecycle = DestroyedLifecycle,
        usbBroadcastReceiverLazy = lazy { error("UsbBroadcastReceiver must not be used in this test") },
        usbManagerLazy = lazy { null },
        usbSerialProberLazy = lazy { error("UsbSerialProber must not be used in this test") },
    )

    private object DestroyedLifecycle : Lifecycle() {
        override val currentState: State = State.DESTROYED

        override fun addObserver(observer: LifecycleObserver) = Unit

        override fun removeObserver(observer: LifecycleObserver) = Unit
    }

    @Test
    fun `successful bond arms the transport`() = runTest(harness.testDispatcher) {
        viewModel.onSelected(ScannerViewModelHarness.unbondedBleEntry(mac))
        testScheduler.advanceUntilIdle()

        assertEquals(1, harness.bluetoothRepository.bondCalls.size)
        assertEquals(expectedFullAddress, harness.radioController.lastSetDeviceAddress)
        assertNull(harness.serviceRepository.errorMessage.value)
    }

    @Test
    fun `generic bond failure does not arm the transport and surfaces an error`() = runTest(harness.testDispatcher) {
        // A failed pairing attempt should wait for another user action instead of immediately arming the transport,
        // which would call transport-side bond() and risk a duplicate PIN dialog or Android createBond() throttle.
        harness.bluetoothRepository.failBondWith(Exception("Failed to initiate bonding"))

        viewModel.onSelected(ScannerViewModelHarness.unbondedBleEntry(mac))
        testScheduler.advanceUntilIdle()

        assertEquals(1, harness.bluetoothRepository.bondCalls.size)
        assertNull(harness.radioController.lastSetDeviceAddress)
        assertEquals(getString(Res.string.bonding_failed_retry), harness.serviceRepository.errorMessage.value)
    }

    @Test
    fun `bond failure after Android records bond still arms the transport`() = runTest(harness.testDispatcher) {
        harness.bluetoothRepository.failBondAfterRecording(Exception("Timed out waiting for bonding to complete"))

        viewModel.onSelected(ScannerViewModelHarness.unbondedBleEntry(mac))
        testScheduler.advanceUntilIdle()

        assertEquals(1, harness.bluetoothRepository.bondCalls.size)
        assertEquals(expectedFullAddress, harness.radioController.lastSetDeviceAddress)
        assertNull(harness.serviceRepository.errorMessage.value)
    }

    @Test
    fun `security exception does not arm the transport and surfaces an error`() = runTest(harness.testDispatcher) {
        // Missing BLUETOOTH_CONNECT: connecting would fail the same way, so surface the error and do NOT arm.
        harness.bluetoothRepository.failBondWithSecurityException()

        viewModel.onSelected(ScannerViewModelHarness.unbondedBleEntry(mac))
        testScheduler.advanceUntilIdle()

        assertEquals(1, harness.bluetoothRepository.bondCalls.size)
        assertNull(harness.radioController.lastSetDeviceAddress)
        assertNotNull(harness.serviceRepository.errorMessage.value)
    }

    @Test
    fun `CancellationException from bond propagates without arming transport`() = runTest(harness.testDispatcher) {
        // CE must propagate — not be swallowed into an error message or transport arming.
        // Discriminator: if the CE catch were removed, CE would fall through to catch(Exception),
        // set an error message, and return false — errorMessage would be non-null, failing this test.
        harness.bluetoothRepository.failBondWith(CancellationException("cancelled"))

        viewModel.onSelected(ScannerViewModelHarness.unbondedBleEntry(mac))
        testScheduler.advanceUntilIdle()

        assertNull(harness.radioController.lastSetDeviceAddress, "transport must not be armed")
        assertNull(
            harness.serviceRepository.errorMessage.value,
            "CE must propagate, not be caught as generic failure",
        )
    }

    @Test
    fun `already bonded entry arms the transport without bonding`() = runTest(harness.testDispatcher) {
        // R6: selecting an already-bonded device connects directly without invoking createBond().
        val bonded = DeviceListEntry.Ble(device = FakeBleDevice(address = mac, name = "Node"), bonded = true)

        val initiatedImmediately = viewModel.onSelected(bonded)
        testScheduler.advanceUntilIdle()

        assertTrue(initiatedImmediately)
        assertTrue(harness.bluetoothRepository.bondCalls.isEmpty())
        assertEquals(expectedFullAddress, harness.radioController.lastSetDeviceAddress)
    }
}
