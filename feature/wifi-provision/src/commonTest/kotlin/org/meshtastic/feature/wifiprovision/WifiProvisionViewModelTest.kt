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
package org.meshtastic.feature.wifiprovision

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeApplicationCoroutineScope
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.COMMANDER_RESPONSE_UUID
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.RELEASE_TIMEOUT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.RESPONSE_TIMEOUT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.SCAN_TIMEOUT
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.Phase
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.ProvisionStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Tests for [WifiProvisionViewModel] covering the full state machine: BLE connect, device found, scan networks,
 * provisioning, disconnect, and error paths.
 *
 * The ViewModel creates [NymeaWifiService] internally with the injected [BleScanner] and [BleConnectionFactory], so we
 * drive the flow end-to-end via BLE fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiProvisionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val deviceAddress = "AA:BB:CC:DD:EE:FF"

    private lateinit var scanner: FakeBleScanner
    private lateinit var connection: FakeBleConnection
    private lateinit var viewModel: WifiProvisionViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanner = FakeBleScanner()
        connection = FakeBleConnection()
        viewModel = createViewModel(FakeBleConnectionFactory(connection))
    }

    private fun createViewModel(connectionFactory: BleConnectionFactory) = WifiProvisionViewModel(
        bleScanner = scanner,
        bleConnectionFactory = connectionFactory,
        dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
        applicationScope = FakeApplicationCoroutineScope(testDispatcher),
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is Idle with empty data`() {
        val state = viewModel.uiState.value
        assertEquals(Phase.Idle, state.phase)
        assertTrue(state.networks.isEmpty())
        assertNull(state.error)
        assertNull(state.deviceName)
        assertNull(state.ipAddress)
        assertEquals(ProvisionStatus.Idle, state.provisionStatus)
    }

    // -----------------------------------------------------------------------
    // connectToDevice
    // -----------------------------------------------------------------------

    @Test
    fun `connectToDevice transitions to ConnectingBle immediately`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF", name = "mpwrd-nm-1234"))
        viewModel.connectToDevice(deviceAddress)

        // After one dispatcher step, should be in ConnectingBle
        assertEquals(Phase.ConnectingBle, viewModel.uiState.value.phase)
    }

    @Test
    fun `connectToDevice transitions to DeviceFound on success`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF", name = "mpwrd-nm-1234"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.DeviceFound, state.phase)
        assertEquals("mpwrd-nm-1234", state.deviceName)
        assertNull(state.error)
    }

    @Test
    fun `a dismissed device chooser returns to Idle with no error`() = runTest {
        val picker =
            WifiProvisionViewModel(
                bleScanner = DismissedChooser(),
                bleConnectionFactory = FakeBleConnectionFactory(connection),
                dispatchers =
                CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
                applicationScope = FakeApplicationCoroutineScope(testDispatcher),
            )
        assertTrue(picker.picksDevice)

        picker.connectToDevice()
        advanceUntilIdle()

        assertEquals(Phase.Idle, picker.uiState.value.phase)
        assertNull(picker.uiState.value.error)
        assertTrue(picker.uiState.value.pickDismissed)
    }

    @Test
    fun `connectToDevice uses device address when name is null`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF", name = null))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.DeviceFound, state.phase)
        assertEquals("AA:BB:CC:DD:EE:FF", state.deviceName)
    }

    @Test
    fun `connectToDevice sets error and returns to Idle on BLE connect failure`() = runTest {
        connection.failNextN = 1
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Idle, state.phase)
        assertIs<WifiProvisionError.ConnectFailed>(state.error)
    }

    @Test
    fun `connectToDevice sets error when connection throws exception`() = runTest {
        connection.connectException = RuntimeException("BLE unavailable")
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Idle, state.phase)
        val error = assertIs<WifiProvisionError.ConnectFailed>(state.error)
        assertTrue(error.detail.contains("BLE unavailable"))
    }

    // -----------------------------------------------------------------------
    // scanNetworks
    // -----------------------------------------------------------------------

    @Test
    fun `scanNetworks transitions to LoadingNetworks then Connected with results`() = runTest {
        // First connect
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()
        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)

        // Enqueue nymea responses: scan ack + networks response
        emitNymeaResponse("""{"c":4,"r":0}""")
        emitNymeaResponse("""{"c":0,"r":0,"p":[{"e":"TestNet","m":"AA:BB","s":80,"p":1}]}""")

        viewModel.scanNetworks()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Connected, state.phase)
        assertEquals(1, state.networks.size)
        assertEquals("TestNet", state.networks[0].ssid)
        assertEquals(80, state.networks[0].signalStrength)
        assertTrue(state.networks[0].isProtected)
    }

    @Test
    fun `scanNetworks deduplicates networks by SSID`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        emitNymeaResponse("""{"c":4,"r":0}""")
        emitNymeaResponse(
            """{"c":0,"r":0,"p":[
                {"e":"Dup","m":"01","s":30,"p":1},
                {"e":"Dup","m":"02","s":90,"p":1},
                {"e":"Unique","m":"03","s":60,"p":0}
            ]}""",
        )

        viewModel.scanNetworks()
        advanceUntilIdle()

        val networks = viewModel.uiState.value.networks
        assertEquals(2, networks.size, "Duplicates should be merged")
        assertEquals("Dup", networks[0].ssid)
        assertEquals(90, networks[0].signalStrength, "Should keep strongest signal")
    }

    @Test
    fun `scanNetworks without a connection connects nothing`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.scanNetworks()
        advanceUntilIdle()

        assertEquals(Phase.Idle, viewModel.uiState.value.phase)
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    // -----------------------------------------------------------------------
    // provisionWifi
    // -----------------------------------------------------------------------

    @Test
    fun `provisionWifi transitions to Provisioning then Connected with Success`() = runTest {
        // Connect and scan first
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        emitNymeaResponse("""{"c":4,"r":0}""")
        emitNymeaResponse("""{"c":0,"r":0,"p":[{"e":"Net","m":"01","s":80,"p":1}]}""")
        viewModel.scanNetworks()
        advanceUntilIdle()

        // Now provision — enqueue success response
        emitNymeaResponse("""{"c":1,"r":0,"p":{"i":"10.10.10.61"}}""")
        viewModel.provisionWifi("Net", "password123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Connected, state.phase)
        assertEquals("10.10.10.61", state.ipAddress)
        assertEquals(ProvisionStatus.Success, state.provisionStatus)
    }

    @Test
    fun `provisionWifi sets Failed status on error response`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        emitNymeaResponse("""{"c":4,"r":0}""")
        emitNymeaResponse("""{"c":0,"r":0,"p":[]}""")
        viewModel.scanNetworks()
        advanceUntilIdle()

        // Provision with error code 3 (NetworkManager unavailable)
        emitNymeaResponse("""{"c":1,"r":3}""")
        viewModel.provisionWifi("Net", "pass")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Connected, state.phase)
        assertEquals(ProvisionStatus.Failed, state.provisionStatus)
        assertIs<WifiProvisionError.ProvisionFailed>(state.error)
    }

    @Test
    fun `provisionWifi ignores blank SSID`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        val phaseBefore = viewModel.uiState.value.phase
        viewModel.provisionWifi("  ", "pass")
        advanceUntilIdle()

        // Phase should not change — blank SSID is a no-op
        assertEquals(phaseBefore, viewModel.uiState.value.phase)
    }

    @Test
    fun `provisionWifi no-ops when service is null`() = runTest {
        // Don't connect — service is null
        viewModel.provisionWifi("Net", "pass")
        advanceUntilIdle()

        assertEquals(Phase.Idle, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // disconnect
    // -----------------------------------------------------------------------

    @Test
    fun `disconnect resets state to initial`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()
        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)

        viewModel.disconnect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Idle, state.phase)
        assertTrue(state.networks.isEmpty())
        assertNull(state.deviceName)
        assertNull(state.ipAddress)
        assertEquals(ProvisionStatus.Idle, state.provisionStatus)
    }

    @Test
    fun `disconnect calls BLE disconnect`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        viewModel.disconnect()
        advanceUntilIdle()

        assertTrue(connection.disconnectCalls >= 1, "BLE disconnect should be called")
    }

    // -----------------------------------------------------------------------
    // Device choice
    // -----------------------------------------------------------------------

    private val firstSeen = FakeBleDevice("00:00:00:00:00:0A", name = "first seen", rssi = -80)
    private val strongest = FakeBleDevice("00:00:00:00:00:0B", name = "strongest", rssi = -50)
    private val chosen = FakeBleDevice("00:00:00:00:00:0C", name = "chosen", rssi = -65)

    @Test
    fun `listed devices connect nothing until one is tapped and the tapped one is connected`() = runTest {
        scanner.emitDevice(firstSeen)
        scanner.emitDevice(strongest)
        scanner.emitDevice(chosen)

        viewModel.open(null)
        runCurrent()

        assertEquals(Phase.Discovering, viewModel.uiState.value.phase)
        assertEquals(
            listOf(strongest.address, chosen.address, firstSeen.address),
            viewModel.uiState.value.devices.map { it.address },
        )
        assertEquals(0, connection.connectAndAwaitCalls)

        advanceUntilIdle()
        assertEquals(Phase.DeviceList, viewModel.uiState.value.phase)
        assertEquals(0, connection.connectAndAwaitCalls)

        viewModel.connectToDiscovered(chosen.address)
        advanceUntilIdle()

        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)
        assertEquals(chosen.address, connection.device?.address)
        assertEquals(chosen.address, viewModel.uiState.value.deviceAddress)
        assertEquals("chosen", viewModel.uiState.value.deviceName)
        assertEquals(1, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a single advertiser is listed and not connected`() = runTest {
        scanner.emitDevice(strongest)

        viewModel.open(null)
        advanceUntilIdle()

        assertEquals(Phase.DeviceList, viewModel.uiState.value.phase)
        assertEquals(listOf(strongest.address), viewModel.uiState.value.devices.map { it.address })
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a scan that finds nothing offers a rescan that lists a device found later`() = runTest {
        viewModel.open(null)
        advanceUntilIdle()
        assertEquals(Phase.DeviceList, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.devices.isEmpty())

        scanner.emitDevice(chosen)
        viewModel.rescan()
        advanceUntilIdle()

        assertEquals(listOf(chosen.address), viewModel.uiState.value.devices.map { it.address })
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `connectToDevice without an address lists devices where the app scans itself`() = runTest {
        scanner.emitDevice(firstSeen)

        viewModel.connectToDevice()
        advanceUntilIdle()

        assertEquals(Phase.DeviceList, viewModel.uiState.value.phase)
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a device chooser still connects the device it returns without an address`() = runTest {
        val picker =
            WifiProvisionViewModel(
                bleScanner = PickingChooser(chosen),
                bleConnectionFactory = FakeBleConnectionFactory(connection),
                dispatchers =
                CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
                applicationScope = FakeApplicationCoroutineScope(testDispatcher),
            )

        picker.connectToDevice()
        advanceUntilIdle()

        assertEquals(Phase.DeviceFound, picker.uiState.value.phase)
        assertEquals(chosen.address, picker.uiState.value.deviceAddress)
    }

    @Test
    fun `a supplied address waits for confirmation and connects nothing`() = runTest {
        scanner.emitDevice(firstSeen)
        scanner.emitDevice(chosen)

        viewModel.open(chosen.address)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.ConfirmDevice, state.phase)
        assertEquals(chosen.address, state.pendingAddress)
        assertEquals("chosen", state.pendingName)
        assertEquals(0, connection.connectAndAwaitCalls)

        viewModel.confirmConnect()
        advanceUntilIdle()

        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)
        assertEquals(chosen.address, connection.device?.address)
    }

    @Test
    fun `cancelling the confirmation leaves the supplied address unconnected`() = runTest {
        scanner.emitDevice(chosen)

        viewModel.open(chosen.address)
        advanceUntilIdle()
        viewModel.disconnect()
        viewModel.confirmConnect()
        advanceUntilIdle()

        assertEquals(Phase.Idle, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.pendingAddress)
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a connect to an address that never advertises ends in the not found state at the scan timeout`() = runTest {
        scanner.emitDevice(firstSeen)

        viewModel.connectToDevice(chosen.address)
        advanceTimeBy(SCAN_TIMEOUT.inWholeMilliseconds - 1)
        runCurrent()
        assertEquals(Phase.ConnectingBle, viewModel.uiState.value.phase)

        advanceTimeBy(2)
        runCurrent()

        assertEquals(Phase.Idle, viewModel.uiState.value.phase)
        assertEquals(WifiProvisionError.DeviceNotFound, viewModel.uiState.value.error)
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `cancelling while a connect is still scanning surfaces no error`() = runTest {
        scanner.emitDevice(firstSeen)

        viewModel.connectToDevice(chosen.address)
        runCurrent()
        viewModel.disconnect()
        advanceUntilIdle()

        assertEquals(Phase.Idle, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a device that never answers the network scan drops the connection and shows the not responding error`() =
        runTest {
            scanner.emitDevice(chosen)
            viewModel.connectToDevice(chosen.address)
            advanceUntilIdle()

            viewModel.scanNetworks()
            advanceUntilIdle()

            assertEquals(Phase.Idle, viewModel.uiState.value.phase)
            assertEquals(WifiProvisionError.DeviceNotResponding, viewModel.uiState.value.error)
            assertEquals(1, connection.disconnectCalls)
        }

    @Test
    fun `a device that never answers the credentials drops the connection and shows the not responding error`() =
        runTest {
            connectAndLoadNetworks()

            viewModel.provisionWifi("Net", "pass")
            advanceUntilIdle()

            assertEquals(Phase.Idle, viewModel.uiState.value.phase)
            assertEquals(ProvisionStatus.Idle, viewModel.uiState.value.provisionStatus)
            assertEquals(WifiProvisionError.DeviceNotResponding, viewModel.uiState.value.error)
            assertEquals(1, connection.disconnectCalls)
        }

    @Test
    fun `disconnecting during a network scan leaves the reset state after every timer has run`() = runTest {
        scanner.emitDevice(chosen)
        viewModel.connectToDevice(chosen.address)
        advanceUntilIdle()

        viewModel.scanNetworks()
        runCurrent()
        assertEquals(Phase.LoadingNetworks, viewModel.uiState.value.phase)
        viewModel.disconnect()
        advanceUntilIdle()

        assertEquals(WifiProvisionUiState(), viewModel.uiState.value)
    }

    @Test
    fun `disconnecting during provisioning leaves the reset state after every timer has run`() = runTest {
        connectAndLoadNetworks()

        viewModel.provisionWifi("Net", "pass")
        runCurrent()
        assertEquals(Phase.Provisioning, viewModel.uiState.value.phase)
        viewModel.disconnect()
        advanceUntilIdle()

        assertEquals(WifiProvisionUiState(), viewModel.uiState.value)
    }

    @Test
    fun `a retry starts no connection until the previous one has finished closing`() = runTest {
        val retrying =
            createViewModel(FixedBleConnectionFactory(SlowCloseConnection(connection, closeDelay = 3.seconds)))
        scanner.emitDevice(chosen)
        retrying.connectToDevice(chosen.address)
        advanceUntilIdle()
        retrying.scanNetworks()
        advanceTimeBy(RESPONSE_TIMEOUT.inWholeMilliseconds + 1)
        runCurrent()
        assertEquals(Phase.Idle, retrying.uiState.value.phase)
        assertEquals(1, connection.connectAndAwaitCalls)

        retrying.connectToDevice(chosen.address)
        runCurrent()
        assertEquals(1, connection.connectAndAwaitCalls)
        advanceTimeBy(2.seconds.inWholeMilliseconds)
        runCurrent()
        assertEquals(1, connection.connectAndAwaitCalls)
        advanceTimeBy(2.seconds.inWholeMilliseconds)
        runCurrent()

        assertEquals(2, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a retry goes ahead once the previous connection has been closing for the release timeout`() = runTest {
        val retrying = createViewModel(FixedBleConnectionFactory(SlowCloseConnection(connection, closeDelay = 1.hours)))
        scanner.emitDevice(chosen)
        retrying.connectToDevice(chosen.address)
        advanceUntilIdle()
        retrying.scanNetworks()
        advanceTimeBy(RESPONSE_TIMEOUT.inWholeMilliseconds + 1)
        runCurrent()

        retrying.connectToDevice(chosen.address)
        advanceTimeBy(RELEASE_TIMEOUT.inWholeMilliseconds - 1)
        runCurrent()
        assertEquals(1, connection.connectAndAwaitCalls)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(2, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a retry waits for every connection still closing and not only the oldest`() = runTest {
        // The first close outlasts the release timeout, so the second connection opens while it is still closing; the
        // third attempt then has two closes in flight and the newer one finishes last.
        val first = FakeBleConnection()
        val second = FakeBleConnection()
        val third = FakeBleConnection()
        val firstClose = RELEASE_TIMEOUT + 2.seconds
        val secondClose = 5.seconds
        val connections =
            ArrayDeque<BleConnection>(
                listOf(SlowCloseConnection(first, firstClose), SlowCloseConnection(second, secondClose), third),
            )
        val retrying =
            createViewModel(
                object : BleConnectionFactory {
                    override fun create(scope: CoroutineScope, tag: String): BleConnection = connections.removeFirst()
                },
            )
        scanner.emitDevice(chosen)
        retrying.connectToDevice(chosen.address)
        advanceUntilIdle()

        retrying.connectToDevice(chosen.address)
        advanceTimeBy(RELEASE_TIMEOUT.inWholeMilliseconds + 1)
        runCurrent()
        assertEquals(1, second.connectAndAwaitCalls)

        retrying.connectToDevice(chosen.address)
        advanceTimeBy((firstClose - RELEASE_TIMEOUT).inWholeMilliseconds + 1)
        runCurrent()
        assertEquals(1, first.disconnectCalls)
        assertEquals(0, third.connectAndAwaitCalls)

        advanceTimeBy(secondClose.inWholeMilliseconds)
        runCurrent()

        assertEquals(1, third.connectAndAwaitCalls)
    }

    @Test
    fun `a connection whose close fails neither crashes nor stops the retry`() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val applicationScope =
            object :
                ApplicationCoroutineScope,
                CoroutineScope by CoroutineScope(
                    SupervisorJob() + testDispatcher + CoroutineExceptionHandler { _, e -> uncaught += e },
                ) {}
        val retrying =
            WifiProvisionViewModel(
                bleScanner = scanner,
                bleConnectionFactory =
                FixedBleConnectionFactory(
                    SlowCloseConnection(
                        connection,
                        closeDelay = 1.seconds,
                        closeFailure = IllegalStateException("x"),
                    ),
                ),
                dispatchers =
                CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
                applicationScope = applicationScope,
            )
        scanner.emitDevice(chosen)
        retrying.connectToDevice(chosen.address)
        advanceUntilIdle()
        retrying.scanNetworks()
        advanceTimeBy(RESPONSE_TIMEOUT.inWholeMilliseconds + 1)
        runCurrent()

        retrying.connectToDevice(chosen.address)
        advanceUntilIdle()

        assertEquals(2, connection.connectAndAwaitCalls)
        assertEquals(Phase.DeviceFound, retrying.uiState.value.phase)
        assertTrue(uncaught.isEmpty(), "close failure escaped: $uncaught")
    }

    @Test
    fun `a second Apply while the credentials are in flight sends nothing more`() = runTest {
        connectAndLoadNetworks()

        viewModel.provisionWifi("Net", "pass")
        runCurrent()
        val written = connection.service.writes.size
        viewModel.provisionWifi("Net", "pass")
        emitNymeaResponse("""{"c":1,"r":0,"p":{"i":"10.0.0.7"}}""")
        advanceUntilIdle()

        assertEquals(written, connection.service.writes.size)
        assertEquals(ProvisionStatus.Success, viewModel.uiState.value.provisionStatus)
    }

    @Test
    fun `a tap on a listed device after a connection has started is ignored`() = runTest {
        scanner.emitDevice(firstSeen)
        scanner.emitDevice(chosen)
        viewModel.open(null)
        runCurrent()

        viewModel.connectToDiscovered(chosen.address)
        viewModel.connectToDiscovered(firstSeen.address)
        advanceUntilIdle()

        assertEquals(chosen.address, connection.device?.address)
        assertEquals(1, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a new connection after disconnecting mid operation works`() = runTest {
        scanner.emitDevice(chosen)
        viewModel.connectToDevice(chosen.address)
        advanceUntilIdle()
        viewModel.scanNetworks()
        runCurrent()
        viewModel.disconnect()
        advanceUntilIdle()

        viewModel.connectToDevice(chosen.address)
        advanceUntilIdle()

        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)
    }

    @Test
    fun `a link address a platform cannot look up falls back to the list and connects nothing`() = runTest {
        scanner.addressIsHardware = false
        val opaque = createViewModel(FakeBleConnectionFactory(connection))
        scanner.emitDevice(chosen)

        opaque.open(chosen.address)
        advanceUntilIdle()

        val state = opaque.uiState.value
        assertEquals(Phase.DeviceList, state.phase)
        assertTrue(state.linkAddressIgnored)
        assertFalse(state.addressIsHardware)
        assertNull(state.pendingAddress)
        assertEquals(listOf(chosen.address), state.devices.map { it.address })
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a link address a browser chooser cannot look up waits for the pick and connects nothing`() = runTest {
        val picker =
            WifiProvisionViewModel(
                bleScanner = PickingChooser(chosen, addressIsHardware = false),
                bleConnectionFactory = FakeBleConnectionFactory(connection),
                dispatchers =
                CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
                applicationScope = FakeApplicationCoroutineScope(testDispatcher),
            )

        picker.open(chosen.address)
        advanceUntilIdle()

        assertEquals(Phase.Idle, picker.uiState.value.phase)
        assertTrue(picker.uiState.value.linkAddressIgnored)
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a link MAC reaches a device that reports a BlueZ path and the address shown is the MAC`() = runTest {
        val linux = FakeBleDevice("/org/bluez/hci0/dev_00_00_00_00_00_0C", name = "linux")
        scanner.emitDevice(firstSeen)
        scanner.emitDevice(linux)

        viewModel.open("00:00:00:00:00:0C")
        advanceUntilIdle()
        assertEquals("linux", viewModel.uiState.value.pendingName)

        viewModel.confirmConnect()
        advanceUntilIdle()

        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)
        assertEquals(linux.address, connection.device?.address)
        assertEquals("00:00:00:00:00:0C", viewModel.uiState.value.deviceAddress)
    }

    @Test
    fun `starting discovery does not discard a pending confirmation`() = runTest {
        scanner.emitDevice(chosen)

        viewModel.open(chosen.address)
        viewModel.open(null)
        advanceUntilIdle()

        assertEquals(Phase.ConfirmDevice, viewModel.uiState.value.phase)
        assertEquals(chosen.address, viewModel.uiState.value.pendingAddress)
    }

    // -----------------------------------------------------------------------
    // Connection ownership
    // -----------------------------------------------------------------------

    @Test
    fun `reconnecting closes the previous BLE connection`() = runTest {
        val factory = RecordingBleConnectionFactory()
        val viewModel = createViewModel(factory)
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))

        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        assertEquals(2, factory.created.size)
        assertEquals(1, factory.created[0].disconnectCalls)
        assertEquals(0, factory.created[1].disconnectCalls)
        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)
    }

    @Test
    fun `reconnecting cancels a connect that is still scanning`() = runTest {
        val factory = RecordingBleConnectionFactory()
        val viewModel = createViewModel(factory)

        viewModel.connectToDevice(deviceAddress)
        runCurrent()
        viewModel.connectToDevice(deviceAddress)
        runCurrent()
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        advanceUntilIdle()

        assertEquals(0, factory.created[0].connectAndAwaitCalls)
        assertEquals(1, factory.created[1].connectAndAwaitCalls)
        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)
    }

    @Test
    fun `clearing the ViewModel closes the current BLE connection`() = runTest {
        val factory = RecordingBleConnectionFactory()
        val viewModel = createViewModel(factory)
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice(deviceAddress)
        advanceUntilIdle()

        val store = ViewModelStore()
        store.put("wifiProvisionViewModel", viewModel)
        store.clear()
        advanceUntilIdle()

        assertEquals(1, factory.created.single().disconnectCalls)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** A browser chooser the user closed: it picks devices, and its scan ends with none. */
    private class DismissedChooser : BleScanner {
        override val picksDevice: Boolean = true

        override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> = emptyFlow()
    }

    /** A browser chooser the user answered: it picks devices, and its scan emits the one device picked. */
    private class PickingChooser(private val picked: BleDevice, override val addressIsHardware: Boolean = true) :
        BleScanner {
        override val picksDevice: Boolean = true

        override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> = flowOf(picked)
    }

    /** Shares one [connection] across every service, whose [disconnect] takes [closeDelay] and then [closeFailure]. */
    private class SlowCloseConnection(
        private val connection: FakeBleConnection,
        private val closeDelay: Duration,
        private val closeFailure: Exception? = null,
    ) : BleConnection by connection {
        override suspend fun disconnect() {
            delay(closeDelay)
            connection.disconnect()
            closeFailure?.let { throw it }
        }
    }

    private class FixedBleConnectionFactory(private val connection: BleConnection) : BleConnectionFactory {
        override fun create(scope: CoroutineScope, tag: String): BleConnection = connection
    }

    /** Hands out a fresh [FakeBleConnection] per [create], so each service's connection can be checked on its own. */
    private class RecordingBleConnectionFactory : BleConnectionFactory {
        val created = mutableListOf<FakeBleConnection>()

        override fun create(scope: CoroutineScope, tag: String): BleConnection =
            FakeBleConnection().also { created += it }
    }

    /**
     * Emit a complete nymea JSON response on the Commander Response characteristic. Uses newline-terminated encoding
     * matching [NymeaPacketCodec].
     */
    private fun emitNymeaResponse(json: String) {
        connection.service.emitNotification(COMMANDER_RESPONSE_UUID, (json + "\n").encodeToByteArray())
    }

    /** Connects to [chosen] and loads an empty network list, which is where credentials can be sent from. */
    private fun TestScope.connectAndLoadNetworks() {
        scanner.emitDevice(chosen)
        viewModel.connectToDevice(chosen.address)
        advanceUntilIdle()
        emitNymeaResponse("""{"c":4,"r":0}""")
        emitNymeaResponse("""{"c":0,"r":0,"p":[]}""")
        viewModel.scanNetworks()
        advanceUntilIdle()
        assertEquals(Phase.Connected, viewModel.uiState.value.phase)
    }
}
