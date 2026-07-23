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
package org.meshtastic.core.network.radio

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.failBondAfterRecording
import org.meshtastic.core.testing.failBondWith
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        try {
            // start() begins connect() which is async
            // In a real test we'd verify the connection state,
            // but for now this confirms it works with the fakes.
            assertEquals(address, bleTransport.address)
        } finally {
            bleTransport.close()
        }
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
     * Virtual-time breakdown (DEFAULT_FAILURE_THRESHOLD = 3, DEFAULT_SETTLE_DELAY = 3 s): t = 3 000 ms — iteration 1
     * settle delay elapses, connectAndAwait throws, backoff 5 s starts t = 8 000 ms — backoff ends t = 11 000 ms —
     * iteration 2 settle delay elapses, connectAndAwait throws, backoff 10 s starts t = 21 000 ms — backoff ends t = 24
     * 000 ms — iteration 3 settle delay elapses, connectAndAwait throws → onDisconnect called
     */
    @Test
    fun `onDisconnect is called after DEFAULT_FAILURE_THRESHOLD consecutive failures`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device) // bounded scan resolves immediately; this test covers reconnect timing

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

        try {
            // Advance through exactly 3 failure iterations (≈24 001 ms virtual time).
            // The 4th iteration's backoff hasn't elapsed yet, so the coroutine is suspended
            // and advanceTimeBy returns cleanly.
            advanceTimeBy(24_001L)

            verify { service.onDisconnect(any(), any()) }
        } finally {
            // Cancel the reconnect loop so runTest can complete.
            bleTransport.close()
        }
    }

    /**
     * Reconnect policy must NEVER give up on its own. The transport is only ever instantiated for the user-selected
     * device, and explicit-disconnect is owned by the service layer (close()). Even after a sustained failure storm —
     * well beyond the legacy [BleReconnectPolicy.DEFAULT_MAX_FAILURES] — the transport must keep retrying and must
     * never call `onDisconnect(isPermanent = true)` from the give-up path.
     *
     * Time budget for 15 failures with bonded device (no scan): each iteration ≈ 3 s settle + immediate throw +
     * backoff. Backoffs cap at 60 s after failure 5: 5+10+20+40+60+60+60+60+60+60+60+60+60+60+60 = 735 s, plus 15×3 s
     * settle = 45 s, total ≈ 780 s. Use 800_000 ms to cover variance.
     */
    @Test
    fun `reconnect loop never gives up - no permanent disconnect from policy`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

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

        try {
            // Run well past where the legacy policy (maxFailures = 10) would have given up.
            advanceTimeBy(800_001L)

            // Transient disconnects (isPermanent = false) are expected once the failure threshold is hit;
            // the policy must NEVER signal a permanent disconnect on its own. Only explicit close()
            // (verified separately by the service layer) may emit isPermanent = true.
            verify(mode = VerifyMode.not) { service.onDisconnect(isPermanent = true, errorMessage = any()) }
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `findDevice prefers freshly scanned device over bonded device`() = runTest {
        val bondedDevice = FakeBleDevice(address = address, name = "Bonded Device")
        val scannedDevice = FakeBleDevice(address = address, name = "Scanned Device")
        bluetoothRepository.bond(bondedDevice)
        scanner.emitDevice(scannedDevice)

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
        try {
            advanceTimeBy(3_001)

            assertEquals("Scanned Device", connection.device?.name)
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `findDevice falls back to bonded device when fresh scans miss`() = runTest {
        val bondedDevice = FakeBleDevice(address = address, name = "Bonded Device")
        bluetoothRepository.bond(bondedDevice)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)

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
        try {
            // 3s settle + one 5s bounded scan before bonded fallback.
            advanceTimeBy(9_000)

            assertEquals(1, connection.connectAndAwaitCalls, "Must use bounded bonded fallback after fresh scans miss")
            assertEquals("Bonded Device", connection.device?.name)
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `findDevice scans with both service UUID and address`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        scanner.emitDevice(device)

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
        try {
            advanceTimeBy(3_001)

            assertNotNull(scanner.lastScanServiceUuid, "scan must include serviceUuid")
            assertEquals(SERVICE_UUID, scanner.lastScanServiceUuid)
            assertEquals(address, scanner.lastScanAddress)
        } finally {
            bleTransport.close()
        }
    }

    // --- Transport-side bond failure handling ---

    @Test
    fun `bond succeeds then connectAndAwait is called`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        scanner.emitDevice(device)

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
        try {
            advanceTimeBy(3_001)
            assertEquals(1, bluetoothRepository.bondCalls.size, "bond() must be called before connecting")
            assertEquals(1, connection.connectAndAwaitCalls, "connectAndAwait must be called after successful bond")
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `bond throws but device is bonded then connectAndAwait is still called`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        scanner.emitDevice(device)
        bluetoothRepository.failBondAfterRecording(Exception("bond flaked"))

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
        try {
            advanceTimeBy(3_001)
            assertEquals(1, bluetoothRepository.bondCalls.size, "bond() must be attempted before connecting")
            assertEquals(
                1,
                connection.connectAndAwaitCalls,
                "connectAndAwait must be called when bond flaked but device is bonded",
            )
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `bond throws and device not bonded then connectAndAwait is not called`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        scanner.emitDevice(device)
        bluetoothRepository.failBondWith(Exception("bond rejected"))

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
        try {
            // Advance past one failed bond attempt and retry backoff, but before the next bond attempt.
            advanceTimeBy(10_001)
            assertEquals(
                0,
                connection.connectAndAwaitCalls,
                "connectAndAwait must not be called when bond failed and device is not bonded",
            )
            assertEquals(1, bluetoothRepository.bondCalls.size, "bond() must have been attempted once")
        } finally {
            bleTransport.close()
        }
    }

    /**
     * When [bluetoothRepository.bond] throws a [CancellationException], it must propagate as coroutine cancellation —
     * NOT be swallowed into a generic [BleReconnectPolicy.Outcome.Failed].
     *
     * Discriminator: with the `catch (CancellationException) { throw e }` guard present, the job is cancelled and bond
     * is called exactly once. If the guard were removed, the CE would be caught by `catch (Exception)`, converted to
     * `RadioNotConnectedException`, become `Outcome.Failed`, and the policy would retry — making `bondCalls.size > 1`.
     */
    @Test
    fun `CancellationException from bond cancels the connection job without retrying`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        scanner.emitDevice(device)
        bluetoothRepository.bondOutcome =
            FakeBluetoothRepository.BondOutcome.Fail(CancellationException("simulated cancel"))

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
        try {
            advanceTimeBy(12_000)
            assertEquals(
                1,
                bluetoothRepository.bondCalls.size,
                "bond() must be called exactly once — cancellation, not retry",
            )
            assertEquals(0, connection.connectAndAwaitCalls, "connectAndAwait must not be called after cancellation")
        } finally {
            bleTransport.close()
        }
    }

    /**
     * Post-OTA GATT cache invalidation: when the service-layer one-shot flag is armed
     * ([RadioInterfaceService.requestGattCacheInvalidationOnNextConnect]), [BleRadioTransport.attemptConnection] must
     * consume it and call [BleConnection.invalidateServiceCache]. When the cache refresh succeeds it disconnects and
     * reconnects once to force fresh service discovery.
     *
     * Uses [FakeRadioInterfaceService] as the callback so the flag's real atomic getAndSet semantics drive the
     * consume-once behavior under test.
     */
    @Test
    fun `post-OTA cache invalidation flag is consumed during connect and triggers reconnect`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)
        connection.invalidateServiceCacheResult = true

        val radioService = FakeRadioInterfaceService()
        radioService.requestGattCacheInvalidationOnNextConnect()

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = radioService,
                address = address,
            )
        bleTransport.start()
        try {
            // 3s settle + connectAndAwait + 500ms reconnect delay + reconnect connectAndAwait, then profile setup
            // bounded by CONNECTED_GATE_TIMEOUT / SUBSCRIPTION_READY_TIMEOUT (5s each).
            advanceTimeBy(20_000)

            // The transport consumed the flag during its first connect cycle: a second consume must return false.
            assertFalse(
                radioService.consumeGattCacheInvalidationRequest(),
                "GATT cache invalidation flag must have been consumed by the transport",
            )
            assertTrue(
                connection.invalidateServiceCacheCalls >= 1,
                "invalidateServiceCache must be called after consuming the flag",
            )
            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "transport must reconnect after cache invalidation (got ${connection.connectAndAwaitCalls} calls)",
            )
        } finally {
            bleTransport.close()
        }
    }
}
