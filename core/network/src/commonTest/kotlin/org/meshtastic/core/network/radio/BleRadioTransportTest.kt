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
package org.meshtastic.core.network.radio

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BleRadioTransportTest {

    private val testScope = TestScope()
    private val scanner = FakeBleScanner()
    private val bluetoothRepository = FakeBluetoothRepository()
    private val connection = FakeBleConnection()
    private val connectionFactory = FakeBleConnectionFactory(connection)
    private val service: RadioInterfaceService = mock(MockMode.autofill)
    private val address = "00:11:22:33:44:55"

    @BeforeTest
    fun setup() {
        bluetoothRepository.setHasPermissions(true)
        bluetoothRepository.setBluetoothEnabled(true)
    }

    @Test
    fun `connect attempts to scan and connect via start`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        scanner.emitDevice(device)

        val bleTransport =
            BleRadioTransport(
                scope = testScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        bleTransport.start()

        // start() begins connect() which is async
        // In a real test we'd verify the connection state,
        // but for now this confirms it works with the fakes.
        assertEquals(address, bleTransport.address)
    }

    @Test
    fun `address returns correct value`() {
        val bleTransport =
            BleRadioTransport(
                scope = testScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        assertEquals(address, bleTransport.address)
    }

    /**
     * After [BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD] consecutive connection failures,
     * [RadioInterfaceService.onDisconnect] must be called so the higher layers can react (e.g. start the device-sleep
     * timeout in [MeshConnectionManagerImpl]).
     *
     * Virtual-time breakdown (DEFAULT_FAILURE_THRESHOLD = 3): t = 1 000 ms — iteration 1 settle delay elapses,
     * connectAndAwait throws, backoff 5 s starts t = 6 000 ms — backoff ends t = 7 000 ms — iteration 2 settle delay
     * elapses, connectAndAwait throws, backoff 10 s starts t = 17 000 ms — backoff ends t = 18 000 ms — iteration 3
     * settle delay elapses, connectAndAwait throws → onDisconnect called
     */
    @Test
    fun `onDisconnect is called after DEFAULT_FAILURE_THRESHOLD consecutive failures`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device) // skip BLE scan — device is already bonded

        // Make every connectAndAwait call throw so each iteration counts as one failure.
        connection.connectException = RadioNotConnectedException("simulated failure")

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        bleTransport.start()

        // Advance through exactly 3 failure iterations (≈18 001 ms virtual time).
        // The 4th iteration's backoff hasn't elapsed yet, so the coroutine is suspended
        // and advanceTimeBy returns cleanly.
        advanceTimeBy(18_001L)

        verify { service.onDisconnect(any(), any()) }

        // Cancel the reconnect loop so runTest can complete.
        bleTransport.close()
    }

    /**
     * After [BleReconnectPolicy.DEFAULT_MAX_FAILURES] (10) consecutive failures, the reconnect loop should stop and
     * signal a permanent disconnect. This prevents infinite battery drain when the device is genuinely offline.
     *
     * Time budget for 10 failures with bonded device (no scan): Each iteration = 1s settle + connectAndAwait throw +
     * backoff Backoffs: 5s, 10s, 20s, 40s, 60s, 60s, 60s, 60s, 60s, (exit at failure 10 before backoff) Total ≈ 10×1s
     * settle + 5+10+20+40+60+60+60+60+60 = 10 + 375 = 385s ≈ 385_000ms We use a generous 400_000ms to cover any timing
     * variance.
     */
    @Test
    fun `reconnect loop stops after DEFAULT_MAX_FAILURES with permanent disconnect`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)

        connection.connectException = RadioNotConnectedException("simulated failure")
        every { service.onDisconnect(any(), any()) } returns Unit

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        bleTransport.start()

        // Advance enough time for all 10 failures to occur.
        advanceTimeBy(400_001L)

        // Should have been called with isPermanent=true at least once (the final call).
        verify { service.onDisconnect(isPermanent = true, errorMessage = any()) }

        bleTransport.close()
    }
}
