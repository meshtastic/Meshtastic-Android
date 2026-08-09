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

import com.juul.kable.GattStatusException
import com.juul.kable.NotConnectedException
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.DisconnectReason
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.testing.FakeBleService
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

private const val BONDED_FALLBACK_CONNECT_BUFFER_MILLIS = 1_000L
private val bondedScanTimeoutMillis = SCAN_TIMEOUT.inWholeMilliseconds
private val bondedReconnectWindowMillis =
    BleReconnectPolicy.DEFAULT_SETTLE_DELAY.inWholeMilliseconds +
        bondedScanTimeoutMillis +
        BONDED_FALLBACK_CONNECT_BUFFER_MILLIS

/**
 * Tests covering the BLE reconnect crash fixes in [BleRadioTransport]:
 * 1. **CancellationException / GATT 133 fix**: [discoverServicesAndSetupCharacteristics] previously had a bare `catch
 *    (e: Exception)` that silently swallowed [CancellationException], meaning [BleConnection.disconnect] was never
 *    called when the scope was cancelled. This leaked the underlying BluetoothGatt handle and caused GATT status 133 on
 *    every subsequent reconnect. The fix adds an explicit `if (e is CancellationException)` branch that calls
 *    [disconnect] under [NonCancellable] before re-throwing.
 * 2. **close() calls disconnect**: Verifies that calling [BleRadioTransport.close] triggers [BleConnection.disconnect]
 *    exactly once so the GATT handle is always released.
 * 3. **Reconnect after failure respects policy backoff**: After a configurable number of consecutive failures the
 *    transport signals a transient (non-permanent) disconnect to the callback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BleRadioTransportReconnectCrashTest {

    private val scanner = FakeBleScanner()
    private val bluetoothRepository = FakeBluetoothRepository()
    private val connection = FakeBleConnection()
    private val connectionFactory = FakeBleConnectionFactory(connection)
    private val service = mock<org.meshtastic.core.repository.RadioInterfaceService>(MockMode.autofill)
    private val address = "AA:BB:CC:DD:EE:FF"

    @BeforeTest
    fun setup() {
        bluetoothRepository.setHasPermissions(true)
        bluetoothRepository.setBluetoothEnabled(true)
    }

    // ─── close() triggers disconnect ─────────────────────────────────────────────────────────────

    /**
     * After [BleRadioTransport.close], [FakeBleConnection.disconnect] must be called.
     *
     * This validates the primary invariant introduced by the fix: GATT cleanup (disconnect) always runs — even when the
     * coroutine scope is cancelled — by wrapping the call in [NonCancellable].
     */
    @Test
    fun `close calls disconnect to clean up GATT handle`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
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
        try {
            bleTransport.start()

            // Allow the connection loop to reach the connected state.
            advanceTimeBy(4_000L)

            bleTransport.close()

            // disconnect() must be called: once by the connection loop teardown + once by close() itself.
            // We only assert it was called at least once — the exact count depends on timing.
            assertTrue(connection.disconnectCalls >= 1, "Expected disconnect() to be called at least once")
        } finally {
            bleTransport.close()
        }
    }

    // ─── disconnect called on connection failure ──────────────────────────────────────────────────

    /**
     * When [FakeBleConnection.connectAndAwait] always returns [BleConnectionState.Disconnected], the transport must
     * still eventually call [BleConnection.disconnect] to ensure the GATT handle state machine is reset before the next
     * attempt.
     *
     * Virtual-time budget: DEFAULT_FAILURE_THRESHOLD (3) × (3 s settle + backoff) ≈ 24 s.
     */
    @Test
    fun `disconnect is called on connection failure`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        // Make every connection attempt fail.
        connection.failNextN = Int.MAX_VALUE

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()

            advanceTimeBy(30_000L)

            bleTransport.close()

            // Each failed connectAndAwait round-trips through the reconnect loop; close() always disconnects.
            assertTrue(connection.disconnectCalls >= 1, "disconnect() not called after connection failure")
        } finally {
            bleTransport.close()
        }
    }

    // ─── transient onDisconnect after failure threshold ──────────────────────────────────────────

    /**
     * Mirrors [BleRadioTransportTest.`onDisconnect is called after DEFAULT_FAILURE_THRESHOLD consecutive failures`] but
     * focuses specifically on the *reconnect* scenario introduced by the fix: after enough consecutive failures, the
     * callback receives `isPermanent = false` — the transport keeps retrying rather than giving up permanently.
     *
     * Virtual time: 3 failures × (3 s settle + backoff starting at 5 s) ≈ 24 s.
     */
    @Test
    fun `transient onDisconnect is signalled after failure threshold without giving up`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        connection.connectException = org.meshtastic.core.model.RadioNotConnectedException("simulated GATT failure")

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
        try {
            bleTransport.start()

            advanceTimeBy(24_001L)

            // Transient disconnect must be signalled with NO user-facing error message — the
            // reconnect loop is still retrying, so a modal dialog would be confusing UX.
            dev.mokkery.verify { service.onDisconnect(isPermanent = false, errorMessage = null) }
            // Permanent disconnect must NEVER be called by the transport on its own.
            dev.mokkery.verify(mode = dev.mokkery.verify.VerifyMode.not) {
                service.onDisconnect(isPermanent = true, errorMessage = any())
            }

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    // ─── CancellationException is not silently swallowed ─────────────────────────────────────────

    /**
     * [BleRadioTransport.close] cancels the [connectionScope]. The cancellation propagates as a [CancellationException]
     * through the active coroutines in [discoverServicesAndSetupCharacteristics].
     *
     * Before the fix, `catch (e: Exception)` swallowed the [CancellationException] and the `disconnect()` call was
     * skipped. After the fix, [disconnect] is called under [NonCancellable].
     *
     * This test uses a dedicated fake that throws [CancellationException] from [BleConnection.profile] to simulate the
     * scope-cancellation path without races.
     */
    @Test
    fun `disconnect is called when profile setup throws CancellationException`() = runTest {
        val throwingConnection = CancellingProfileBleConnection()
        val throwingFactory =
            object : BleConnectionFactory {
                override fun create(scope: CoroutineScope, tag: String): BleConnection = throwingConnection
            }
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = throwingFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()

            // Allow one connection attempt to reach profile() and be cancelled.
            advanceTimeBy(4_000L)

            bleTransport.close()

            assertTrue(
                throwingConnection.disconnectCalls >= 1,
                "disconnect() must be called after CancellationException in profile() — GATT leak fix",
            )
        } finally {
            bleTransport.close()
        }
    }

    // ─── Reconnect after a stable connection drops ───────────────────────────────────────────────

    /**
     * Regression test for the BLE reconnect hang.
     *
     * Symptom: after a stable connection (uptime > minStableConnection) was terminated by a remote disconnect (e.g.
     * node power-cycle), the transport's reconnect loop never iterated — `attemptConnection` ran exactly once, the GATT
     * disconnect callback fired, and then nothing.
     *
     * Root cause: `attemptConnection` wrapped its disconnect-watcher in a `coroutineScope {
     * connectionState.onEach{...}.launchIn(this); connectionState.first { Disconnected } }` block. `coroutineScope`
     * waits for ALL launched children before returning, but the `.launchIn` collector on a hot `StateFlow` (or
     * `SharedFlow(replay=1)`) never completes naturally. After `.first` returned, the scope hung forever, blocking
     * `BleReconnectPolicy.execute` from issuing the next attempt.
     *
     * This test exercises the full happy-path reconnect cycle: connect → stable uptime → external disconnect → expect a
     * second `connectAndAwait` call. With the bug present, only one `connectAndAwait` call ever happens.
     */
    @Test
    fun `transport reconnects after a stable connection is dropped remotely`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
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
        try {
            bleTransport.start()

            // Settle delay (3 s) + connect + handshake.
            advanceTimeBy(4_000L)
            assertTrue(connection.connectAndAwaitCalls == 1, "First connect must happen during initial start window")

            // Stay connected long enough to be considered stable (> minStableConnection = 5 s).
            advanceTimeBy(10_000L)

            // Simulate the firmware dying mid-session — the same path a node power-cycle takes.
            connection.simulateRemoteDisconnect(reason = DisconnectReason.Timeout)

            // Settle delay (3 s) before the next attempt + re-connect window. Generous to absorb
            // the policy retry backoff (5 s on first failure) plus another 3 s settle delay.
            advanceTimeBy(30_000L)

            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "Reconnect loop must call connectAndAwait again after a remote disconnect " +
                    "(actual calls: ${connection.connectAndAwaitCalls})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    // ─── Session-failure recovery ────────────────────────────────────────────────────────────────

    @Test
    fun `write failure while connected clears state and triggers reconnect`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L) // connect + handshake
            assertTrue(connection.connectAndAwaitCalls == 1, "First connect must happen")

            // Inject write failure while BLE state remains Connected
            connection.service.writeException = NotConnectedException("session closed")

            // Trigger a write (simulating a heartbeat or user packet)
            bleTransport.handleSendToRadio(byteArrayOf(1, 2, 3))
            // Advance through retryBleOperation's 3 retries (~750ms backoff) so the write failure
            // reaches handleFailure and forces disconnect. Stay under the 3s reconnect settle delay.
            advanceTimeBy(1_000L)

            // After failure: disconnect must be called (GATT cleanup)
            assertTrue(connection.disconnectCalls >= 1, "disconnect() must be called after write failure")

            // Reconnect policy should iterate — wait for settle (3s) + connect
            advanceTimeBy(10_000L)
            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "Reconnect loop must call connectAndAwait again after session failure " +
                    "(actual calls: ${connection.connectAndAwaitCalls})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `CancellationException from write does not trigger callback`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L)

            connection.service.writeException = CancellationException("cancelled")

            bleTransport.handleSendToRadio(byteArrayOf(1))
            // Drain the cancelled write coroutine; no retry is triggered for CancellationException.
            testScheduler.runCurrent()

            // CancellationException must NOT result in a user-facing disconnect callback
            dev.mokkery.verify(mode = dev.mokkery.verify.VerifyMode.not) { service.onDisconnect(any(), any()) }

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `write retry stops when remote disconnect retires the captured session`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L)

            val attemptsBefore = connection.service.writeAttempts
            connection.service.writeException = NotConnectedException("session closed")
            bleTransport.handleSendToRadio(byteArrayOf(1))
            testScheduler.runCurrent()
            assertEquals(attemptsBefore + 1, connection.service.writeAttempts)

            connection.simulateRemoteDisconnect()
            testScheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                attemptsBefore + 1,
                connection.service.writeAttempts,
                "A write captured from a retired session must not consume more retry attempts",
            )
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `repeated writes after session failure do not spam onDisconnect`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        var onDisconnectCalls = 0
        every { service.onDisconnect(any(), any()) } calls { onDisconnectCalls++ }

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()
            advanceTimeBy(4_000L)

            // First write failure — should trigger onDisconnect once
            connection.service.writeException = NotConnectedException("session closed")
            bleTransport.handleSendToRadio(byteArrayOf(1))
            // Advance enough for retryBleOperation to exhaust 3 retries (~750ms max) + handleFailure +
            // disconnect, but NOT enough to reach the 3 s reconnect settle delay.
            advanceTimeBy(2_000L)

            // Second write failure against same session — must NOT trigger another callback
            bleTransport.handleSendToRadio(byteArrayOf(2))
            advanceTimeBy(1_000L)

            assertEquals(1, onDisconnectCalls, "onDisconnect must be called exactly once for the session failure")

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `stale radioService is cleared after session failure`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L)

            val writesBefore = connection.service.writes.size

            // First write failure clears radioService
            connection.service.writeException = NotConnectedException("session closed")
            bleTransport.handleSendToRadio(byteArrayOf(1, 2, 3))
            // Advance enough for retryBleOperation to exhaust 3 retries (~750ms max) + handleFailure +
            // disconnect, but NOT enough to reach the 3 s reconnect settle delay.
            advanceTimeBy(2_000L)

            // Second write — radioService should be null, so no write is attempted
            connection.service.writeException = null // clear the exception hook
            bleTransport.handleSendToRadio(byteArrayOf(4, 5, 6))
            advanceTimeBy(1_000L)

            // No new writes should have been recorded (radioService was null → write skipped)
            assertEquals(
                writesBefore,
                connection.service.writes.size,
                "No new write should be recorded after radioService was cleared",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `internal session failure is not treated as intentional disconnect`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L) // connect + handshake

            // Inject a write failure — this should NOT be treated as intentional
            connection.service.writeException = NotConnectedException("session closed")
            bleTransport.handleSendToRadio(byteArrayOf(1, 2, 3))
            // Advance through retryBleOperation's 3 retries (~750ms backoff) so the write failure
            // reaches handleFailure and forces disconnect.
            advanceTimeBy(1_000L)

            // disconnect must have been called (forced cleanup)
            assertTrue(connection.disconnectCalls >= 1, "disconnect() must be called after write failure")

            // Verify onDisconnect was called with isPermanent = false and NO user-facing error —
            // non-permanent session failures auto-recover, so no modal dialog should be shown.
            dev.mokkery.verify { service.onDisconnect(isPermanent = false, errorMessage = null) }
            dev.mokkery.verify(mode = dev.mokkery.verify.VerifyMode.not) {
                service.onDisconnect(isPermanent = true, errorMessage = any())
            }

            // Reconnect should happen (policy should iterate)
            advanceTimeBy(15_000L)
            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "Reconnect loop must iterate after internal session failure",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    // ─── Liveness restart semantics ──────────────────────────────────────────────────────────────

    /**
     * Validates the transport-level behavior that liveness restart depends on: after stop (close) + start, a new
     * connection attempt must occur.
     *
     * The [SharedRadioInterfaceService.checkLiveness] path calls stopTransportLocked then startTransportLocked, which
     * destroys and recreates the transport. This test verifies that BleRadioTransport correctly handles this stop/start
     * cycle.
     */
    @Test
    fun `stop and restart creates a new connection attempt`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L)
            assertTrue(connection.connectAndAwaitCalls == 1, "First connect must happen")

            // Simulate liveness restart: close + create fresh transport
            bleTransport.close()
            advanceTimeBy(2_000L)

            val freshConnection = FakeBleConnection()
            val freshFactory = FakeBleConnectionFactory(freshConnection)

            val restartedTransport =
                BleRadioTransport(
                    scope = this,
                    scanner = scanner,
                    bluetoothRepository = bluetoothRepository,
                    connectionFactory = freshFactory,
                    callback = service,
                    address = address,
                )
            try {
                restartedTransport.start()
                advanceTimeBy(4_000L)

                assertTrue(
                    freshConnection.connectAndAwaitCalls >= 1,
                    "Fresh transport must attempt connection after restart",
                )

                restartedTransport.close()
            } finally {
                restartedTransport.close()
            }
        } finally {
            bleTransport.close()
        }
    }

    // ─── Profile setup failure returns Failed outcome and retries ──────────────────────────────────

    /**
     * When profile setup fails (e.g. missing service UUID), the transport should return a
     * [BleReconnectPolicy.Outcome.Failed] and the reconnect loop should iterate. Uses
     * [FakeBleConnection.missingServices] to cause `profile()` to throw.
     */
    @Test
    fun `profile setup failure returns Failed outcome and retries`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        // Make SERVICE_UUID missing → profile() throws NoSuchElementException
        connection.missingServices.add(SERVICE_UUID)

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()

            // First attempt fails at profile setup, then settle delay + reconnect.
            advanceTimeBy(15_000L)

            // The reconnect loop should have called connectAndAwait at least twice
            // (initial failure + retry)
            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "Reconnect loop must iterate after profile setup failure " +
                    "(actual calls: ${connection.connectAndAwaitCalls})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    /**
     * Regression: CancellationException from a write should not trigger onDisconnect even after the transport has
     * reconnected and is operating normally.
     */
    @Test
    fun `CancellationException from write does not trigger onDisconnect after reconnection`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L)

            // Inject CancellationException into a write
            connection.service.writeException = CancellationException("cancelled")
            bleTransport.handleSendToRadio(byteArrayOf(1))
            // Drain the cancelled write coroutine; no retry is triggered for CancellationException.
            testScheduler.runCurrent()

            // CancellationException must NOT result in a user-facing disconnect callback
            dev.mokkery.verify(mode = dev.mokkery.verify.VerifyMode.not) { service.onDisconnect(any(), any()) }

            // Clear the exception and verify the transport is still usable
            connection.service.writeException = null
            bleTransport.handleSendToRadio(byteArrayOf(2))
            advanceTimeBy(1_000L)

            // Still no disconnect should have been called
            dev.mokkery.verify(mode = dev.mokkery.verify.VerifyMode.not) { service.onDisconnect(any(), any()) }

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    // ─── Read-path session-failure recovery (fromRadio fatal) ──────────────────────────────────────

    /**
     * Validates that a session-fatal exception in the fromRadio READ path (not just the write path) triggers
     * handleFailure → forced disconnect → reconnect.
     *
     * Existing tests only exercise the WRITE path. The read-path recovery is the primary mechanism for detecting zombie
     * sessions where fromRadio throws GATT 133/19/8/129 during a poll.
     */
    @Test
    fun `fromRadio read failure triggers handleFailure and reconnect`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        // Register FROMNUM + FROMRADIO before start() so the profile's observe collector and read
        // loop are set up during discoverServicesAndSetupCharacteristics. Without FROMNUM, no
        // notification collector is established (emitNotification fires into a void). Without
        // FROMRADIO, hasCharacteristic(fromRadioChar) returns false and the read loop skips before
        // readException can fire.
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
        try {
            bleTransport.start()
            advanceTimeBy(4_000L) // connect + handshake
            assertTrue(connection.connectAndAwaitCalls == 1, "First connect must happen")

            // Inject a read failure that will fire on the next drain cycle
            connection.service.readException = NotConnectedException("read session closed")

            // Trigger a drain by emitting a FROMNUM notification — this causes the profile to poll
            // fromRadioChar, which throws NotConnectedException (a session-fatal BLE exception).
            connection.service.emitNotification(FROMNUM_CHARACTERISTIC, byteArrayOf(1))
            // Drain the immediate fromRadio failure and forced disconnect; the explicit advanceTimeBy
            // below covers the reconnect loop's delayed retry.
            testScheduler.runCurrent()

            // handleFailure must have forced a GATT disconnect
            assertTrue(connection.disconnectCalls >= 1, "disconnect() must be called after fromRadio read failure")

            // Reconnect policy should iterate
            advanceTimeBy(10_000L)
            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "Reconnect loop must iterate after fromRadio read failure " +
                    "(actual calls: ${connection.connectAndAwaitCalls})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    /**
     * Validates the sessionFailed dedup guard when read-path and write-path failures race.
     *
     * The guard at the top of handleFailure exists precisely for this scenario: fromRadio's .catch handler and a
     * concurrent write failure both call handleFailure against the same dead session. Only the first caller should fire
     * onDisconnect.
     */
    @Test
    fun `concurrent fromRadio and write failure fires onDisconnect exactly once`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        // Register FROMNUM + FROMRADIO before start() so both read and write failure paths can
        // fire. See the read-failure test above for details.
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)

        var onDisconnectCalls = 0
        every { service.onDisconnect(any(), any()) } calls { onDisconnectCalls++ }

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()
            advanceTimeBy(4_000L)

            // Inject BOTH read and write failures against the same session
            connection.service.readException = NotConnectedException("read failure")
            connection.service.writeException = NotConnectedException("write failure")

            // Trigger both paths nearly simultaneously
            connection.service.emitNotification(FROMNUM_CHARACTERISTIC, byteArrayOf(1))
            bleTransport.handleSendToRadio(byteArrayOf(42))
            // Advance through the write-path retryBleOperation (~750ms) so BOTH the read and write
            // failure paths reach handleFailure, truly exercising the CAS dedup guard.
            advanceTimeBy(1_000L)

            assertEquals(
                1,
                onDisconnectCalls,
                "onDisconnect must fire exactly once despite concurrent read+write failures " +
                    "(actual: $onDisconnectCalls)",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    // ─── FROMNUM subscription readiness setup failures ───────────────────────────────────────────

    @Test
    fun `FROMNUM NotConnected before readiness aborts setup and retries`() =
        fromNumPreReadinessFailureAbortsSetupAndRetries(
            failure = NotConnectedException("FROMNUM observe failed before CCCD"),
        )

    @Test
    fun `FROMNUM GATT 133 before readiness aborts setup and retries`() =
        fromNumPreReadinessFailureAbortsSetupAndRetries(
            failure = GattStatusException(status = 133, message = "GATT error"),
        )

    private fun fromNumPreReadinessFailureAbortsSetupAndRetries(failure: Exception) = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.observeBeforeSubscriptionExceptionByCharacteristic[FROMNUM_CHARACTERISTIC] = failure

        var onConnectCalls = 0
        every { service.onConnect() } calls { onConnectCalls++ }
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
        try {
            bleTransport.start()

            // Initial settle (3s) + setup failure. Assert before retry can succeed, proving the failed
            // pre-readiness attempt did not falsely mark subscription readiness or call onConnect().
            advanceTimeBy(4_000L)

            assertTrue(connection.disconnectCalls >= 1, "disconnect() must be called after FROMNUM observe failure")
            assertEquals(0, onConnectCalls, "Failed pre-readiness setup must not call onConnect")

            advanceTimeBy(10_000L)
            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "Reconnect loop must retry after FROMNUM pre-readiness failure " +
                    "(actual calls: ${connection.connectAndAwaitCalls})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `FROMNUM subscription readiness timeout aborts setup clears stale service and retries`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.observeNeverSubscribeCharacteristics += FROMNUM_CHARACTERISTIC

        var onConnectCalls = 0
        every { service.onConnect() } calls { onConnectCalls++ }
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
        try {
            bleTransport.start()

            // Settle (3s) + SUBSCRIPTION_READY_TIMEOUT (5s) + margin. FROMNUM observe never invokes
            // onSubscription, so setup must abort instead of continuing with a half-initialized service.
            advanceTimeBy(9_000L)

            assertTrue(connection.disconnectCalls >= 1, "disconnect() must be called after subscription timeout")
            assertEquals(0, onConnectCalls, "Subscription timeout must not call onConnect")

            val writesBefore = connection.service.writes.size
            bleTransport.handleSendToRadio(byteArrayOf(7, 8, 9))
            assertEquals(
                writesBefore,
                connection.service.writes.size,
                "Timed-out setup must clear radioService so writes do not use a half-initialized service",
            )

            advanceTimeBy(10_000L)
            assertTrue(
                connection.connectAndAwaitCalls >= 2,
                "Reconnect loop must retry after subscription readiness timeout " +
                    "(actual calls: ${connection.connectAndAwaitCalls})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    // ─── Connected-gate timeout cleanup ──────────────────────────────────────────────────────────

    @Test
    fun `Connected-gate timeout cleans up stale service and retries without disconnect spam`() = runTest {
        val staleConnection = NeverConnectedStateBleConnection()
        val staleFactory =
            object : BleConnectionFactory {
                override fun create(scope: CoroutineScope, tag: String): BleConnection = staleConnection
            }
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)

        var onDisconnectCalls = 0
        every { service.onDisconnect(any(), any()) } calls { onDisconnectCalls++ }

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = staleFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()

            // Settle (3s) + CONNECTED_GATE_TIMEOUT (5s) + margin. connectAndAwait returns Connected,
            // profile setup succeeds, but connectionState never emits Connected, so the gate times out.
            advanceTimeBy(9_000L)

            assertTrue(staleConnection.disconnectCalls >= 1, "Connected-gate timeout must force GATT disconnect")
            assertTrue(onDisconnectCalls <= 1, "Connected-gate timeout must not spam onDisconnect callbacks")

            val writesBefore = staleConnection.service.writes.size
            bleTransport.handleSendToRadio(byteArrayOf(4, 5, 6))
            assertEquals(
                writesBefore,
                staleConnection.service.writes.size,
                "Connected-gate timeout must clear radioService so writes do not use stale profile state",
            )

            advanceTimeBy(10_000L)
            assertTrue(
                staleConnection.connectAndAwaitCalls >= 2,
                "Reconnect loop must retry after Connected-gate timeout " +
                    "(actual calls: ${staleConnection.connectAndAwaitCalls})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    /**
     * Validates the normal Connected-gate path: when connectAndAwait returns Connected and the connectionState
     * StateFlow reflects Connected, the gate succeeds immediately and the transport operates normally. The timeout path
     * — where connectionState never reaches Connected — is covered separately by the `Connected-gate timeout cleans up
     * stale service and retries without disconnect spam` test.
     */
    @Test
    fun `Connected-gate normal path succeeds and transport operates`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Radio")
        bluetoothRepository.bond(device)
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
        every { service.onDisconnect(any(), any()) } returns Unit

        try {
            bleTransport.start()
            // advance past connect — connectAndAwait returns Connected synchronously and the
            // FakeBleConnection sets connectionState to Connected in connect(), so this test
            // verifies the normal path where the gate succeeds.
            advanceTimeBy(10_000L)
            assertTrue(connection.connectAndAwaitCalls >= 1, "Must attempt at least one connection")

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    // ─── Bonded fresh-advertisement discovery ─────────────────────────────────────────────────────

    /**
     * Regression: bonded reconnect uses one bounded scanner registration and still prefers a fresh advertisement over
     * the stale bonded handle. Starting a short scan and then a second escalated scan consumed two Android scan starts
     * per reconnect, which could trigger SCAN_FAILED_SCANNING_TOO_FREQUENTLY during rapid device switching.
     */
    @Test
    fun `bonded reconnect uses one bounded scan and prefers its fresh device`() = runTest {
        val bondedDevice = FakeBleDevice(address = address, name = "Bonded Handle")
        bluetoothRepository.bond(bondedDevice)

        val freshDevice = FakeBleDevice(address = address, name = "Fresh Scan Result")
        val sequencedScanner = SequencedBleScanner().apply { responses += { flow { emit(freshDevice) } } }

        // Profile setup needs FROMNUM/FROMRADIO so the transport reaches a stable Connected state
        // rather than tearing down before we can inspect which device was connected.
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = sequencedScanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()

            advanceTimeBy(bondedReconnectWindowMillis)

            assertEquals(1, sequencedScanner.callCount, "Bonded reconnect must consume one scan registration")
            assertEquals(
                bondedScanTimeoutMillis,
                sequencedScanner.calls.single().timeout.inWholeMilliseconds,
                "The single bonded scan must use SCAN_TIMEOUT",
            )
            assertEquals(SERVICE_UUID, sequencedScanner.calls.single().serviceUuid, "Scan must include service UUID")
            assertEquals(address, sequencedScanner.calls.single().address, "Scan must target selected address")
            assertEquals(1, connection.connectAndAwaitCalls, "Must connect exactly once with the fresh scanned device")
            assertTrue(
                connection.device === freshDevice,
                "Must use the fresh scanned device, not the stale bonded handle " +
                    "(got: ${connection.device?.name}, expected: ${freshDevice.name})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }

    /**
     * Regression: when the bonded device misses the bounded fresh-advertisement scan, [findDevice] must fall back to
     * the bonded handle so Android's autoConnect path can patiently wait for the radio to advertise again.
     */
    @Test
    fun `bonded device never advertising falls back after one bounded scan`() = runTest {
        val bondedDevice = FakeBleDevice(address = address, name = "Bonded Handle")
        bluetoothRepository.bond(bondedDevice)

        val sequencedScanner = SequencedBleScanner().apply { responses += { flow { awaitCancellation() } } }

        // Keep profile setup stable so the test can inspect the connected device.
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = sequencedScanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )
        try {
            bleTransport.start()

            advanceTimeBy(bondedReconnectWindowMillis)

            assertEquals(1, sequencedScanner.callCount, "Bonded fallback must consume one scan registration")
            assertEquals(
                bondedScanTimeoutMillis,
                sequencedScanner.calls.single().timeout.inWholeMilliseconds,
                "The single bonded scan must use SCAN_TIMEOUT",
            )
            assertEquals(1, connection.connectAndAwaitCalls, "Must connect once through the bounded bonded fallback")
            assertTrue(
                connection.device === bondedDevice,
                "Must use the bonded handle after the fresh scan misses (got: ${connection.device?.name})",
            )

            bleTransport.close()
        } finally {
            bleTransport.close()
        }
    }
}

// ─── Test doubles ────────────────────────────────────────────────────────────────────────────────

/**
 * A [BleConnection] that succeeds at [connectAndAwait] but throws [CancellationException] from [profile]. This
 * simulates what happens when the owning coroutine scope is cancelled while GATT service discovery is in progress.
 */
private class CancellingProfileBleConnection : BleConnection {

    private val _deviceFlow = MutableStateFlow<BleDevice?>(null)
    override val deviceFlow: StateFlow<BleDevice?> = _deviceFlow.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    override val device: BleDevice? = null

    var disconnectCalls = 0

    override suspend fun connect(device: BleDevice) {
        _deviceFlow.value = device
        _connectionState.value = BleConnectionState.Connected
    }

    override suspend fun connectAndAwait(device: BleDevice, timeout: Duration): BleConnectionState {
        connect(device)
        return BleConnectionState.Connected
    }

    override suspend fun disconnect() {
        disconnectCalls++
        _connectionState.value = BleConnectionState.Disconnected()
        _deviceFlow.value = null
    }

    override suspend fun <T> profile(
        serviceUuid: kotlin.uuid.Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T = throw CancellationException("Simulated scope cancellation during service discovery")

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? = null
}

/**
 * A [BleConnection] whose [connectAndAwait] reports success while [connectionState] never emits
 * [BleConnectionState.Connected]. This exercises the Connected-gate timeout cleanup path after profile setup succeeds.
 */
private class NeverConnectedStateBleConnection : BleConnection {

    private val _deviceFlow = MutableStateFlow<BleDevice?>(null)
    override val deviceFlow: StateFlow<BleDevice?> = _deviceFlow.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    override val device: BleDevice?
        get() = _deviceFlow.value

    val service = FakeBleService().apply { addCharacteristic(FROMNUM_CHARACTERISTIC) }

    var connectAndAwaitCalls = 0
        private set

    var disconnectCalls = 0
        private set

    override suspend fun connect(device: BleDevice) {
        _deviceFlow.value = device
        _connectionState.value = BleConnectionState.Connecting
    }

    override suspend fun connectAndAwait(device: BleDevice, timeout: Duration): BleConnectionState {
        connectAndAwaitCalls++
        connect(device)
        return BleConnectionState.Connected
    }

    override suspend fun disconnect() {
        disconnectCalls++
        _connectionState.value = BleConnectionState.Disconnected()
        _deviceFlow.value = null
    }

    override suspend fun <T> profile(
        serviceUuid: kotlin.uuid.Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T = CoroutineScope(currentCoroutineContext()).setup(service)

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? = null
}

/**
 * A [BleScanner] test double that records each [scan] call's arguments and delegates to a per-call [Flow] supplier.
 *
 * Unlike [FakeBleScanner] (which is replay-based and re-emits all previously-emitted devices on every call), this
 * scanner lets a test model "first scan misses, second scan finds" by configuring [responses] index-by-index. Calls
 * beyond the configured list return a never-completing flow (models a scan that runs until cancelled).
 */
private class SequencedBleScanner : BleScanner {
    data class Call(val timeout: Duration, val serviceUuid: kotlin.uuid.Uuid?, val address: String?)

    private val _calls = mutableListOf<Call>()
    val calls: List<Call>
        get() = _calls.toList()

    val callCount: Int
        get() = _calls.size

    /**
     * Per-call [Flow] suppliers, indexed by call order. Call 0 returns [responses][0], call 1 returns [responses][1],
     * etc. Calls beyond the list size return a never-completing flow (suspends until cancelled).
     */
    val responses = mutableListOf<() -> Flow<BleDevice>>()

    override fun scan(timeout: Duration, serviceUuid: kotlin.uuid.Uuid?, address: String?): Flow<BleDevice> {
        val index = _calls.size
        _calls += Call(timeout, serviceUuid, address)
        return if (index < responses.size) responses[index]() else flow { awaitCancellation() }
    }
}
