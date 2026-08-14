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
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanStartException
import org.meshtastic.core.ble.BleScanStartFailureReason
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_disabled
import org.meshtastic.core.resources.bluetooth_scan_location_services_disabled
import org.meshtastic.core.resources.bluetooth_scan_missing_permission
import org.meshtastic.core.resources.bluetooth_scan_start_failed
import org.meshtastic.core.resources.bluetooth_scan_too_frequent
import org.meshtastic.core.resources.getPluralStringSuspend
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private lateinit var harness: ScannerViewModelHarness
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
        harness = ScannerViewModelHarness()
        Dispatchers.setMain(harness.testDispatcher)

        serviceRepository.setConnectionProgress("")
        baseDevicesFlow.value = DiscoveredDevices()
        resolvedServicesFlow.value = emptyList()

        viewModel = harness.buildBase()
    }

    @AfterTest
    fun tearDown() {
        // Order matters: the ViewModel's coroutines must be gone before Main is unset.
        harness.clearViewModel(viewModel)
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    /**
     * Demo Mode's gate opens mid-session in a release build, when the user performs the hidden-features gesture in
     * Settings. Sampling it once in `init` (as this used to) meant the Connections list never noticed.
     */
    @Test
    fun `showMockTransport follows the transport gate after construction`() = runTest {
        viewModel.showMockTransport.test {
            assertEquals(false, awaitItem())

            harness.mockTransportEnabled.value = true
            assertEquals(true, awaitItem())

            harness.mockTransportEnabled.value = false
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The gate has to reach the device list, not merely be observable on the ViewModel.
     *
     * Asserting on `showMockTransport` alone would pass even if the ViewModel stopped feeding the gate into the
     * device-list query, so this asserts on the requests the use case actually received: one per gate value, in order.
     * A gate sampled once at construction — which is what this branch fixes — records only its initial value here.
     */
    @Test
    fun `a mid-session unlock re-queries the device list`() = runTest {
        viewModel.usbDevicesForUi.test {
            awaitItem()
            testScheduler.runCurrent()
            assertEquals(
                listOf(false to false),
                harness.discoveryRequests,
                "a locked gate must still have queried the device list once",
            )

            // Each transition is checkpointed before the next one is provoked. Writing `true` and `false` back to
            // back would let the StateFlow conflate them, and the `true` request — the one this whole feature exists
            // to produce — could then never be observed, leaving the test green but vacuous. Waiting here fixes the
            // ordering through the test's own control flow rather than through dispatcher timing, which is not a
            // contract worth asserting on.
            harness.mockTransportEnabled.value = true
            testScheduler.runCurrent()
            assertEquals(
                listOf(false to false, true to false),
                harness.discoveryRequests,
                "unlocking Demo Mode mid-session must re-query the device list",
            )

            harness.mockTransportEnabled.value = false
            testScheduler.runCurrent()
            assertEquals(
                listOf(false to false, true to false, false to false),
                harness.discoveryRequests,
                "re-locking must re-query it again",
            )
            cancelAndIgnoreRemainingEvents()
        }
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
    fun `scan startup failure clears scanning state disables auto-scan and surfaces error`() = runTest {
        warmScanFailureStrings()
        harness.uiPrefs.setBleAutoScan(true)
        every { bleScanner.scan(any(), any()) } returns failingScanFlow()

        viewModel.startBleScan()

        assertEquals(false, viewModel.isBleScanning.value)
        assertEquals(false, viewModel.bleAutoScan.value)
        assertEquals(
            "Bluetooth scan couldn't start. Try again, or toggle Bluetooth if the problem continues.",
            serviceRepository.errorMessage.value,
        )
    }

    @Test
    fun `scan startup failure cooldown prevents immediate retry and allows later manual retry`() = runTest {
        var scanAttempts = 0
        every { bleScanner.scan(any(), any()) } returns
            flow {
                scanAttempts += 1
                throw BleScanStartException(
                    reason = BleScanStartFailureReason.ApplicationRegistrationFailed,
                    cause = IllegalStateException("Failed to start scan as app cannot be registered"),
                )
            }

        viewModel.startBleScan()
        assertEquals(1, scanAttempts)
        assertEquals(false, viewModel.isBleScanning.value)

        viewModel.startBleScan()
        assertEquals(1, scanAttempts)

        harness.testDispatcher.scheduler.advanceTimeBy(BLE_SCAN_START_FAILURE_RETRY_COOLDOWN.inWholeMilliseconds + 1)
        viewModel.startBleScan()

        assertEquals(2, scanAttempts)
    }

    @Test
    fun `bluetooth-disabled failure allows an immediate retry once the user re-enables it`() = runTest {
        warmScanFailureStrings()
        // No cooldown for preconditions the user clears with a system toggle — a dead scan button right after they
        // switched Bluetooth back on reads as a broken app.
        var scanAttempts = 0
        every { bleScanner.scan(any(), any()) } returns
            flow {
                scanAttempts += 1
                throw BleScanStartException(
                    reason = BleScanStartFailureReason.BluetoothDisabled,
                    cause = IllegalStateException("Bluetooth disabled"),
                )
            }

        viewModel.startBleScan()
        assertEquals(1, scanAttempts)
        assertEquals(false, viewModel.isBleScanning.value)
        assertEquals("Bluetooth is off. Turn it on to scan for nearby devices.", serviceRepository.errorMessage.value)

        // Immediately retryable — no waiting on the scheduler.
        viewModel.startBleScan()
        assertEquals(2, scanAttempts)
    }

    @Test
    fun `location-services-disabled failure allows an immediate retry`() = runTest {
        warmScanFailureStrings()
        var scanAttempts = 0
        every { bleScanner.scan(any(), any()) } returns
            flow {
                scanAttempts += 1
                throw BleScanStartException(
                    reason = BleScanStartFailureReason.LocationServicesDisabled,
                    cause = IllegalStateException("Location services are required for scanning but are disabled"),
                )
            }

        viewModel.startBleScan()
        assertEquals(1, scanAttempts)
        assertEquals(
            "Location services are off. Turn them on to scan for nearby devices.",
            serviceRepository.errorMessage.value,
        )

        viewModel.startBleScan()
        assertEquals(2, scanAttempts)
    }

    @Test
    fun `scan quota failure honors retry-after cooldown`() = runTest {
        warmScanFailureStrings()
        var scanAttempts = 0
        every { bleScanner.scan(any(), any()) } returns
            flow {
                scanAttempts += 1
                throw BleScanStartException(
                    reason = BleScanStartFailureReason.ScanningTooFrequently,
                    cause = IllegalStateException("Android BLE scan-start quota exhausted"),
                    retryAfter = 30.seconds + 1.milliseconds,
                )
            }

        viewModel.startBleScan()
        assertEquals(1, scanAttempts)
        assertEquals("Bluetooth scan limit reached. Try again in 31 seconds.", serviceRepository.errorMessage.value)

        harness.testDispatcher.scheduler.advanceTimeBy(BLE_SCAN_START_FAILURE_RETRY_COOLDOWN.inWholeMilliseconds + 1)
        viewModel.startBleScan()
        assertEquals(1, scanAttempts)

        harness.testDispatcher.scheduler.advanceTimeBy(15.seconds.inWholeMilliseconds + 1)
        viewModel.startBleScan()
        assertEquals(2, scanAttempts)
    }

    @Test
    fun `stopBleScan is idempotent after failed startup`() = runTest {
        every { bleScanner.scan(any(), any()) } returns failingScanFlow()

        viewModel.startBleScan()
        viewModel.stopBleScan()
        viewModel.stopBleScan()

        assertEquals(false, viewModel.isBleScanning.value)
    }

    @Test
    fun `changeDeviceAddress calls radioController`() = runTest {
        viewModel.changeDeviceAddress("test_address")
        testScheduler.advanceUntilIdle()

        assertEquals("test_address", radioController.lastSetDeviceAddress)
    }

    @Test
    fun `dismissRecovery clears the pending recovery record`() = runTest {
        // User dismisses a recovery that can't succeed (e.g. an unflashable bootloader) — the record
        // must be cleared so the banner stops nagging.
        viewModel.dismissRecovery()
        testScheduler.advanceUntilIdle()

        verifySuspend { harness.firmwareRecoveryDataSource.clear() }
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
    fun `active transport defaults to BLE when no selected device or preference exists`() {
        assertEquals(DeviceType.BLE, viewModel.activeTransport.value)
    }

    @Test
    fun `active transport defaults to selected device type when no explicit preference exists`() {
        harness.currentDeviceAddressFlow.value = "t192.168.1.50"

        assertEquals(DeviceType.TCP, viewModel.activeTransport.value)
    }

    @Test
    fun `explicit active transport preference wins over selected device type`() {
        harness.currentDeviceAddressFlow.value = "s/dev/bus/usb/001/002"

        viewModel.selectTransport(DeviceType.BLE)

        assertEquals(DeviceType.BLE, viewModel.activeTransport.value)
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

    /**
     * The replay demo entry only makes sense in a build carrying the capture asset; without it the replay transport
     * degrades to the plain mock. These two pin the ViewModel as the thing that forwards that capability, so the gate
     * cannot regress into "always offer replay whenever Demo Mode is on".
     */
    @Test
    fun `replay is requested only when the transport reports the capture asset`() = runTest {
        assertEquals(listOf(true to true), requestedVisibility(mockTransport = true, replayAvailable = true))
    }

    @Test
    fun `replay is not requested when the capture asset is absent`() = runTest {
        assertEquals(listOf(true to false), requestedVisibility(mockTransport = true, replayAvailable = false))
    }

    /**
     * Builds a ViewModel against the given transport capabilities and returns the distinct `(showMock, showReplay)`
     * pairs it asked the use case for. A fresh ViewModel is required because the replay flag is latched in `init` — the
     * Demo Mode gate itself is observed, so it is set on the backing flow rather than stubbed.
     */
    private suspend fun requestedVisibility(
        mockTransport: Boolean,
        replayAvailable: Boolean,
    ): List<Pair<Boolean, Boolean>> {
        harness.mockTransportEnabled.value = mockTransport
        every { harness.radioInterfaceService.isReplayTransportAvailable } returns replayAvailable
        harness.discoveryRequests.clear()
        val subject = harness.buildBase()
        try {
            subject.usbDevicesForUi.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            return harness.discoveryRequests.distinct()
        } finally {
            harness.clearViewModel(subject)
        }
    }

    @Test
    fun `bleDevicesForUi shows bonded devices only once they are visible via scan`() = runTest {
        val device1 = FakeBleDevice(address = "01:02:03:04:05:06", name = "Node B", rssi = -50)
        val device2 = FakeBleDevice(address = "07:08:09:0A:0B:0C", name = "Node A", rssi = -30)
        val bondedBle = FakeBleDevice(address = "0D:0E:0F:10:11:12", name = "Bonded C", rssi = null)
        val bondedDevice = DeviceListEntry.Ble(device = bondedBle, bonded = true)

        val scanFlow = MutableStateFlow<BleDevice?>(null)
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
        val scanFlow = MutableStateFlow<BleDevice?>(null)
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

    // ── Mutual exclusion: only one of BLE / Network scanning may be active at a time ──────────

    @Test
    fun `startBleScan cancels active network scan`() = runTest {
        viewModel.startNetworkScan()
        assertEquals(true, viewModel.isNetworkScanning.value)

        viewModel.startBleScan()

        assertEquals(false, viewModel.isNetworkScanning.value)
        assertEquals(true, viewModel.isBleScanning.value)
    }

    @Test
    fun `startNetworkScan cancels active ble scan`() = runTest {
        viewModel.startBleScan()
        assertEquals(true, viewModel.isBleScanning.value)

        viewModel.startNetworkScan()

        assertEquals(false, viewModel.isBleScanning.value)
        assertEquals(true, viewModel.isNetworkScanning.value)
    }

    @Test
    fun `selecting BLE stops active network scan`() = runTest {
        viewModel.startNetworkScan()
        assertEquals(true, viewModel.isNetworkScanning.value)

        viewModel.selectTransport(DeviceType.BLE)

        assertEquals(DeviceType.BLE, viewModel.activeTransport.value)
        assertEquals(false, viewModel.isNetworkScanning.value)
    }

    @Test
    fun `selecting TCP stops active ble scan`() = runTest {
        viewModel.startBleScan()
        assertEquals(true, viewModel.isBleScanning.value)

        viewModel.selectTransport(DeviceType.TCP)

        assertEquals(DeviceType.TCP, viewModel.activeTransport.value)
        assertEquals(false, viewModel.isBleScanning.value)
    }

    @Test
    fun `selecting USB stops all active scans`() = runTest {
        viewModel.startBleScan()
        assertEquals(true, viewModel.isBleScanning.value)

        viewModel.selectTransport(DeviceType.USB)

        assertEquals(DeviceType.USB, viewModel.activeTransport.value)
        assertEquals(false, viewModel.isBleScanning.value)
        assertEquals(false, viewModel.isNetworkScanning.value)

        viewModel.startNetworkScan()
        assertEquals(true, viewModel.isNetworkScanning.value)

        viewModel.selectTransport(DeviceType.USB)

        assertEquals(false, viewModel.isBleScanning.value)
        assertEquals(false, viewModel.isNetworkScanning.value)
    }

    // Manual scan control.

    @Test
    fun `startBleScan succeeds while Connected`() = runTest {
        serviceRepository.setConnectionState(ConnectionState.Connected)
        testScheduler.advanceUntilIdle()

        viewModel.startBleScan()

        assertEquals(true, viewModel.isBleScanning.value)
    }

    @Test
    fun `startNetworkScan succeeds while Connected`() = runTest {
        serviceRepository.setConnectionState(ConnectionState.Connected)
        testScheduler.advanceUntilIdle()

        viewModel.startNetworkScan()

        assertEquals(true, viewModel.isNetworkScanning.value)
    }

    @Test
    fun `startBleScan succeeds while Connecting`() = runTest {
        serviceRepository.setConnectionState(ConnectionState.Connecting)
        testScheduler.advanceUntilIdle()

        viewModel.startBleScan()

        assertEquals(true, viewModel.isBleScanning.value)
    }

    @Test
    fun `startBleScan succeeds while DeviceSleep`() = runTest {
        serviceRepository.setConnectionState(ConnectionState.DeviceSleep)
        testScheduler.advanceUntilIdle()

        viewModel.startBleScan()

        assertEquals(true, viewModel.isBleScanning.value)
    }

    @Test
    fun `connectionState transition to Connected cancels active ble scan`() = runTest {
        viewModel.startBleScan()
        assertEquals(true, viewModel.isBleScanning.value)

        serviceRepository.setConnectionState(ConnectionState.Connected)
        testScheduler.advanceUntilIdle()

        assertEquals(false, viewModel.isBleScanning.value)
    }

    @Test
    fun `connectionState transition to Connected cancels active network scan`() = runTest {
        viewModel.startNetworkScan()
        assertEquals(true, viewModel.isNetworkScanning.value)

        serviceRepository.setConnectionState(ConnectionState.Connected)
        testScheduler.advanceUntilIdle()

        assertEquals(false, viewModel.isNetworkScanning.value)
    }

    @Test
    fun `startBleAutoScan skips when device already selected`() = runTest {
        harness.currentDeviceAddressFlow.value = "x01:02:03:04:05:06"

        viewModel.startBleAutoScan()

        assertEquals(false, viewModel.isBleScanning.value)
    }

    @Test
    fun `startBleAutoScan starts when no device selected`() = runTest {
        harness.currentDeviceAddressFlow.value = null

        viewModel.startBleAutoScan()

        assertEquals(true, viewModel.isBleScanning.value)
    }

    @Test
    fun `startBleAutoScan starts only when active transport is BLE`() = runTest {
        viewModel.selectTransport(DeviceType.TCP)

        viewModel.startBleAutoScan()

        assertEquals(false, viewModel.isBleScanning.value)

        viewModel.selectTransport(DeviceType.BLE)
        viewModel.startBleAutoScan()

        assertEquals(true, viewModel.isBleScanning.value)
    }

    @Test
    fun `startNetworkAutoScan starts only when active transport is TCP`() = runTest {
        viewModel.startNetworkAutoScan()

        assertEquals(false, viewModel.isNetworkScanning.value)

        viewModel.selectTransport(DeviceType.TCP)
        viewModel.startNetworkAutoScan()

        assertEquals(true, viewModel.isNetworkScanning.value)
    }

    // ── Toggle persistence: enable clears the opposite persisted auto-scan pref ──────────────

    @Test
    fun `toggleBleScan enabling scan clears networkAutoScan`() = runTest {
        harness.uiPrefs.setNetworkAutoScan(true)
        assertEquals(true, viewModel.networkAutoScan.value)

        viewModel.toggleBleScan()

        // Successful enable persisted bleAutoScan=true AND cleared the opposite pref to mirror the runtime
        // mutual-exclusion invariant in persisted state.
        assertEquals(true, viewModel.isBleScanning.value)
        assertEquals(true, viewModel.bleAutoScan.value)
        assertEquals(false, viewModel.networkAutoScan.value)
    }

    @Test
    fun `toggleNetworkScan enabling scan clears bleAutoScan`() = runTest {
        harness.uiPrefs.setBleAutoScan(true)
        assertEquals(true, viewModel.bleAutoScan.value)

        viewModel.toggleNetworkScan()

        assertEquals(true, viewModel.isNetworkScanning.value)
        assertEquals(true, viewModel.networkAutoScan.value)
        assertEquals(false, viewModel.bleAutoScan.value)
    }

    // ── onSelected stops active scan before connection setup ────────────────────────────────

    @Test
    fun `onSelected bonded BLE stops active scan before changing device`() = runTest {
        val entry =
            DeviceListEntry.Ble(device = FakeBleDevice(address = "01:02:03:04:05:06", name = "Node"), bonded = true)

        viewModel.startBleScan()
        assertEquals(true, viewModel.isBleScanning.value)

        viewModel.onSelected(entry)
        testScheduler.advanceUntilIdle()

        // stopAllScans() runs before changeDeviceAddress() in onSelected — scan flag flips to false and
        // the radio controller sees the new address.
        assertEquals(false, viewModel.isBleScanning.value)
        assertEquals(entry.fullAddress, radioController.lastSetDeviceAddress)
    }

    @Test
    fun `onSelected TCP stops active network scan before changing device`() = runTest {
        val entry = DeviceListEntry.Tcp(name = "TCP Node", fullAddress = "t192.168.1.50")

        viewModel.startNetworkScan()
        assertEquals(true, viewModel.isNetworkScanning.value)

        viewModel.onSelected(entry)
        testScheduler.advanceUntilIdle()

        assertEquals(false, viewModel.isNetworkScanning.value)
        assertEquals(entry.fullAddress, radioController.lastSetDeviceAddress)
    }

    @Test
    fun `onSelected records active transport for BLE TCP and USB entries`() = runTest {
        val bleEntry =
            DeviceListEntry.Ble(device = FakeBleDevice(address = "01:02:03:04:05:06", name = "BLE Node"), bonded = true)
        val tcpEntry = DeviceListEntry.Tcp(name = "TCP Node", fullAddress = "t192.168.1.50")
        val usbEntry =
            DeviceListEntry.Usb(
                usbData = object : org.meshtastic.feature.connections.model.UsbDeviceData {},
                name = "USB Node",
                fullAddress = "s/dev/bus/usb/001/002",
                bonded = true,
            )

        viewModel.onSelected(bleEntry)
        assertEquals(DeviceType.BLE, viewModel.activeTransport.value)

        viewModel.onSelected(tcpEntry)
        assertEquals(DeviceType.TCP, viewModel.activeTransport.value)

        viewModel.onSelected(usbEntry)
        assertEquals(DeviceType.USB, viewModel.activeTransport.value)
    }

    // ── persistNetworkAutoScanIntent invariant ───────────────────────────────────────────────

    @Test
    fun `persistNetworkAutoScanIntent true clears bleAutoScan`() = runTest {
        harness.uiPrefs.setBleAutoScan(true)
        assertEquals(true, viewModel.bleAutoScan.value)

        viewModel.persistNetworkAutoScanIntent(true)

        // Persisting a network-auto-scan intent must clear the opposite BLE pref so persisted state
        // mirrors the runtime mutual-exclusion invariant — at most one of the two may be true.
        assertEquals(false, viewModel.bleAutoScan.value)
        assertEquals(true, viewModel.networkAutoScan.value)
    }

    // ── connectToManualAddress stops scans before changing device ────────────────────────────

    @Test
    fun `connectToManualAddress stops active network scan and changes device address`() = runTest {
        viewModel.startNetworkScan()
        assertEquals(true, viewModel.isNetworkScanning.value)

        viewModel.connectToManualAddress("t192.168.1.99")
        testScheduler.advanceUntilIdle()

        assertEquals(false, viewModel.isNetworkScanning.value)
        assertEquals("t192.168.1.99", radioController.lastSetDeviceAddress)
    }

    private fun failingScanFlow() = flow<BleDevice> {
        throw BleScanStartException(
            reason = BleScanStartFailureReason.ApplicationRegistrationFailed,
            cause = IllegalStateException("Failed to start scan as app cannot be registered"),
        )
    }
}

/**
 * Loads the scan-failure strings into the compose-resources cache up front.
 *
 * The first read of a resource completes on an internal `Dispatchers.Default` scope, so an un-warmed lookup lands after
 * the caller has moved on. Warming keeps the error message observable synchronously, which these tests need because
 * they also assert on exact virtual-time retry cooldowns and so cannot wait in real time.
 *
 * Best-effort via [safeCatchingAll], mirroring the ViewModel: the androidHostTest stubs leave `Resources.getSystem()`
 * unmocked and skiko's initializer can raise an `Error` here, so resources never resolve and the untranslated fallback
 * — identical text, produced synchronously — is what the assertions match. Cancellation still propagates.
 */
private suspend fun warmScanFailureStrings() {
    safeCatchingAll {
        getStringSuspend(Res.string.bluetooth_scan_start_failed)
        getStringSuspend(Res.string.bluetooth_scan_missing_permission)
        getStringSuspend(Res.string.bluetooth_disabled)
        getStringSuspend(Res.string.bluetooth_scan_location_services_disabled)
        getPluralStringSuspend(Res.plurals.bluetooth_scan_too_frequent, 1, 1L)
    }
}
