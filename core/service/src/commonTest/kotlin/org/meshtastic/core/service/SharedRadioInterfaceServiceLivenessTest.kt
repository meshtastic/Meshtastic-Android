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
package org.meshtastic.core.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeRadioTransport
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Service-level tests for [SharedRadioInterfaceService] liveness detection.
 *
 * Uses a controllable clock via [SharedRadioInterfaceService.clockMillis] so [onConnect], [handleFromRadio], and
 * [checkLiveness] all share one coherent time source — no mixing of real wall-clock with test time.
 *
 * A counting transport factory returns a fresh [FakeRadioTransport] per createTransport() call so we can observe how
 * many restarts actually occurred.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedRadioInterfaceServiceLivenessTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    private lateinit var processLifecycleOwner: TestLifecycleOwner

    @BeforeTest
    fun setUp() {
        // processLifecycle.coroutineScope uses Dispatchers.Main.immediate internally;
        // JVM tests must install a Main dispatcher or get IllegalStateException.
        Dispatchers.setMain(testDispatcher)
        // Create the lifecycle owner AFTER setMain so Robolectric's main thread is ready.
        // Field initializers run before @BeforeTest, which is too early for Robolectric.
        processLifecycleOwner = TestLifecycleOwner()
        // USB tests leave serialDeviceKeys non-empty; reset before each test so non-USB
        // tests start from the documented empty default.
        serialDeviceKeys.value = emptySet()
    }

    @AfterTest
    fun tearDown() {
        // Release any suspended close gate so a held in-flight restart can complete; otherwise
        // disconnect() below would block forever on the gated transport's close().
        // NOTE: relies on UnconfinedTestDispatcher resuming the gated close() inline when
        // complete(Unit) is called — if testDispatcher is ever changed to StandardTestDispatcher,
        // add testDispatcher.scheduler.runCurrent() here before runBlocking to avoid a mutex deadlock.
        activeCloseGate?.complete(Unit)
        activeCloseGate = null
        // Service cleanup is handled per-test in try/finally blocks — each test calls
        // service.disconnect() + advanceTimeBy in a finally clause. tearDown cannot use
        // runBlocking { services.forEach { it.disconnect() } } because it deadlocks on
        // Robolectric's main thread (androidHostTest target).
        services.clear()
        createdTransports.clear()
        // CRITICAL: Destroy the lifecycle to cancel processLifecycle.coroutineScope and all
        // leaked collectors (devAddr, bluetoothRepository.state, networkRepository.networkAvailable).
        // Without this, those infinite flow collectors keep the forked test JVM alive after tests
        // complete, causing Gradle to hang at subsequent :core:*:allTests tasks.
        processLifecycleOwner.destroy()
        // Let pending cancellations propagate before resetting the Main dispatcher. Use runCurrent
        // (NOT advanceUntilIdle): the test bodies already disconnect every service, so no heartbeat
        // loop should be active here — advanceUntilIdle would hang if one somehow survived.
        testDispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private val bluetoothRepository = FakeBluetoothRepository()
    private val radioPrefs = FakeRadioPrefs()

    /**
     * Controllable backing flow for [serialDevicePresence]. Defaults to the empty set so liveness/gate-regression tests
     * (which exercise BLE/TCP paths only) leave the USB recovery observer inert. USB replug tests drive this flow
     * directly to exercise each branch of the observer's gate contract.
     */
    private val serialDeviceKeys = MutableStateFlow<Set<String>>(emptySet())

    /** [SerialDevicePresence] backed by [serialDeviceKeys] so tests can publish device-key sets on demand. */
    private val serialDevicePresence: SerialDevicePresence =
        object : SerialDevicePresence {
            override val deviceKeys: StateFlow<Set<String>> = serialDeviceKeys.asStateFlow()
        }

    private val networkRepository: NetworkRepository = mock(MockMode.autofill)
    private val analytics: PlatformAnalytics = mock(MockMode.autofill)

    /**
     * Minimal [LifecycleOwner] for tests that avoids [LifecycleRegistry], which enforces main-thread checks and throws
     * `RuntimeException` under Robolectric (androidHostTest). This custom [Lifecycle] dispatches ON_DESTROY to
     * registered [LifecycleEventObserver]s so `lifecycleScope` cancels correctly.
     */
    private class TestLifecycleOwner : LifecycleOwner {
        private val observers = mutableListOf<LifecycleObserver>()
        private var state = Lifecycle.State.RESUMED

        override val lifecycle: Lifecycle =
            object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    observers.add(observer)
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    observers.remove(observer)
                }

                override val currentState: Lifecycle.State
                    get() = state
            }

        fun destroy() {
            state = Lifecycle.State.DESTROYED
            val event = Lifecycle.Event.ON_DESTROY
            observers.toList().forEach { observer ->
                (observer as? LifecycleEventObserver)?.onStateChanged(this@TestLifecycleOwner, event)
            }
        }
    }

    /**
     * Test-only [RadioTransport] whose [close] suspends on a [CompletableDeferred] gate.
     *
     * The liveness restart path calls `stopTransportLocked` → `currentTransport.close()` inside a launched coroutine.
     * With the default [FakeRadioTransport], `close()` returns without suspending, so under [UnconfinedTestDispatcher]
     * the entire restart completes synchronously during `checkLiveness()` and a second `checkLiveness()` never observes
     * an in-flight restart. By awaiting a gate inside `close()`, this fake holds the restart genuinely suspended
     * mid-flight, letting a test deterministically exercise the in-flight overlap window and prove the second check
     * does not stack another restart/close.
     *
     * The gate is shared across instances; once completed by the test, any pending or subsequent `close()` resumes
     * immediately.
     */
    private class GatedFakeRadioTransport(private val closeGate: CompletableDeferred<Unit>) : RadioTransport {
        var closeCalled = false
            private set

        var closeCount = 0
            private set

        var closeCompletedCount = 0
            private set

        // Liveness restart skips the polite-disconnect frame (sendPoliteDisconnect = false), so no
        // outbound data is expected; satisfy the contract with a no-op.
        override fun handleSendToRadio(p: ByteArray) = Unit

        override suspend fun close() {
            closeCalled = true
            closeCount++
            // Suspend here until the test releases the gate, holding the restart in-flight.
            closeGate.await()
            closeCompletedCount++
        }
    }

    /** Controllable clock — tests advance this manually so all time comparisons are deterministic. */
    private var clock: Long = 0L

    /**
     * Tracks every [SharedRadioInterfaceService] created via [createConnectedService] so [tearDown] can disconnect them
     * deterministically. Destroying the process lifecycle does NOT cancel the service's private `_serviceScope` (which
     * hosts the heartbeat loop), so we must call `disconnect()` explicitly.
     */
    private val services = mutableListOf<SharedRadioInterfaceService>()

    /**
     * Tracks the suspended close gate for the in-flight restart test so [tearDown] can release it even if the test body
     * throws. Without this, a failed assertion before `closeGate.complete(Unit)` would leave a restart suspended
     * forever and hang teardown.
     */
    private var activeCloseGate: CompletableDeferred<Unit>? = null

    /** Tracks all transports created by the factory so we can count restarts and inspect sent data. */
    private val createdTransports = mutableListOf<FakeRadioTransport>()
    private val transportFactory: RadioTransportFactory = mock(MockMode.autofill)

    /**
     * Creates a [SharedRadioInterfaceService] with a controllable clock and a factory that returns a fresh
     * [FakeRadioTransport] per createTransport() call. After construction, calls [connect] then explicitly [onConnect]
     * to bring the service to Connected state (FakeRadioTransport does not call onConnect itself).
     *
     * Pass [transportProvider] to swap in a custom test double (e.g. a suspending-close fake) instead of the default
     * [FakeRadioTransport]; the default records each created transport in [createdTransports].
     */
    private fun createConnectedService(
        address: String,
        transportProvider: () -> RadioTransport = { FakeRadioTransport().also { createdTransports.add(it) } },
        networkAvailability: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): SharedRadioInterfaceService {
        every { networkRepository.networkAvailable } returns networkAvailability
        every { networkRepository.resolvedList } returns MutableSharedFlow()
        every { analytics.isPlatformServicesAvailable } returns false
        every { transportFactory.supportedDeviceTypes } returns listOf(DeviceType.BLE)
        every { transportFactory.isMockTransport() } returns false
        every { transportFactory.isAddressValid(any()) } returns true
        every { transportFactory.toInterfaceAddress(any(), any()) } returns address
        every { transportFactory.createTransport(any(), any()) } calls { transportProvider() }

        radioPrefs.setDevAddr(address)

        val service =
            SharedRadioInterfaceService(
                dispatchers = dispatchers,
                bluetoothRepository = bluetoothRepository,
                networkRepository = networkRepository,
                serialDevicePresence = serialDevicePresence,
                processLifecycle = processLifecycleOwner.lifecycle,
                radioPrefs = radioPrefs,
                transportFactory = transportFactory,
                analytics = analytics,
            )
        service.clockMillis = { clock }
        // Register the service so tearDown can disconnect it deterministically (the heartbeat loop
        // launched in _serviceScope would otherwise outlive the test).
        services.add(service)
        service.connect()
        service.onConnect()
        return service
    }

    // ─── BLE: Liveness timeout triggers recovery ───────────────────────────────────────────────

    @Test
    fun `BLE liveness timeout closes old transport and creates fresh one`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            clock = 65_000L
            service.checkLiveness()
            // Under UnconfinedTestDispatcher the liveness restart (sendPoliteDisconnect = false) runs
            // inline during checkLiveness(). runCurrent/advanceTimeBy are belt-and-suspenders; the
            // real 500ms polite-disconnect delay is covered by the trailing service.disconnect() below.
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(2, createdTransports.size, "Liveness restart should create exactly one fresh transport")
            assertTrue(createdTransports.first().closeCalled, "Old transport must be closed")
            assertEquals(1, createdTransports.first().closeCount, "Old transport closed exactly once")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE liveness restart does not emit permanent Disconnected`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")

        try {
            // Capture all state transitions during the liveness recovery
            val stateEmissions = mutableListOf<ConnectionState>()
            val collectJob = backgroundScope.launch { service.connectionState.collect { stateEmissions.add(it) } }

            clock = 65_000L
            service.checkLiveness()
            // The restart completes inline under UnconfinedTestDispatcher; runCurrent/advanceTimeBy
            // are belt-and-suspenders. The trailing disconnect() covers its own 500ms polite delay.
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            collectJob.cancel()

            // Recovery must NEVER emit permanent Disconnected
            assertFalse(
                ConnectionState.Disconnected in stateEmissions,
                "Automatic recovery must not emit permanent Disconnected state " + "(emitted: $stateEmissions)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE liveness restart does not emit user-facing connection error`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")

        try {
            // Collect connectionError emissions — automatic liveness recovery must be silent.
            // _connectionError is a no-replay SharedFlow, so the collector must subscribe before
            // triggering the liveness timeout. Under UnconfinedTestDispatcher the launch runs
            // eagerly to its first suspension (awaiting SharedFlow emission).
            val errors = mutableListOf<String>()
            val collectJob = backgroundScope.launch { service.connectionError.collect { errors.add(it) } }

            clock = 65_000L
            service.checkLiveness()
            // The restart completes inline under UnconfinedTestDispatcher; runCurrent/advanceTimeBy
            // are belt-and-suspenders (mirrors the sibling liveness tests' pattern).
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            collectJob.cancel()

            assertTrue(
                errors.isEmpty(),
                "Automatic BLE liveness recovery must not emit user-facing connection error (got: $errors)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE liveness restart does not send polite disconnect into zombie transport`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            val oldTransport = createdTransports.first()

            oldTransport.sentData.clear()

            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertTrue(
                oldTransport.sentData.isEmpty(),
                "Polite disconnect frame must NOT be sent into zombie transport during liveness restart",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE repeated liveness checks do not stack restarts`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")

        try {
            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            clock = 66_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            val firstTransportCloses = createdTransports.firstOrNull()?.closeCount ?: 0
            assertEquals(1, firstTransportCloses, "First transport should be closed exactly once (no stacking)")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE in-flight liveness restart prevents overlapping restart via isRestarting`() = runTest(testDispatcher) {
        // Deterministic in-flight overlap: a GatedFakeRadioTransport holds the first restart
        // genuinely suspended inside stopTransportLocked → close() (awaiting closeGate). This
        // removes reliance on UnconfinedTestDispatcher scheduling so the overlap window is real.
        //
        // The first checkLiveness() flips state to DeviceSleep and CAS-sets isRestarting before
        // launching the restart coroutine, which then suspends in close(). The second
        // checkLiveness() is issued while that restart is still suspended and must NOT begin
        // another close/create cycle.
        val gatedTransports = mutableListOf<GatedFakeRadioTransport>()
        val closeGate = CompletableDeferred<Unit>()
        // Publish the gate to activeCloseGate so tearDown can release it even if an assertion below
        // throws before we reach the try/finally — otherwise disconnect() would hang on close().
        activeCloseGate = closeGate
        val transportProvider: () -> RadioTransport = {
            GatedFakeRadioTransport(closeGate).also { gatedTransports.add(it) }
        }

        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF", transportProvider)
        try {
            assertEquals(1, gatedTransports.size, "Initial connect should create one transport")
            val initialTransport = gatedTransports.first()

            // Past the 60s threshold → first checkLiveness triggers a restart whose close() suspends
            // on closeGate. Under UnconfinedTestDispatcher the launched restart runs eagerly up to the
            // suspension point, so by the time checkLiveness() returns the restart is in-flight.
            clock = 65_000L
            service.checkLiveness()

            // Issue a second checkLiveness() while the first restart is still suspended in close().
            // Do NOT advance time here — the overlap must happen with the first restart in-flight.
            clock = 65_001L
            service.checkLiveness()

            // Assertions below run while the restart is held suspended on closeGate. They MUST be
            // wrapped in try/finally so closeGate is completed even if one of them fails; otherwise
            // tearDown's runBlocking { disconnect() } would hang forever on the gated close().
            try {
                // While the first restart is suspended: exactly one transport created so far, and close()
                // was entered exactly once and has NOT completed. The second check started no new cycle.
                assertEquals(
                    1,
                    gatedTransports.size,
                    "Second check must not create a transport while the first restart is in-flight",
                )
                assertTrue(
                    initialTransport.closeCalled,
                    "First transport close must have been entered by the restart",
                )
                assertEquals(
                    1,
                    initialTransport.closeCount,
                    "close() entered exactly once (no stacking of close calls)",
                )
                assertEquals(
                    0,
                    initialTransport.closeCompletedCount,
                    "close() must still be suspended (restart held in-flight) before releasing the gate",
                )
            } finally {
                // Release the gate unconditionally so the suspended restart can complete. tearDown
                // also releases activeCloseGate, but completing it here is required for the post-finally
                // assertions below to observe the resumed restart.
                closeGate.complete(Unit)
            }

            // Release the gate: the suspended restart resumes, completes stopTransportLocked (whose
            // polite-disconnect delay is 500ms — covered by the 1s below), and startTransportLocked
            // creates the single fresh transport. isRestarting is reset in the finally block.
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            // Exactly 2 transports: 1 initial + 1 restart. A stacking bug would produce 3+.
            assertEquals(
                2,
                gatedTransports.size,
                "Exactly one fresh transport created after the restart resumes (1 initial + 1 restart)",
            )
            assertEquals(
                1,
                initialTransport.closeCount,
                "First transport still closed exactly once after restart completes",
            )
            assertEquals(1, initialTransport.closeCompletedCount, "First transport close completed exactly once")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    // ─── Non-BLE: Liveness does not mutate state ───────────────────────────────────────────────

    @Test
    fun `non-BLE transport liveness timeout does not close transport or change state`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("t192.168.1.100")
        try {
            val stateBefore = service.connectionState.value

            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(stateBefore, service.connectionState.value, "Non-BLE state must not change")
            assertFalse(createdTransports.first().closeCalled, "Non-BLE transport must NOT be closed")
            assertEquals(1, createdTransports.size, "No restart should occur for non-BLE transport")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    // ─── handleFromRadio resets the liveness timer ──────────────────────────────────────────────

    @Test
    fun `inbound data resets liveness timer so timeout does not fire`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")

        try {
            // Advance 30s, then receive data (resets lastDataReceivedMillis to clock=30s)
            clock = 30_000L
            service.handleFromRadio(byteArrayOf(1, 2, 3))

            // 30s since last data → within 60s threshold → should NOT fire
            clock = 60_000L
            service.checkLiveness()
            assertFalse(
                createdTransports.first().closeCalled,
                "Liveness must not fire when silence is within threshold after inbound data",
            )

            // 66s since last data (at t=30s) → past 60s threshold → should fire
            clock = 96_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            assertTrue(
                createdTransports.first().closeCalled,
                "Liveness should fire after silence exceeds threshold since last inbound data",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `USB permission denial emits error and permanent disconnected state`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("s/dev/bus/usb/001/002")
        try {
            val errors = mutableListOf<String>()
            val collectJob = backgroundScope.launch { service.connectionError.collect { errors.add(it) } }

            service.onDisconnect(
                isPermanent = true,
                errorMessage = null,
                reason = TransportDisconnectReason.UsbPermissionDenied,
            )
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            collectJob.cancel()

            assertEquals(ConnectionState.Disconnected, service.connectionState.value)
            assertEquals(
                listOf("USB permission denied. Reconnect the device to try again."),
                errors,
                "USB permission denial must surface a specific error message.",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE liveness does not fire when connection state is not Connected`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")

        try {
            service.onDisconnect(isPermanent = true)
            assertFalse(service.connectionState.value == ConnectionState.Connected)

            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            assertFalse(createdTransports.first().closeCalled, "Liveness must not fire when not Connected")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    // ─── connectionRequested gate: environmental recovery vs explicit disconnect ────────────

    /**
     * Regression: after an explicit [SharedRadioInterfaceService.disconnect], BT state emissions MUST NOT restart the
     * transport. Without the `connectionRequested` gate, a subsequent `bluetoothRepository.state = enabled` emission
     * would silently resurrect the transport the user tore down — leaving the app "connected" with no orchestrator
     * collector, no NodeDB load, and no channels. Only [disconnect] clears the gate; the state listener checks it
     * before calling `startTransportLocked()`.
     */
    @Test
    fun `BLE state recovery does not restart transport after explicit disconnect`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            // Explicit user-initiated disconnect: clears the connectionRequested gate BEFORE
            // stopTransportLocked() so a racing state-listener emission cannot re-arm the transport.
            service.disconnect()
            // Drain the polite-disconnect frame (production waits POLITE_DISCONNECT_DRAIN_MS = 500ms).
            advanceTimeBy(1_000L)

            val transportCountAfterDisconnect = createdTransports.size

            // Force a fresh BLE state cycle (disabled → enabled). The initial listener subscription
            // already consumed the default enabled=true emission during connect(), so toggling is
            // required to deliver a NEW enabled=true emission that would trigger startTransportLocked().
            bluetoothRepository.setBluetoothEnabled(false)
            testDispatcher.scheduler.runCurrent()
            bluetoothRepository.setBluetoothEnabled(true)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                transportCountAfterDisconnect,
                createdTransports.size,
                "BT-enabled emission after disconnect must NOT restart transport (connectionRequested gate)",
            )
            assertFalse(
                service.connectionState.value == ConnectionState.Connected,
                "State must remain Disconnected after post-disconnect BT recovery emission",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Counterpart to the disconnect-gate test: environmental recovery MUST still function while a connection is
     * explicitly desired (connectionRequested == true). When the BT radio toggles off then back on (user toggled
     * airplane mode, BT permission revoked/restored, etc.) the state listener must tear down and restart the transport.
     * Only [disconnect] clears the gate; environmental stops via `stopTransportLocked()` do not.
     */
    @Test
    fun `BLE environmental recovery restarts transport while connection is still desired`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            // Environmental stop: BT radio disabled while a connection is active.
            // stopTransportLocked() fires but connectionRequested stays true (only disconnect() clears it).
            bluetoothRepository.setBluetoothEnabled(false)
            testDispatcher.scheduler.runCurrent()
            // Drain the polite-disconnect frame inside the listener's stopTransportLocked() (500ms).
            advanceTimeBy(1_000L)

            assertTrue(
                createdTransports.first().closeCalled,
                "BLE-disabled should close the running transport via environmental stop",
            )

            // Environmental recovery: BT radio re-enabled. connectionRequested is still true, so the
            // listener MUST call startTransportLocked() and bring the transport back.
            bluetoothRepository.setBluetoothEnabled(true)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                2,
                createdTransports.size,
                "BLE-enabled should restart transport via environmental recovery (connectionRequested still true)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Network/TCP counterpart to `BLE state recovery does not restart transport after explicit disconnect`.
     *
     * The [networkRepository.networkAvailable] listener (see `initStateListeners`) consults the same
     * `connectionRequested` gate as the BLE listener: after an explicit [SharedRadioInterfaceService.disconnect], a
     * network-available emission MUST NOT resurrect the transport. Without the gate, a connectivity cycle (Wi-Fi
     * toggled off→on, network handoff) would silently restart a transport the user tore down.
     */
    @Test
    fun `network available recovery does not restart transport after explicit disconnect`() = runTest(testDispatcher) {
        clock = 0L
        val networkAvailability = MutableStateFlow<Boolean>(true)
        val service = createConnectedService("t192.168.1.100", networkAvailability = networkAvailability)
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            // Explicit user-initiated disconnect: clears the connectionRequested gate BEFORE
            // stopTransportLocked() so a racing network-listener emission cannot re-arm the transport.
            service.disconnect()
            // Drain the polite-disconnect frame (production waits POLITE_DISCONNECT_DRAIN_MS = 500ms).
            advanceTimeBy(1_000L)

            val transportCountAfterDisconnect = createdTransports.size

            // Force a fresh network-available cycle (false → true). The initial listener subscription
            // already consumed the default true emission during connect(), so toggling is required to
            // deliver a NEW true emission that would trigger startTransportLocked().
            networkAvailability.value = false
            testDispatcher.scheduler.runCurrent()
            networkAvailability.value = true
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                transportCountAfterDisconnect,
                createdTransports.size,
                "network-available emission after disconnect must NOT restart transport (connectionRequested gate)",
            )
            assertFalse(
                service.connectionState.value == ConnectionState.Connected,
                "State must remain Disconnected after post-disconnect network recovery emission",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    // ─── connectionRequested gate: setDeviceAddress(null)/("n") deselect ────────────────────

    /**
     * Regression: after [SharedRadioInterfaceService.setDeviceAddress] with `null`/`"n"` (deselect), the
     * `connectionRequested` gate MUST be cleared so subsequent BLE state emissions cannot restart the transport.
     * Without the gate-clear in setDeviceAddress(), BT recovery (user toggled BT off→on) would silently resurrect a
     * transport for a device the user explicitly deselected — leaving the app "connected" to an unselected device with
     * no orchestrator collector.
     */
    @Test
    fun `BLE state recovery does not restart transport after setDeviceAddress deselect`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            // Explicit device deselect: setDeviceAddress(null)/("n") must clear the connectionRequested
            // gate BEFORE stopTransportLocked() so a racing state-listener emission cannot re-arm it.
            service.setDeviceAddress("n")
            // Drain the polite-disconnect frame (production waits POLITE_DISCONNECT_DRAIN_MS = 500ms).
            advanceTimeBy(1_000L)

            val transportCountAfterDeselect = createdTransports.size

            // Force a fresh BLE state cycle (disabled → enabled). The initial listener subscription
            // already consumed the default enabled=true emission during connect(), so toggling is
            // required to deliver a NEW enabled=true emission that would trigger startTransportLocked().
            bluetoothRepository.setBluetoothEnabled(false)
            testDispatcher.scheduler.runCurrent()
            bluetoothRepository.setBluetoothEnabled(true)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                transportCountAfterDeselect,
                createdTransports.size,
                "BT-enabled emission after deselect must NOT restart transport (connectionRequested gate cleared)",
            )
            assertFalse(
                service.connectionState.value == ConnectionState.Connected,
                "State must NOT be Connected after post-deselect BT recovery emission",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Network/TCP counterpart to `BLE state recovery does not restart transport after setDeviceAddress deselect`.
     *
     * After [SharedRadioInterfaceService.setDeviceAddress] with `null`/`"n"`, a network-available emission MUST NOT
     * resurrect the transport. Without the gate-clear in setDeviceAddress(), network recovery (Wi-Fi toggled off→on,
     * network handoff) would silently restart a transport for a device the user explicitly deselected.
     */
    @Test
    fun `network available recovery does not restart transport after setDeviceAddress deselect`() =
        runTest(testDispatcher) {
            clock = 0L
            val networkAvailability = MutableStateFlow<Boolean>(true)
            val service = createConnectedService("t192.168.1.100", networkAvailability = networkAvailability)
            try {
                assertEquals(1, createdTransports.size, "Initial connect should create one transport")

                // Explicit device deselect: setDeviceAddress(null)/("n") must clear the connectionRequested
                // gate BEFORE stopTransportLocked() so a racing network-listener emission cannot re-arm it.
                service.setDeviceAddress("n")
                // Drain the polite-disconnect frame (production waits POLITE_DISCONNECT_DRAIN_MS = 500ms).
                advanceTimeBy(1_000L)

                val transportCountAfterDeselect = createdTransports.size

                // Force a fresh network-available cycle (false → true). The initial listener subscription
                // already consumed the default true emission during connect(), so toggling is required to
                // deliver a NEW true emission that would trigger startTransportLocked().
                networkAvailability.value = false
                testDispatcher.scheduler.runCurrent()
                networkAvailability.value = true
                testDispatcher.scheduler.runCurrent()
                advanceTimeBy(1_000L)

                assertEquals(
                    transportCountAfterDeselect,
                    createdTransports.size,
                    "network-available emission after deselect must NOT restart transport (connectionRequested gate cleared)",
                )
                assertFalse(
                    service.connectionState.value == ConnectionState.Connected,
                    "State must NOT be Connected after post-deselect network recovery emission",
                )
            } finally {
                service.disconnect()
                advanceTimeBy(1_000L)
            }
        }

    // ─── restartTransport gate contract ─────────────────────────────────────────────────────
    //
    // [SharedRadioInterfaceService.restartTransport] is the app-level handshake-stall recovery
    // path. It mirrors BLE liveness silent recovery: stopTransportLocked(notifyPermanent=false,
    // sendPoliteDisconnect=false) then startTransportLocked(). The isRestarting CAS runs
    // synchronously BEFORE transportMutex (mirroring checkLiveness), then the remaining gates run
    // inside the mutex. The gate contract has four early-return gates that MUST all hold for the
    // cycle to fire, evaluated in this exact order:
    //   1. isRestarting.compareAndSet(false, true) succeeds  (reuses the liveness CAS; synchronous,
    //      BEFORE acquiring transportMutex — both restart paths serialize on this CAS first)
    //   2. connectionRequested == true  (cleared by disconnect() / setDeviceAddress(null)/("n");
    //      checked inside transportMutex)
    //   3. getBondedDeviceAddress() != null  (re-validates the selected address; inside transportMutex)
    //   4. radioTransport != null  (defends against a stale restart after an environmental stop;
    //      inside transportMutex)
    // The cycle must also be address-preserving and silent (no permanent Disconnected, no
    // user-facing error). The tests below lock each leg of that contract.

    /**
     * Happy path: [SharedRadioInterfaceService.restartTransport] after a normal [connect] with a selected address stops
     * the running transport and creates a fresh one. Mirrors the BLE liveness "timeout closes old transport and creates
     * fresh one" assertion shape.
     */
    @Test
    fun `restartTransport after connect closes old transport and creates fresh one`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            val initialTransport = createdTransports.first()

            // Lock the sendPoliteDisconnect=false contract: clear any bytes recorded during the
            // initial connect so sentData reflects only writes performed during restartTransport.
            initialTransport.sentData.clear()

            service.restartTransport()
            // sendPoliteDisconnect = false → no 500ms drain inside the cycle. Under
            // UnconfinedTestDispatcher the whole stop/start runs inline; runCurrent is
            // belt-and-suspenders. The trailing disconnect() covers its own polite delay.
            testDispatcher.scheduler.runCurrent()

            assertEquals(2, createdTransports.size, "restartTransport should create exactly one fresh transport")
            assertTrue(initialTransport.closeCalled, "Old transport must be closed by restartTransport")
            assertEquals(1, initialTransport.closeCount, "Old transport closed exactly once (no double-close)")
            assertTrue(
                initialTransport.sentData.isEmpty(),
                "restartTransport must NOT write any bytes to the old transport (sendPoliteDisconnect=false)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: [SharedRadioInterfaceService.restartTransport] MUST be a no-op after an explicit
     * [SharedRadioInterfaceService.disconnect]. disconnect() clears `connectionRequested` BEFORE stopTransportLocked(),
     * and restartTransport() consults that gate as its first check. Without the gate, a racing handshake-induced
     * restart would silently resurrect a transport the user tore down.
     */
    @Test
    fun `restartTransport is a no-op after explicit disconnect`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            service.disconnect()
            advanceTimeBy(1_000L)
            val transportCountAfterDisconnect = createdTransports.size

            service.restartTransport()
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                transportCountAfterDisconnect,
                createdTransports.size,
                "restartTransport must not create a transport after disconnect (connectionRequested gate)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: [SharedRadioInterfaceService.restartTransport] MUST be a no-op after
     * [SharedRadioInterfaceService.setDeviceAddress] with `null`. The deselect clears `connectionRequested` AND leaves
     * getBondedDeviceAddress() == null (invalid address); either gate alone is sufficient to skip the restart, but both
     * must hold to defend against a stale listener emission re-arming the transport.
     */
    @Test
    fun `restartTransport is a no-op after setDeviceAddress null`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            service.setDeviceAddress(null)
            advanceTimeBy(1_000L)
            val transportCountAfterDeselect = createdTransports.size

            service.restartTransport()
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                transportCountAfterDeselect,
                createdTransports.size,
                "restartTransport must not create a transport after setDeviceAddress(null)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Counterpart to the null-deselect test: `"n"` is the UI sentinel for "no device" and is sanitized to `null` inside
     * [SharedRadioInterfaceService.setDeviceAddress]. The same gate must skip restartTransport for both forms.
     */
    @Test
    fun `restartTransport is a no-op after setDeviceAddress n`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            service.setDeviceAddress("n")
            advanceTimeBy(1_000L)
            val transportCountAfterDeselect = createdTransports.size

            service.restartTransport()
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                transportCountAfterDeselect,
                createdTransports.size,
                "restartTransport must not create a transport after setDeviceAddress(\"n\")",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: [SharedRadioInterfaceService.restartTransport] cycles the transport in place WITHOUT writing a new
     * devAddr or clearing currentDeviceAddressFlow. The caller (MeshConnectionManager) is the sole owner of address
     * changes; restartTransport must never silently re-bind to a different device or evict the selection.
     */
    @Test
    fun `restartTransport preserves selected device address`() = runTest(testDispatcher) {
        clock = 0L
        val address = "xAA:BB:CC:DD:EE:FF"
        val service = createConnectedService(address)
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            val addrBefore = service.getDeviceAddress()
            val prefsAddrBefore = radioPrefs.devAddr.value

            service.restartTransport()
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                addrBefore,
                service.getDeviceAddress(),
                "getDeviceAddress must not change across restartTransport",
            )
            assertEquals(address, service.getDeviceAddress(), "Selected device address preserved")
            assertEquals(
                prefsAddrBefore,
                radioPrefs.devAddr.value,
                "radioPrefs.devAddr must not be rewritten by restartTransport",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: [SharedRadioInterfaceService.restartTransport] mirrors BLE liveness silent recovery. It MUST emit a
     * transient DeviceSleep (via onDisconnect(isPermanent = false)) BEFORE the stop/start cycle so the subsequent
     * onConnect() produces a real Connected transition (StateFlow is idempotent on same-value — without the DeviceSleep
     * flip, Connected -> Connected is a no-op and MeshConnectionManager stays stuck on its app-level Disconnected
     * state). It MUST NOT emit a permanent user-facing Disconnected state — the caller drives app-level state
     * transitions separately, and surfacing a permanent disconnect for a self-healing cycle would pop a confusing modal
     * for a transient condition.
     */
    @Test
    fun `restartTransport does not emit permanent Disconnected`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            val stateEmissions = mutableListOf<ConnectionState>()
            val collectJob = backgroundScope.launch { service.connectionState.collect { stateEmissions.add(it) } }

            service.restartTransport()
            testDispatcher.scheduler.runCurrent()

            collectJob.cancel()

            // The DeviceSleep emission is the intended transport-level transition that lets
            // MeshConnectionManager observe a real Connected transition on the fresh transport.
            assertTrue(
                ConnectionState.DeviceSleep in stateEmissions,
                "restartTransport must emit transient DeviceSleep so the post-restart onConnect() re-triggers handleConnected() (emitted: $stateEmissions)",
            )
            assertFalse(
                ConnectionState.Disconnected in stateEmissions,
                "restartTransport must not emit permanent Disconnected state (emitted: $stateEmissions)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: [SharedRadioInterfaceService.restartTransport] must not emit a user-facing connection error.
     * _connectionError is a no-replay SharedFlow, so the collector must subscribe BEFORE the restart; under
     * UnconfinedTestDispatcher the launch runs eagerly up to its first suspension (awaiting SharedFlow emission). A
     * silent-recovery cycle surfacing an error modal is the same UX bug as a liveness recovery surfacing one.
     */
    @Test
    fun `restartTransport does not emit user-facing connection error`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            val errors = mutableListOf<String>()
            val collectJob = backgroundScope.launch { service.connectionError.collect { errors.add(it) } }

            service.restartTransport()
            testDispatcher.scheduler.runCurrent()

            collectJob.cancel()

            assertTrue(
                errors.isEmpty(),
                "restartTransport must not emit user-facing connection error (got: $errors)",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: [SharedRadioInterfaceService.restartTransport] reuses the BLE liveness `isRestarting` CAS so a
     * concurrent liveness silent-recovery cycle and a handshake-induced restart cannot stack. The loser of the CAS
     * observes `isRestarting == true` and defers to the in-flight cycle, preventing a double stop/start race on the
     * transport.
     *
     * restartTransport performs the isRestarting CAS synchronously BEFORE acquiring transportMutex (mirroring
     * checkLiveness). Under this pattern, a concurrent liveness restart and a handshake-induced restart serialize on
     * the CAS first — the loser returns immediately without touching the mutex. This is deterministic on all
     * dispatchers, not just UnconfinedTestDispatcher.
     *
     * Deterministic in-flight overlap: a [GatedFakeRadioTransport] suspends the liveness restart genuinely inside
     * stopTransportLocked → close() (awaiting closeGate). While the liveness coroutine holds `isRestarting == true`
     * inside its restart cycle, a concurrent `restartTransport()` call CAS-fails on `isRestarting` and returns
     * immediately without ever acquiring `transportMutex`. A stacking bug (CAS ignored, or CAS performed inside the
     * mutex) would produce 3 transports.
     */
    @Test
    fun `restartTransport coordinates with in-flight liveness restart via isRestarting`() = runTest(testDispatcher) {
        val gatedTransports = mutableListOf<GatedFakeRadioTransport>()
        val closeGate = CompletableDeferred<Unit>()
        // Publish the gate to activeCloseGate so tearDown can release it even if an assertion
        // throws before we reach the inner try/finally — otherwise disconnect() below would
        // hang forever on the gated close().
        activeCloseGate = closeGate
        val transportProvider: () -> RadioTransport = {
            GatedFakeRadioTransport(closeGate).also { gatedTransports.add(it) }
        }

        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF", transportProvider)
        try {
            assertEquals(1, gatedTransports.size, "Initial connect should create one transport")
            val initialTransport = gatedTransports.first()

            // Past the 60s threshold → first checkLiveness CAS-sets isRestarting=true and
            // launches a restart coroutine whose close() suspends on closeGate. Under
            // UnconfinedTestDispatcher the launched coroutine runs eagerly up to the
            // suspension point: by the time checkLiveness() returns, mutex is held and
            // isRestarting == true.
            clock = 65_000L
            service.checkLiveness()

            // Issue restartTransport() while the liveness restart is in-flight. Under the refactored
            // CAS-before-mutex pattern, restartTransport's CAS fails immediately (isRestarting is already
            // true from checkLiveness's synchronous CAS). It returns without acquiring the mutex.
            val restartJob = backgroundScope.launch { service.restartTransport() }
            testDispatcher.scheduler.runCurrent()

            try {
                // Pre-release: liveness still in-flight (close() suspended on closeGate),
                // but restartTransport already returned via CAS-fail on isRestarting (it never
                // touched transportMutex). No fresh transport from restartTransport, no stacking.
                assertEquals(
                    1,
                    gatedTransports.size,
                    "No fresh transport created while liveness restart is in-flight",
                )
                assertTrue(initialTransport.closeCalled, "Liveness restart must have entered close()")
                assertEquals(
                    0,
                    initialTransport.closeCompletedCount,
                    "Liveness restart close() must still be suspended (gate not yet released)",
                )
                assertTrue(
                    restartJob.isCompleted,
                    "restartTransport should CAS-fail immediately when liveness already holds isRestarting, " +
                        "not queue on transportMutex",
                )
            } finally {
                // Release the gate unconditionally so the suspended liveness restart can
                // complete. tearDown also releases activeCloseGate, but completing here is
                // required for the post-finally assertions to observe the resumed cycle.
                closeGate.complete(Unit)
            }

            // Release the gate: liveness restart completes (close + startTransport).
            // restartTransport already returned via CAS-fail, so no second cycle. Exactly 2 transports.
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            restartJob.join()

            // Exactly 2 transports: 1 initial + 1 from liveness restart. restartTransport
            // was a no-op via the isRestarting CAS. A stacking bug would produce 3.
            assertEquals(
                2,
                gatedTransports.size,
                "restartTransport must not stack another cycle on an in-flight liveness restart",
            )
            assertEquals(1, initialTransport.closeCount, "Initial transport closed exactly once")
            assertEquals(
                1,
                initialTransport.closeCompletedCount,
                "Initial transport close completed exactly once after gate release",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression for the `radioTransport == null` gate added to [SharedRadioInterfaceService.restartTransport] (the
     * mutex-protected early-return, checked after the `isRestarting` CAS but before the `onDisconnect(isPermanent =
     * false)` call).
     *
     * Scenario: a TCP transport has been torn down by an environmental stop (`networkAvailable = false`) but
     * `connectionRequested` is intentionally preserved so the network recovery listener can re-bring-up the transport
     * when connectivity returns. A stale handshake-stall restart fired in that window MUST be a no-op:
     * - It MUST NOT create a fresh transport via `startTransportLocked()` (that would bypass the recovery listener,
     *   which owns the re-bring-up).
     * - It MUST NOT emit `DeviceSleep` via `onDisconnect(isPermanent = false)` (the gate returns before that call), so
     *   the recovery path's later transitions stay meaningful.
     *
     * Counter-assertion: the recovery listener itself MUST still function after the gate fires — toggling
     * `networkAvailable` back to `true` MUST create the second transport, proving the gate did not break the documented
     * re-bring-up path.
     */
    @Test
    fun `restartTransport is a no-op when transport is environmentally stopped but connection remains requested`() =
        runTest(testDispatcher) {
            clock = 0L
            val networkAvailability = MutableStateFlow<Boolean>(true)
            val service = createConnectedService("t192.168.1.100", networkAvailability = networkAvailability)
            try {
                assertEquals(1, createdTransports.size, "Initial connect should create one transport")
                val initialTransport = createdTransports.first()

                // Subscribe BEFORE the environmental stop so we capture every state transition through
                // the stop, the gated restartTransport, and the recovery. _connectionState is a StateFlow
                // (replays current value to late collectors), so the launch's first emission is Connected.
                val stateEmissions = mutableListOf<ConnectionState>()
                val collectJob = backgroundScope.launch { service.connectionState.collect { stateEmissions.add(it) } }
                testDispatcher.scheduler.runCurrent()

                // Environmental stop: network drops while a TCP transport is running. The network
                // listener (see initStateListeners) calls stopTransportLocked() with defaults
                // (notifyPermanent=true → onDisconnect(isPermanent=true) → Disconnected). The transport
                // is closed and radioTransport becomes null, but connectionRequested STAYS true so the
                // recovery listener can re-bring-up later.
                networkAvailability.value = false
                testDispatcher.scheduler.runCurrent()
                // Drain the polite-disconnect frame (POLITE_DISCONNECT_DRAIN_MS = 500ms).
                advanceTimeBy(1_000L)

                assertTrue(initialTransport.closeCalled, "Environmental stop must close the running TCP transport")
                assertEquals(
                    1,
                    createdTransports.size,
                    "Environmental stop must not create a new transport (only teardown)",
                )

                // Stale handshake-stall restart fired in the window where radioTransport == null but
                // connectionRequested is still true. The new gate must short-circuit AFTER the
                // isRestarting CAS (inside transportMutex) but BEFORE the onDisconnect(isPermanent=false) DeviceSleep
                // emission.
                service.restartTransport()
                testDispatcher.scheduler.runCurrent()
                advanceTimeBy(1_000L)

                assertEquals(
                    1,
                    createdTransports.size,
                    "restartTransport must be a no-op when radioTransport == null (gate fired)",
                )
                assertFalse(
                    ConnectionState.DeviceSleep in stateEmissions,
                    "Gated restartTransport must NOT emit DeviceSleep " +
                        "(returned before onDisconnect(isPermanent=false)); emitted: $stateEmissions",
                )

                // Environmental recovery: networkAvailable flips back to true. connectionRequested is
                // still true (neither the environmental stop nor the gated restart cleared it), so the
                // recovery listener MUST call startTransportLocked() and create the second transport.
                // This proves the new gate did not break the documented re-bring-up path.
                networkAvailability.value = true
                testDispatcher.scheduler.runCurrent()
                advanceTimeBy(1_000L)

                collectJob.cancel()

                assertEquals(
                    2,
                    createdTransports.size,
                    "Network recovery must re-bring-up the transport after the gated no-op restart (gate still set)",
                )
            } finally {
                service.disconnect()
                advanceTimeBy(1_000L)
            }
        }

    // ─── USB serial replug observer (observeUsbRecoveryTriggers) ───────────────────────────
    //
    // The USB-serial recovery observer (see observeUsbRecoveryTriggers) watches the selected SERIAL device's
    // presence via [SerialDevicePresence.deviceKeys] combined with [currentDeviceAddressFlow] and
    // [_connectionState]. It arms recovery only after the SELECTED serial key has been observed absent and then
    // present again; when that armed edge converges with zombie DeviceSleep, it tears down the zombie transport
    // (left over from the unplug I/O-death path that emits DeviceSleep without nulling radioTransport) and starts a
    // fresh one — sparing the user a manual re-select.
    //
    // Pipeline (see SharedRadioInterfaceService.observeUsbRecoveryTriggers):
    //   selectedPresence = combine(currentDeviceAddressFlow, serialDevicePresence.deviceKeys)
    //   combine(selectedPresence, _connectionState)
    //     .runningFold(...)         // arms only after selected key goes absent → present
    //     .filter { triggerRecovery }
    //     .onEach { transportMutex.withLock {
    //         if (!connectionRequested) return@withLock
    //         if (runningTransportId != InterfaceId.SERIAL) return@withLock
    //         if (state is Connected || state is Connecting) return@withLock  // race-defense
    //         ignoreExceptionSuspend { stopTransportLocked(notifyPermanent = false, sendPoliteDisconnect = false) }
    //         ignoreExceptionSuspend { startTransportLocked() }
    //     } }
    //
    // The tests below drive the controllable [serialDeviceKeys] flow directly to exercise each branch of that gate
    // contract.

    /**
     * Happy path: a SERIAL transport left in zombie DeviceSleep (after an unplug I/O death) is recovered automatically
     * when the same USB device is replugged. The observer must call stopTransportLocked + startTransportLocked exactly
     * once, producing exactly one fresh transport and closing the zombie.
     *
     * The zombie state mirrors what `SerialConnectionListener.onDisconnected → onDeviceDisconnect` leaves behind: a
     * transient DeviceSleep emission WITHOUT nulling radioTransport (the I/O-death path doesn't run
     * stopTransportLocked). Only the observer's stop/start cycle reaps the zombie and builds a fresh transport for the
     * replugged device.
     */
    @Test
    fun `USB replug of selected serial device restarts transport when zombie`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("s/dev/bus/usb/001/002")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            val initialTransport = createdTransports.first()

            // Simulate the unplug I/O-death path: SerialConnectionListener.onDisconnected →
            // onDeviceDisconnect emits a transient DeviceSleep but does NOT null radioTransport
            // (the zombie state the observer comment specifies it recovers from).
            service.onDisconnect(isPermanent = false)
            assertEquals(ConnectionState.DeviceSleep, service.connectionState.value, "Precondition: zombie state")

            // Replug: serialDevicePresence emits the device's `rest` key (the address minus its 's').
            serialDeviceKeys.value = setOf("/dev/bus/usb/001/002")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(2, createdTransports.size, "Replug should create exactly one fresh transport")
            assertTrue(initialTransport.closeCalled, "Zombie transport must be closed by stopTransportLocked")
            assertEquals(1, initialTransport.closeCount, "Zombie transport closed exactly once (no double-close)")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Negative counterpart: only the SELECTED device's key triggers recovery. A different USB device being plugged in
     * must not perturb the selected transport — `rest in keys` evaluates false, the combined flow stays false, and
     * distinctUntilChanged swallows the redundant false emission.
     */
    @Test
    fun `USB replug of unrelated serial device does not restart transport`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("s/dev/bus/usb/001/002")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            service.onDisconnect(isPermanent = false)
            assertEquals(ConnectionState.DeviceSleep, service.connectionState.value, "Precondition: zombie state")

            // A different physical device — different `rest` key from the selected address.
            serialDeviceKeys.value = setOf("/dev/bus/usb/001/003")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(1, createdTransports.size, "Unrelated device replug must NOT restart transport")
            assertFalse(
                createdTransports.first().closeCalled,
                "Zombie transport must NOT be closed for an unrelated device",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Healthy-state guard: when the selected device is already Connected (or Connecting), the observer must NOT fire a
     * restart. This guards the cold-flow false → true transition that happens when the user selects an already-present
     * USB device — setDeviceAddress has already brought the transport up to Connected, so a "recovery" cycle would be a
     * redundant teardown of a healthy link.
     */
    @Test
    fun `USB replug does not restart transport when already Connected`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("s/dev/bus/usb/001/002")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            assertEquals(ConnectionState.Connected, service.connectionState.value, "Precondition: Connected state")

            // Device present in the keys — but state is Connected (healthy), not zombie.
            serialDeviceKeys.value = setOf("/dev/bus/usb/001/002")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(1, createdTransports.size, "Healthy-state guard must skip restart for Connected state")
            assertFalse(createdTransports.first().closeCalled, "Healthy transport must NOT be closed")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: the unplug/replug race in which the USB presence signal arrives BEFORE the I/O-death callback has
     * flipped `_connectionState` to DeviceSleep. The combined trigger now includes `_connectionState` so that the
     * subsequent Connected → DeviceSleep transition re-emits the flow and fires recovery — without that, the
     * early-return on Connected swallows the presence emission, the later state transition does not re-emit
     * (distinctUntilChanged swallows true → true), and the zombie transport never recovers.
     */
    @Test
    fun `USB replug recovers when presence arrives before DeviceSleep`() = runTest(testDispatcher) {
        clock = 0L
        val selectedKey = "/dev/bus/usb/001/002"
        serialDeviceKeys.value = setOf(selectedKey)
        val service = createConnectedService("s$selectedKey")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            assertEquals(ConnectionState.Connected, service.connectionState.value, "Precondition: Connected state")

            // The actual detach is observed first, while state is still Connected.
            serialDeviceKeys.value = emptySet()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            assertEquals(1, createdTransports.size, "Detach while Connected must NOT restart transport")
            assertFalse(
                createdTransports.first().closeCalled,
                "Detach while Connected must NOT close the transport",
            )

            // Replug signal arrives WHILE state is still Connected — the trigger must NOT fire yet.
            serialDeviceKeys.value = setOf(selectedKey)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            assertEquals(1, createdTransports.size, "Presence while Connected must NOT restart transport")
            assertFalse(createdTransports.first().closeCalled, "Healthy transport must NOT be closed")

            // The I/O-death callback now fires (late) and flips state to DeviceSleep. The combine
            // re-emits because state is one of its sources; recovery fires after the state catches up.
            service.onDisconnect(isPermanent = false)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(2, createdTransports.size, "Recovery MUST fire once state catches up to DeviceSleep")
            assertTrue(
                createdTransports.first().closeCalled,
                "Zombie transport must be closed by stopTransportLocked",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: Android serialDevices starts as an empty snapshot before UsbRepository refreshes. That initial empty
     * → present emission is not a detach/replug edge and must not arm recovery for a later normal unplug callback.
     */
    @Test
    fun `USB initial empty presence does not arm recovery`() = runTest(testDispatcher) {
        clock = 0L
        val selectedKey = "/dev/bus/usb/001/002"
        val service = createConnectedService("s$selectedKey")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            val initialTransport = createdTransports.first()
            assertEquals(ConnectionState.Connected, service.connectionState.value, "Precondition: Connected state")

            serialDeviceKeys.value = setOf(selectedKey)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(1, createdTransports.size, "Initial empty → present must NOT restart transport")
            assertFalse(initialTransport.closeCalled, "Initial presence refresh must not close the transport")

            service.onDisconnect(isPermanent = false)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                1,
                createdTransports.size,
                "Later DeviceSleep with initially-present key must NOT restart transport",
            )
            assertFalse(initialTransport.closeCalled, "Unplug callback alone must not close the transport")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: some USB stacks deliver the transport disconnect callback before UsbRepository has emitted the detach
     * that removes the selected key. A present-at-start key plus DeviceSleep is only "normal unplug in progress", not a
     * replug, so recovery must stay quiet until the observer sees the selected key go absent and then present again.
     */
    @Test
    fun `USB disconnect before detach emission waits for absent to present replug`() = runTest(testDispatcher) {
        clock = 0L
        serialDeviceKeys.value = setOf("/dev/bus/usb/001/002")
        val service = createConnectedService("s/dev/bus/usb/001/002")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            val initialTransport = createdTransports.first()
            assertEquals(ConnectionState.Connected, service.connectionState.value, "Precondition: Connected state")

            // The transport-level disconnect arrives before the deviceKeys detach emission. Because the key was
            // already present, this must not be treated as an absent → present replug.
            service.onDisconnect(isPermanent = false)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                1,
                createdTransports.size,
                "DeviceSleep with an already-present key must NOT restart transport",
            )
            assertFalse(initialTransport.closeCalled, "Unplug callback alone must not close the transport")

            // Now the actual detach/attach sequence is visible: absent first, then present again.
            serialDeviceKeys.value = emptySet()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(1, createdTransports.size, "Detach emission alone must not restart transport")
            assertFalse(initialTransport.closeCalled, "Detach emission alone must not close the transport")

            serialDeviceKeys.value = setOf("/dev/bus/usb/001/002")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(2, createdTransports.size, "Absent → present replug should create one fresh transport")
            assertTrue(initialTransport.closeCalled, "Zombie transport must be closed by stopTransportLocked")
            assertEquals(1, initialTransport.closeCount, "Zombie transport closed exactly once")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: after explicit [SharedRadioInterfaceService.disconnect], the observer MUST be disarmed. disconnect()
     * clears `connectionRequested` BEFORE stopTransportLocked(), so even if the selected device reappears the
     * observer's first gate (`!connectionRequested → return@withLock`) fires and no transport is resurrected for a
     * connection the user tore down. Same shape as the BLE/network post-disconnect recovery regressions above.
     */
    @Test
    fun `USB replug does not restart transport after explicit disconnect`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("s/dev/bus/usb/001/002")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            // Explicit user disconnect: clears connectionRequested gate BEFORE stopTransportLocked(),
            // nulls radioTransport, and clears runningTransportId. The observer is now disarmed on
            // multiple gates.
            service.disconnect()
            advanceTimeBy(1_000L)
            val transportCountAfterDisconnect = createdTransports.size

            // Replug the selected device — the observer must observe but not act.
            serialDeviceKeys.value = setOf("/dev/bus/usb/001/002")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                transportCountAfterDisconnect,
                createdTransports.size,
                "Replug after explicit disconnect must NOT restart transport (connectionRequested gate)",
            )
            assertFalse(
                service.connectionState.value == ConnectionState.Connected,
                "State must remain Disconnected after post-disconnect replug",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: the reducer's armed-by-absence state must not survive an explicit Disconnected state. A user can
     * unplug while Connected, explicitly disconnect before replugging, then reconnect later. That later normal
     * transport-level unplug callback arrives while the selected key is still present; it must not consume stale arm
     * state from the previous connection and restart the fresh transport.
     */
    @Test
    fun `USB recovery arm clears across explicit disconnect before reconnect`() = runTest(testDispatcher) {
        clock = 0L
        val selectedKey = "/dev/bus/usb/001/002"
        serialDeviceKeys.value = setOf(selectedKey)
        val service = createConnectedService("s$selectedKey")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")
            val initialTransport = createdTransports.first()

            // Detach while Connected arms the replug edge but must not restart while the transport is healthy.
            serialDeviceKeys.value = emptySet()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(1, createdTransports.size, "Detach while Connected must NOT restart transport")
            assertFalse(initialTransport.closeCalled, "Detach while Connected must not close the transport")

            // Explicit disconnect must clear the armed reducer state.
            service.disconnect()
            advanceTimeBy(1_000L)
            val transportCountAfterDisconnect = createdTransports.size
            assertEquals(ConnectionState.Disconnected, service.connectionState.value, "Precondition: disconnected")

            // Replug while disconnected must not preserve stale arm into the next connection.
            serialDeviceKeys.value = setOf(selectedKey)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                transportCountAfterDisconnect,
                createdTransports.size,
                "Replug while disconnected must NOT restart transport",
            )

            service.connect()
            service.onConnect()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            val reconnectedTransport = createdTransports.last()
            val transportCountAfterReconnect = createdTransports.size
            assertEquals(ConnectionState.Connected, service.connectionState.value, "Precondition: reconnected")

            service.onDisconnect(isPermanent = false)
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                transportCountAfterReconnect,
                createdTransports.size,
                "Normal unplug after reconnect must NOT restart from stale armed state",
            )
            assertFalse(
                reconnectedTransport.closeCalled,
                "Fresh transport must not be closed by stale recovery arm",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    /**
     * Regression: duplicate same-value emissions of [serialDevicePresence.deviceKeys] must not produce duplicate
     * restarts. StateFlow semantics dedupe equal set re-publications at the source, and the pipeline's
     * distinctUntilChanged() coalesces any redundant true → true emissions that slip through, so even a flappy
     * publisher that re-emits the same set produces exactly ONE stop+start cycle for one physical replug.
     */
    @Test
    fun `USB replug duplicate same-key emissions produce exactly one restart`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("s/dev/bus/usb/001/002")
        try {
            assertEquals(1, createdTransports.size, "Initial connect should create one transport")

            service.onDisconnect(isPermanent = false)
            assertEquals(ConnectionState.DeviceSleep, service.connectionState.value, "Precondition: zombie state")

            // First replug: triggers one restart (false → true transition through the pipeline).
            serialDeviceKeys.value = setOf("/dev/bus/usb/001/002")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            val transportsAfterFirstReplug = createdTransports.size

            // Duplicate same-value emission (e.g. a flappy UsbRepository re-publishing the same set).
            // StateFlow dedupes equal values at the source; distinctUntilChanged() coalesces any
            // redundant true → true. No additional restart.
            serialDeviceKeys.value = setOf("/dev/bus/usb/001/002")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(
                transportsAfterFirstReplug,
                createdTransports.size,
                "Duplicate same-key emission must NOT produce a second restart",
            )
            assertEquals(2, createdTransports.size, "Exactly one restart total (1 initial + 1 replug)")
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    // ─── Post-OTA GATT cache invalidation flag lifecycle ───────────────────────────────────────

    /**
     * The one-shot cache-invalidation flag is armed by
     * [SharedRadioInterfaceService.requestGattCacheInvalidationOnNextConnect] and consumed exactly once by
     * [SharedRadioInterfaceService.consumeGattCacheInvalidationRequest] (atomic getAndSet).
     * [SharedRadioInterfaceService.disconnect] must clear any still-pending flag so a later reconnect does not silently
     * trigger a stale invalidation.
     */
    @Test
    fun `gatt cache invalidation flag is consumed once and cleared on disconnect`() = runTest(testDispatcher) {
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertFalse(
                service.consumeGattCacheInvalidationRequest(),
                "flag must default to unset before any request",
            )

            service.requestGattCacheInvalidationOnNextConnect()
            assertTrue(
                service.consumeGattCacheInvalidationRequest(),
                "first consume after request must return true",
            )
            assertFalse(
                service.consumeGattCacheInvalidationRequest(),
                "second consume must return false (one-shot getAndSet)",
            )

            // Re-arm; disconnect below must clear it.
            service.requestGattCacheInvalidationOnNextConnect()
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }

        assertFalse(
            service.consumeGattCacheInvalidationRequest(),
            "disconnect must clear the pending GATT cache invalidation flag",
        )
    }

    /**
     * Rebinding to a DIFFERENT device address must drop the pending flag — the cache invalidation was requested for the
     * previous device's post-OTA reboot and must not bleed into the new device's connection.
     * ([SharedRadioInterfaceService.setDeviceAddress] clears `gattCacheInvalidationRequested` when the address
     * changes.)
     */
    @Test
    fun `setDeviceAddress with a different address clears the pending gatt cache invalidation flag`() =
        runTest(testDispatcher) {
            val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
            try {
                service.requestGattCacheInvalidationOnNextConnect()

                assertTrue(service.setDeviceAddress("xBB:11:22:33:44:55"), "setDeviceAddress must accept a new address")
                // The clear happens inside the launched transportMutex.withLock; flush it.
                testDispatcher.scheduler.runCurrent()
                advanceTimeBy(1_000L)

                assertFalse(
                    service.consumeGattCacheInvalidationRequest(),
                    "switching to a different device address must clear the pending flag",
                )
            } finally {
                service.disconnect()
                advanceTimeBy(1_000L)
            }
        }

    /**
     * Counterpart: rebinding to the SAME address is a documented no-op ([SharedRadioInterfaceService.setDeviceAddress]
     * early-returns when already Connected to that address), so it must NOT touch the pending flag.
     */
    @Test
    fun `setDeviceAddress with the same address preserves the pending gatt cache invalidation flag`() =
        runTest(testDispatcher) {
            val address = "xAA:BB:CC:DD:EE:FF"
            val service = createConnectedService(address)
            try {
                service.requestGattCacheInvalidationOnNextConnect()

                assertFalse(
                    service.setDeviceAddress(address),
                    "setDeviceAddress with the same connected address is a documented no-op (returns false)",
                )
                testDispatcher.scheduler.runCurrent()

                assertTrue(
                    service.consumeGattCacheInvalidationRequest(),
                    "same-address rebind must preserve the pending GATT cache invalidation flag",
                )
            } finally {
                service.disconnect()
                advanceTimeBy(1_000L)
            }
        }

    @Test
    fun `setDeviceAddress after deselect preserves pending gatt cache invalidation flag`() = runTest(testDispatcher) {
        val address = "xAA:BB:CC:DD:EE:FF"
        val service = createConnectedService(address)
        try {
            // OTA handler deselects to free the GATT
            assertTrue(service.setDeviceAddress("n"), "deselect must succeed")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            // Post-OTA: arm flag, then re-select the SAME device
            service.requestGattCacheInvalidationOnNextConnect()
            assertTrue(service.setDeviceAddress(address), "re-select must start transport")
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertTrue(
                service.consumeGattCacheInvalidationRequest(),
                "post-OTA re-select (null to address) must preserve the pending flag",
            )
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }
}
