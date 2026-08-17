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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.ble.DisconnectReason
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
import kotlin.time.Duration.Companion.seconds

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

    @Test
    fun `send is rejected before the BLE profile is available`() = runTest {
        val bleTransport =
            BleRadioTransport(
                scope = backgroundScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                callback = service,
                address = address,
            )

        try {
            assertFalse(bleTransport.handleSendToRadio(byteArrayOf(1, 2, 3)))
        } finally {
            bleTransport.close()
        }
    }

    @Test
    fun `close drains every write admitted by the active BLE profile generation`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val secondWriteStarted = CompletableDeferred<Unit>()
        val releaseSecondWrite = CompletableDeferred<Unit>()
        val firstPayload = byteArrayOf(1, 2, 3)
        val secondPayload = byteArrayOf(4, 5, 6)
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
            connection.service.beforeWrite = { _, data ->
                when {
                    data.contentEquals(firstPayload) -> {
                        firstWriteStarted.complete(Unit)
                        releaseFirstWrite.await()
                    }

                    data.contentEquals(secondPayload) -> {
                        secondWriteStarted.complete(Unit)
                        releaseSecondWrite.await()
                    }
                }
            }

            assertTrue(bleTransport.handleSendToRadio(firstPayload))
            runCurrent()
            withTimeout(5.seconds) { firstWriteStarted.await() }
            assertTrue(bleTransport.handleSendToRadio(secondPayload))
            runCurrent()

            val closeJob = async { bleTransport.close() }
            runCurrent()
            assertFalse(closeJob.isCompleted, "close must drain admitted BLE writes before GATT teardown")
            assertEquals(0, connection.disconnectCalls, "GATT teardown must not overtake an admitted write")

            releaseFirstWrite.complete(Unit)
            runCurrent()
            withTimeout(5.seconds) { secondWriteStarted.await() }
            assertEquals(0, connection.disconnectCalls, "queued admitted work must run before GATT teardown")
            assertFalse(closeJob.isCompleted, "close must wait until the second admitted write finishes")
            releaseSecondWrite.complete(Unit)
            withTimeout(5.seconds) { closeJob.await() }
            assertEquals(1, connection.disconnectCalls)
        } finally {
            releaseFirstWrite.complete(Unit)
            releaseSecondWrite.complete(Unit)
            connection.service.beforeWrite = null
            bleTransport.close()
        }
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
            // 3s settle + connectAndAwait, then POST_INVALIDATION_RECONNECT_DELAY must fully elapse before the
            // forced reconnect fires. Checking at 4.5s — safely past where a regressed 500 ms delay would have
            // already fired (3s settle + 500ms = 3.5s) but safely short of the real 3s delay firing (3s settle +
            // 3s = 6s) — pins the delay's actual value: a regression back to the old 500 ms would already show
            // the second connectAndAwait call by this point, which connectAndAwaitCalls == 1 below would catch.
            advanceTimeBy(4_500)
            assertEquals(
                1,
                connection.connectAndAwaitCalls,
                "the forced reconnect must wait for the full post-invalidation settle delay, not fire early",
            )

            // Clears the remaining settle delay, the reconnect itself, and profile setup (CONNECTED_GATE_TIMEOUT /
            // SUBSCRIPTION_READY_TIMEOUT, 5s each) — 15.5s more, matching the original 20s total budget.
            advanceTimeBy(15_500)

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

    private fun bleTransportOn(scope: CoroutineScope, callback: RadioInterfaceService): BleRadioTransport =
        BleRadioTransport(
            scope = scope,
            scanner = scanner,
            bluetoothRepository = bluetoothRepository,
            connectionFactory = connectionFactory,
            callback = callback,
            address = address,
        )

    /**
     * Simulates the platform replaying a stale GATT service table: `profile()` cannot find the Meshtastic service, and
     * the service only reappears once the platform has reported a *successful* cache refresh.
     *
     * Keyed on [FakeBleConnection.invalidateServiceCacheResult] as well as the call count so that a platform which
     * cannot refresh at all (Android reflection miss → `false`) never recovers, which is what makes the recovery test
     * below non-vacuous.
     */
    private fun FakeBleConnection.simulateStaleServiceTable() {
        missingServices.add(SERVICE_UUID)
        onDisconnect = {
            if (invalidateServiceCacheResult && invalidateServiceCacheCalls > 0) missingServices.remove(SERVICE_UUID)
        }
    }

    /**
     * Issue #6685: a bonded radio that was out of range or powered off for a long time can come back with Android still
     * serving a stale cached GATT service table, so every reconnect fails and [BleReconnectPolicy] (maxFailures =
     * [Int.MAX_VALUE]) retries forever. Once the failure streak reaches
     * [GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD], the next attempt that reaches a connected link must
     * refresh the cache and reconnect so discovery re-reads the device.
     *
     * Timeline in virtual time (3 s settle before every attempt, backoff 5/10/20/40/60/60 s after failures 1–6):
     * attempts fail at t = 3, 11, 24, 47, 90 and 153 s; the seventh attempt starts at t = 216 s (3 min 36 s) and
     * connects. That the refresh is *not* reachable any earlier is the whole point — see the sub-threshold test below.
     */
    @Test
    fun `stale GATT cache is refreshed after the reconnect failure streak reaches the threshold`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)
        connection.invalidateServiceCacheResult = true
        connection.failNextN = GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD

        val bleTransport = bleTransportOn(this, FakeRadioInterfaceService())
        bleTransport.start()
        try {
            advanceTimeBy(240_000)

            assertEquals(
                1,
                connection.invalidateServiceCacheCalls,
                "the cache must be refreshed exactly once for the streak, not on every retry",
            )
            assertTrue(
                connection.connectAndAwaitCalls >= GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD + 2,
                "expected the failed attempts, the connected attempt, and the post-refresh reconnect " +
                    "(got ${connection.connectAndAwaitCalls})",
            )
        } finally {
            bleTransport.close()
        }
    }

    /**
     * Discriminator for the test above, and the regression this guards: an ordinary out-of-range gap must cost no cache
     * refresh and no extra disconnect/reconnect round trip. With the threshold at the policy's transient-disconnect
     * value (3 failures ≈ 47 s) this test fails, because a completely ordinary absence would be treated as a stale
     * cache and the reconnect that was about to succeed would be torn down instead.
     *
     * Timeline: attempts fail at t = 3, 11, 24, 47 and 90 s; the sixth attempt starts at t = 153 s and connects with
     * one failure fewer than the threshold recorded.
     */
    @Test
    fun `a reconnect failure streak below the threshold never refreshes the GATT cache`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)
        connection.invalidateServiceCacheResult = true
        connection.failNextN = GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD - 1

        val bleTransport = bleTransportOn(this, FakeRadioInterfaceService())
        bleTransport.start()
        try {
            advanceTimeBy(180_000)

            assertTrue(
                connection.connectAndAwaitCalls >= GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD,
                "the connection must have recovered after the sub-threshold failures " +
                    "(got ${connection.connectAndAwaitCalls} attempts)",
            )
            assertEquals(
                0,
                connection.invalidateServiceCacheCalls,
                "an ordinary out-of-range gap must not throw away a valid GATT cache",
            )
        } finally {
            bleTransport.close()
        }
    }

    /**
     * The one-refresh-per-streak allowance must be re-armed when a streak ends, or the second time a radio disappears
     * the recovery is silently unavailable and issue #6685 returns.
     *
     * This is the case that cannot be inferred from `BleReconnectPolicy.consecutiveFailures`: the counter is reset only
     * *after* the attempt that owned the stable session returns, and the first attempt of the next streak fails before
     * it ever reaches the point where the transport reads it. Only an explicit end-of-streak signal from the disconnect
     * path re-arms the gate.
     *
     * The streak is ended here with [DisconnectReason.LocalDisconnect] rather than by letting the session age past
     * `minStableConnection`: connection uptime is measured with `nowMillis` (the wall clock), which `advanceTimeBy`
     * does not move, so the `wasStable` branch is unreachable from a virtual-time test. Both branches feed the same
     * re-arm call, so the wiring under test is the same.
     */
    @Test
    fun `a second failure streak after the previous one ended earns its own cache refresh`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)
        connection.invalidateServiceCacheResult = true
        connection.failNextN = GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD

        val bleTransport = bleTransportOn(this, FakeRadioInterfaceService())
        bleTransport.start()
        try {
            // First streak: threshold failures, then the next attempt connects and refreshes the cache at ~t = 216 s.
            advanceTimeBy(240_000)
            assertEquals(1, connection.invalidateServiceCacheCalls, "the first streak must refresh the cache once")

            // End the streak with a disconnect the policy treats as non-failing, then arm a second streak behind it.
            connection.failNextN = GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD
            connection.simulateRemoteDisconnect(DisconnectReason.LocalDisconnect)

            advanceTimeBy(240_000)
            assertEquals(
                2,
                connection.invalidateServiceCacheCalls,
                "a streak that follows an ended streak must earn its own cache refresh",
            )
        } finally {
            bleTransport.close()
        }
    }

    /**
     * A post-OTA refresh must not spend the failure streak's single allowance.
     *
     * The two triggers are independent: the post-OTA refresh is scheduled by the firmware-update flow, not by any
     * failure streak. If it charged the streak, a radio that came back from an OTA into an unstable streak would have
     * its stale-cache recovery disabled for that whole streak — the gate is only re-armed by a stable or intentional
     * disconnect, which by definition cannot happen while a streak is running.
     *
     * Here the post-OTA refresh fires on the very first attempt (zero consecutive failures), then an unstable
     * disconnect starts a streak that runs up to the threshold. The second refresh proves the allowance survived.
     */
    @Test
    fun `a post-OTA cache refresh does not consume the stale-cache allowance`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)
        connection.invalidateServiceCacheResult = true

        val radioService = FakeRadioInterfaceService()
        radioService.requestGattCacheInvalidationOnNextConnect()

        val bleTransport = bleTransportOn(this, radioService)
        bleTransport.start()
        try {
            advanceTimeBy(20_000)
            assertEquals(1, connection.invalidateServiceCacheCalls, "the post-OTA flag must refresh the cache once")

            // An unstable (non-intentional, sub-minStableConnection) disconnect starts a streak, so the gate is never
            // re-armed between the post-OTA refresh and the stale-cache refresh under test.
            connection.failNextN = GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD - 1
            connection.simulateRemoteDisconnect(DisconnectReason.Timeout)

            advanceTimeBy(300_000)
            assertEquals(
                2,
                connection.invalidateServiceCacheCalls,
                "the streak that followed the OTA must still earn its own stale-cache refresh",
            )
        } finally {
            bleTransport.close()
        }
    }

    /**
     * The recovery this whole mechanism exists for: connects succeed, but service discovery keeps replaying a cached
     * table without the Meshtastic service, so every attempt fails at profile setup. Once the streak reaches the
     * threshold the cache is refreshed, and *because of that refresh* the following attempt finds the service and the
     * reconnect loop settles.
     *
     * Unlike a connect-time failure this exercises the actual issue #6685 shape — the device is reachable, the cached
     * service table is not. The fake only makes the service reappear after a successful `invalidateServiceCache()`, so
     * a passing run cannot be explained by anything other than the invalidation; the companion test below pins that
     * down with the platform refusing to refresh.
     *
     * Timings are not asserted: attempts here fail after connecting, so each one also pays session-cleanup waits. The
     * assertions are on production-observable behavior only — one refresh call, and a loop that stopped retrying
     * afterward — not on the fake's own `missingServices` bookkeeping, since that store is mutated by the fake's own
     * [FakeBleConnection.simulateStaleServiceTable] callback and would just be testing the fake against itself. The
     * negative-control test below is what actually proves recovery is caused by the invalidation.
     */
    @Test
    fun `a stale service table recovers on the attempt that follows the cache refresh`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)
        connection.invalidateServiceCacheResult = true
        connection.simulateStaleServiceTable()

        val bleTransport = bleTransportOn(this, FakeRadioInterfaceService())
        bleTransport.start()
        try {
            advanceTimeBy(1_200_000)

            assertEquals(
                1,
                connection.invalidateServiceCacheCalls,
                "the streak must earn exactly one refresh, and that refresh must have been enough",
            )

            val callsAfterRecovery = connection.connectAndAwaitCalls
            advanceTimeBy(300_000)
            assertEquals(
                callsAfterRecovery,
                connection.connectAndAwaitCalls,
                "the loop must have settled on a working connection instead of continuing to retry",
            )
        } finally {
            bleTransport.close()
        }
    }

    /**
     * Negative control for the recovery test: with the platform unable to refresh (`invalidateServiceCache()` returning
     * `false`, e.g. the Android reflection hop missing), the stale service table is never repaired and the loop keeps
     * retrying. This is what proves the recovery above is caused by the invalidation rather than by the retry loop
     * eventually getting lucky.
     *
     * A refresh that never happened must also not burn the streak's allowance, so the gate keeps asking on every
     * subsequent attempt — hence the "more than once" assertion here versus "exactly once" above.
     */
    @Test
    fun `a stale service table never recovers when the platform cannot refresh the cache`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        bluetoothRepository.bond(device)
        scanner.emitDevice(device)
        connection.service.addCharacteristic(FROMNUM_CHARACTERISTIC)
        connection.service.addCharacteristic(FROMRADIO_CHARACTERISTIC)
        connection.invalidateServiceCacheResult = false
        connection.simulateStaleServiceTable()

        val bleTransport = bleTransportOn(this, FakeRadioInterfaceService())
        bleTransport.start()
        try {
            advanceTimeBy(1_200_000)

            assertTrue(
                connection.invalidateServiceCacheCalls > 1,
                "a refresh that never happened must not burn the allowance, so the gate must keep asking " +
                    "(got ${connection.invalidateServiceCacheCalls} calls)",
            )
            assertTrue(
                SERVICE_UUID in connection.missingServices,
                "without a successful refresh the stale service table must persist",
            )

            val callsSoFar = connection.connectAndAwaitCalls
            advanceTimeBy(300_000)
            assertTrue(
                connection.connectAndAwaitCalls > callsSoFar,
                "the reconnect loop must still be retrying (stuck at $callsSoFar attempts)",
            )
        } finally {
            bleTransport.close()
        }
    }
}
