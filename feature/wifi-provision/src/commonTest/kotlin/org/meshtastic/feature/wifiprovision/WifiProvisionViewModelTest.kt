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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.wifiprovision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.COMMANDER_RESPONSE_UUID
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.Phase
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.ProvisionStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private lateinit var scanner: FakeBleScanner
    private lateinit var connection: FakeBleConnection
    private lateinit var viewModel: WifiProvisionViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanner = FakeBleScanner()
        connection = FakeBleConnection()
        viewModel =
            WifiProvisionViewModel(
                bleScanner = scanner,
                bleConnectionFactory = FakeBleConnectionFactory(connection),
                dispatchers = CoroutineDispatchers(
                    io = testDispatcher,
                    main = testDispatcher,
                    default = testDispatcher,
                ),
            )
    }

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
        assertEquals(ProvisionStatus.Idle, state.provisionStatus)
    }

    // -----------------------------------------------------------------------
    // connectToDevice
    // -----------------------------------------------------------------------

    @Test
    fun `connectToDevice transitions to ConnectingBle immediately`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF", name = "mpwrd-nm-1234"))
        viewModel.connectToDevice()

        // After one dispatcher step, should be in ConnectingBle
        assertEquals(Phase.ConnectingBle, viewModel.uiState.value.phase)
    }

    @Test
    fun `connectToDevice transitions to DeviceFound on success`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF", name = "mpwrd-nm-1234"))
        viewModel.connectToDevice()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.DeviceFound, state.phase)
        assertEquals("mpwrd-nm-1234", state.deviceName)
        assertNull(state.error)
    }

    @Test
    fun `connectToDevice uses device address when name is null`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF", name = null))
        viewModel.connectToDevice()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.DeviceFound, state.phase)
        assertEquals("AA:BB:CC:DD:EE:FF", state.deviceName)
    }

    @Test
    fun `connectToDevice sets error and returns to Idle on BLE connect failure`() = runTest {
        connection.failNextN = 1
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Idle, state.phase)
        assertIs<WifiProvisionError.ConnectFailed>(state.error)
    }

    @Test
    fun `connectToDevice sets error when connection throws exception`() = runTest {
        connection.connectException = RuntimeException("BLE unavailable")
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice()
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
        viewModel.connectToDevice()
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
        viewModel.connectToDevice()
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
    fun `scanNetworks reconnects if no service exists`() = runTest {
        // Don't connect first — scanNetworks should trigger connectToDevice
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.scanNetworks()
        advanceUntilIdle()

        // Should have connected (DeviceFound) via the reconnect path
        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // provisionWifi
    // -----------------------------------------------------------------------

    @Test
    fun `provisionWifi transitions to Provisioning then Connected with Success`() = runTest {
        // Connect and scan first
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice()
        advanceUntilIdle()

        emitNymeaResponse("""{"c":4,"r":0}""")
        emitNymeaResponse("""{"c":0,"r":0,"p":[{"e":"Net","m":"01","s":80,"p":1}]}""")
        viewModel.scanNetworks()
        advanceUntilIdle()

        // Now provision — enqueue success response
        emitNymeaResponse("""{"c":1,"r":0}""")
        viewModel.provisionWifi("Net", "password123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Connected, state.phase)
        assertEquals(ProvisionStatus.Success, state.provisionStatus)
    }

    @Test
    fun `provisionWifi sets Failed status on error response`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice()
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
        viewModel.connectToDevice()
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
        viewModel.connectToDevice()
        advanceUntilIdle()
        assertEquals(Phase.DeviceFound, viewModel.uiState.value.phase)

        viewModel.disconnect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Phase.Idle, state.phase)
        assertTrue(state.networks.isEmpty())
        assertNull(state.deviceName)
        assertEquals(ProvisionStatus.Idle, state.provisionStatus)
    }

    @Test
    fun `disconnect calls BLE disconnect`() = runTest {
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:FF"))
        viewModel.connectToDevice()
        advanceUntilIdle()

        viewModel.disconnect()
        advanceUntilIdle()

        assertTrue(connection.disconnectCalls >= 1, "BLE disconnect should be called")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Emit a complete nymea JSON response on the Commander Response characteristic. Uses newline-terminated encoding
     * matching [NymeaPacketCodec].
     */
    private fun emitNymeaResponse(json: String) {
        connection.service.emitNotification(COMMANDER_RESPONSE_UUID, (json + "\n").encodeToByteArray())
    }
}
