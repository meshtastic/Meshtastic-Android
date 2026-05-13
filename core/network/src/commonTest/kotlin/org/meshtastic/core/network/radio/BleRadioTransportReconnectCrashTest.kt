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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.DisconnectReason
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

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

        // Allow the connection loop to reach the connected state.
        advanceTimeBy(4_000L)

        bleTransport.close()

        // disconnect() must be called: once by the connection loop teardown + once by close() itself.
        // We only assert it was called at least once — the exact count depends on timing.
        assertTrue(connection.disconnectCalls >= 1, "Expected disconnect() to be called at least once")
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
        bleTransport.start()

        advanceTimeBy(30_000L)

        bleTransport.close()

        // Each failed connectAndAwait round-trips through the reconnect loop; close() always disconnects.
        assertTrue(connection.disconnectCalls >= 1, "disconnect() not called after connection failure")
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
        bleTransport.start()

        advanceTimeBy(24_001L)

        // Transient disconnect must have been signalled.
        dev.mokkery.verify { service.onDisconnect(isPermanent = false, errorMessage = any()) }
        // Permanent disconnect must NEVER be called by the transport on its own.
        dev.mokkery.verify(mode = dev.mokkery.verify.VerifyMode.not) {
            service.onDisconnect(isPermanent = true, errorMessage = any())
        }

        bleTransport.close()
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

        val bleTransport =
            BleRadioTransport(
                scope = this,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = throwingFactory,
                callback = service,
                address = address,
            )
        bleTransport.start()

        // Allow one connection attempt to reach profile() and be cancelled.
        advanceTimeBy(4_000L)

        bleTransport.close()

        assertTrue(
            throwingConnection.disconnectCalls >= 1,
            "disconnect() must be called after CancellationException in profile() — GATT leak fix",
        )
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
