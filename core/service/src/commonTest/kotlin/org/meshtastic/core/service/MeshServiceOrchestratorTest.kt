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

import co.touchlab.kermit.Severity
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.TakPrefs
import org.meshtastic.core.repository.TakServerIntegration
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.local_network_permission_denied_hint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshServiceOrchestratorTest {

    private companion object {
        const val DEFAULT_ADDRESS = "x:AA:BB:CC:DD:EE:FF"
    }

    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeManager: NodeManager = mock(MockMode.autofill)

    private val serviceNotifications: MeshNotificationManager = mock(MockMode.autofill)
    private val takServerIntegration: TakServerIntegration = mock(MockMode.autofill)
    private val takPrefs: TakPrefs = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val connectionManager: MeshConnectionManager = mock(MockMode.autofill)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    /** Stubs the shared flow dependencies used by every test and returns an orchestrator. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createOrchestrator(
        connectionError: MutableSharedFlow<String> = MutableSharedFlow(),
        connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected),
        // A valid default address lets every start() proceed through the new
        // wait-for-valid-address -> DB switch -> connect ordering without per-test boilerplate.
        // Tests that need a different initial address (or no address) override it explicitly.
        currentDeviceAddressFlow: MutableStateFlow<String?> = MutableStateFlow<String?>(DEFAULT_ADDRESS),
        sessionGeneration: MutableStateFlow<Long> = MutableStateFlow(0L),
        activeSession: MutableStateFlow<RadioSessionContext?> =
            MutableStateFlow(currentDeviceAddressFlow.value?.let { RadioSessionContext(sessionGeneration.value, it) }),
        isSessionActive: (RadioSessionContext) -> Boolean = { activeSession.value == it },
        takEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        takRunningFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        // Granted by default so every pre-existing test keeps reaching connect(); DEFAULT_ADDRESS is BLE
        // anyway, but the cold-start ordering test deliberately uses a TCP address.
        localNetworkAccess: LocalNetworkAccess = LocalNetworkAccess { true },
    ): MeshServiceOrchestrator {
        every { radioInterfaceService.connectionError } returns connectionError
        every { radioInterfaceService.connectionState } returns connectionState
        every { radioInterfaceService.currentDeviceAddressFlow } returns currentDeviceAddressFlow
        every { radioInterfaceService.sessionGeneration } returns sessionGeneration
        every { radioInterfaceService.activeSession } returns activeSession
        every { radioInterfaceService.isSessionActive(any()) } calls
            {
                isSessionActive(it.args[0] as RadioSessionContext)
            }
        every { takPrefs.isTakServerEnabled } returns takEnabledFlow
        every { takServerIntegration.isRunning } returns takRunningFlow

        return MeshServiceOrchestrator(
            radioInterfaceService = radioInterfaceService,
            serviceStateWriter = serviceRepository,
            nodeManager = nodeManager,
            serviceNotifications = serviceNotifications,
            takServerIntegration = takServerIntegration,
            takPrefs = takPrefs,
            databaseManager = databaseManager,
            connectionManager = connectionManager,
            dispatchers = dispatchers,
            localNetworkAccess = localNetworkAccess,
            lastBleNodeRecorder = {},
        )
    }

    @Test
    fun testStartWiresComponents() {
        val orchestrator = createOrchestrator()

        assertFalse(orchestrator.isRunning)
        orchestrator.start()
        assertTrue(orchestrator.isRunning)

        verify { serviceNotifications.initChannels() }
        verify { nodeManager.loadCachedNodeDB() }

        orchestrator.stop()
        assertFalse(orchestrator.isRunning)
    }

    @Test
    fun testTakServerStartsAndStopsWithPreference() {
        val takEnabledFlow = MutableStateFlow(false)
        val takRunningFlow = MutableStateFlow(false)

        val orchestrator = createOrchestrator(takEnabledFlow = takEnabledFlow, takRunningFlow = takRunningFlow)

        orchestrator.start()

        // Toggle on
        takEnabledFlow.value = true
        // Exactly 1, not just "at least 1": nothing before this point could have called start() — the
        // orchestrator's collector's only prior emission (isTakServerEnabled's initial `false`) takes the
        // stop() branch instead — so this genuinely proves the toggle drove it.
        verify(exactly(1)) { takServerIntegration.start(any()) }

        // Update mock state to reflect it's running
        takRunningFlow.value = true

        // Toggle off
        takEnabledFlow.value = false
        // Exactly 2, not 1: the real TAKMeshIntegration this mock replaces has its own start()/stop()
        // re-entrancy latch, so its initial no-op stop() (from isTakServerEnabled's starting `false`
        // value, before "Toggle on" above) never reached the wrapped TAKServerManager it forwards to. A
        // bare interface mock has no such latch — the orchestrator really does call stop() twice here
        // (once at that initial emission, once for this toggle) — so asserting exactly(1) would fail,
        // and a bare `verify { stop() }` would pass without proving *this* toggle caused a call.
        verify(exactly(2)) { takServerIntegration.stop() }

        orchestrator.stop()
    }

    @Test
    fun testTakServerCanRetryAfterFailedStart() {
        val takEnabledFlow = MutableStateFlow(false)
        val takRunningFlow = MutableStateFlow(false)
        val lifecycleEvents = mutableListOf<String>()
        // The real TAKMeshIntegration this mock replaces has its own start()/stop() re-entrancy latch
        // (a CAS'd AtomicBoolean) so a redundant stop() while already stopped — or a redundant start()
        // while already started — is a silent no-op instead of forwarding to TAKServerManager. A bare
        // autofill mock has no such state, so replicate the latch here to keep asserting the real
        // lifecycle sequence rather than every call the orchestrator happens to make.
        var started = false
        every { takServerIntegration.start(any()) } calls
            {
                if (!started) {
                    started = true
                    lifecycleEvents += "start"
                }
            }
        every { takServerIntegration.stop() } calls
            {
                if (started) {
                    started = false
                    lifecycleEvents += "stop"
                }
            }
        val orchestrator = createOrchestrator(takEnabledFlow = takEnabledFlow, takRunningFlow = takRunningFlow)

        orchestrator.start()
        // The mock never changes takRunningFlow, modeling a start attempt that failed before listening.
        takEnabledFlow.value = true
        takEnabledFlow.value = false
        takEnabledFlow.value = true

        assertEquals(listOf("start", "stop", "start"), lifecycleEvents)

        orchestrator.stop()
        assertEquals(listOf("start", "stop", "start", "stop"), lifecycleEvents)
    }

    @Test
    fun testStopStopsTakServerWhileStarting() {
        val takEnabledFlow = MutableStateFlow(true)
        val takRunningFlow = MutableStateFlow(false)
        val lifecycleEvents = mutableListOf<String>()
        // See testTakServerCanRetryAfterFailedStart for why this latch replicates the real
        // TAKMeshIntegration's own start()/stop() re-entrancy guard.
        var started = false
        every { takServerIntegration.start(any()) } calls
            {
                if (!started) {
                    started = true
                    lifecycleEvents += "start"
                }
            }
        every { takServerIntegration.stop() } calls
            {
                if (started) {
                    started = false
                    lifecycleEvents += "stop"
                }
            }
        val orchestrator = createOrchestrator(takEnabledFlow = takEnabledFlow, takRunningFlow = takRunningFlow)

        orchestrator.start()
        orchestrator.stop()

        takEnabledFlow.value = false
        orchestrator.start()
        takEnabledFlow.value = true
        orchestrator.stop()

        assertEquals(listOf("start", "stop", "start", "stop"), lifecycleEvents)
    }

    @Test
    fun testStartCallsSwitchActiveDatabase() {
        // New ordering: start() waits for currentDeviceAddressFlow to surface a valid address,
        // then switches the DB to it and connects. getDeviceAddress() is no longer consulted
        // from start(), so drive the address via the flow.
        val deviceAddressFlow = MutableStateFlow<String?>("tcp:192.168.1.100")
        val orchestrator = createOrchestrator(currentDeviceAddressFlow = deviceAddressFlow)

        // Event recorder: locks the cold-start ordering (DB switch -> load cached NodeDB ->
        // connect) without depending on Mokkery's global call-order semantics. The global
        // order verifier (verifySuspend(order) { ... }) fails because start() also drives
        // other calls on the same mocks (currentDeviceAddressFlow collectors, the mid-session DB switch replay, etc.)
        // that interleave with the
        // three calls we care about and break the global sequence. Recording only the
        // three calls under test sidesteps the global check entirely.
        val events = mutableListOf<String>()
        everySuspend { databaseManager.switchActiveDatabase(any()) } calls { events.add("switchDb") }
        every { nodeManager.loadCachedNodeDB() } calls { events.add("loadCachedNodeDb") }
        every { radioInterfaceService.connect() } calls { events.add("connect") }

        orchestrator.start()

        verifySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.100") }
        verify { nodeManager.loadCachedNodeDB() }
        verify { radioInterfaceService.connect() }

        // Locks in the cold-start ordering invariant: DB switch -> load cached NodeDB ->
        // connect. loadCachedNodeDB must run AFTER the DB switch (so it reads from the
        // freshly-selected per-device DB) and BEFORE connect() (so the firmware handshake
        // doesn't see a stale or empty node set). On UnconfinedTestDispatcher the
        // handledLaunch block runs eagerly, producing these three events in order. The
        // mid-session address observer then replays the initial value, producing a
        // redundant "switchDb"; assert only the first three to lock the order without
        // coupling to the redundant replay.
        assertEquals(listOf("switchDb", "loadCachedNodeDb", "connect"), events.take(3))

        orchestrator.stop()
    }

    @Test
    fun testConnectionErrorForwardedToServiceRepository() {
        val connectionError = MutableSharedFlow<String>(extraBufferCapacity = 1)

        val orchestrator = createOrchestrator(connectionError = connectionError)
        orchestrator.start()

        // Emit an error into the radio interface's connectionError flow
        connectionError.tryEmit("BLE connection lost")

        verify { serviceRepository.setErrorMessage("BLE connection lost", Severity.Warn) }

        orchestrator.stop()
    }

    @Test
    fun testStartIsIdempotent() {
        val orchestrator = createOrchestrator()

        orchestrator.start()
        assertTrue(orchestrator.isRunning)

        // Second call should be a no-op
        orchestrator.start()
        assertTrue(orchestrator.isRunning)

        // Components should only be initialized once
        verify(exactly(1)) { serviceNotifications.initChannels() }
        verify(exactly(1)) { nodeManager.loadCachedNodeDB() }

        orchestrator.stop()
        assertFalse(orchestrator.isRunning)
    }

    /**
     * Regression: when [RadioInterfaceService.currentDeviceAddressFlow] emits a new address mid-session (e.g. a late
     * process-lifecycle address resolution on Android), the orchestrator must propagate it to [DatabaseManager] so Room
     * writes land in the right per-device DB. Previously start() only switched the DB once via getDeviceAddress() and
     * missed subsequent address changes, leaving the new session writing to the old DB.
     *
     * DatabaseManager is idempotent, so the redundant initial replay (matching the getDeviceAddress() snapshot) is a
     * no-op; we therefore assert by argument value rather than brittle exact counts.
     */
    @Test
    fun testCurrentDeviceAddressChangeSwitchesActiveDatabaseAfterStart() {
        val deviceAddressFlow = MutableStateFlow<String?>("tcp:192.168.1.100")
        val orchestrator = createOrchestrator(currentDeviceAddressFlow = deviceAddressFlow)

        orchestrator.start()

        // Initial address was switched (the first-valid emission in start() + the StateFlow
        // replay into the mid-session observer; DatabaseManager is idempotent for the dup).
        verifySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.100") }

        // Mid-session address resolution must propagate to DatabaseManager.
        deviceAddressFlow.value = "tcp:10.0.0.5"
        verifySuspend { databaseManager.switchActiveDatabase("tcp:10.0.0.5") }

        orchestrator.stop()
    }

    /**
     * Lifecycle invariant: when the transport reports [ConnectionState.Connected] while the orchestrator is stopped
     * (e.g. a late BLE liveness reconnect, or an emission that arrives after [MeshService.onDestroy]), the orchestrator
     * MUST NOT auto-restart. The orchestrator is a Koin `@Single` whose lifetime exceeds the Android `Service`, so an
     * unguarded observer launched in `init {}` would resurrect the orchestrator after a deliberate stop — connecting
     * the radio in the background with no foreground service, no wake lock, and no UI.
     *
     * This also preserves the documented invariant that [MeshConnectionManagerImpl] is the only consumer of
     * [RadioInterfaceService.connectionState]. Recovery after stop is the host's responsibility: it must call `start()`
     * explicitly (e.g. via a fresh `MeshService.onCreate()`).
     *
     * Replaces the previous "orphan-Connected recovery" behavior that was removed for violating both invariants.
     */
    @Test
    fun testConnectedWhileStoppedDoesNotRestartWithoutExplicitStart() {
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val orchestrator = createOrchestrator(connectionState = connectionState)

        // Stopped; never call start() explicitly.
        assertFalse(orchestrator.isRunning)

        // Transport reaches Connected — orchestrator must stay stopped.
        connectionState.value = ConnectionState.Connected
        assertFalse(orchestrator.isRunning)

        // Likewise, subsequent transport state cycles must not restart the orchestrator.
        connectionState.value = ConnectionState.Disconnected
        connectionState.value = ConnectionState.Connected
        assertFalse(orchestrator.isRunning)
    }

    @Test
    fun tcpReconnectWarnsAndStillConnectsWhenLocalNetworkAccessIsMissing() = runTest {
        // Android 17 gates the socket: without ACCESS_LOCAL_NETWORK a local TCP connect times out silently. The
        // service has no Activity to request from, so the contract is warn-and-proceed — proceed because the address
        // prefix says nothing about locality (public-IP/VPN radios need no permission) and because connect() is the
        // sole installer of the transport-recovery listeners.
        //
        // Pre-warm the resource: the first read completes on compose-resources' own Dispatchers.Default scope, so an
        // un-warmed lookup lands after the assertions (see ScannerViewModelTest's warmScanFailureStrings). The same
        // safeCatchingAll-with-fallback shape as production keeps the expected text identical either way.
        val expectedMessage = safeCatchingAll {
            getStringSuspend(Res.string.local_network_permission_denied_hint)
        }
            .getOrDefault(UNTRANSLATED_LOCAL_NETWORK_PERMISSION_DENIED_HINT)
        val orchestrator =
            createOrchestrator(
                currentDeviceAddressFlow = MutableStateFlow<String?>("t192.168.1.100"),
                localNetworkAccess = LocalNetworkAccess { false },
            )

        orchestrator.start()

        verify { serviceRepository.setErrorMessage(expectedMessage, Severity.Warn) }
        verify { radioInterfaceService.connect() }

        orchestrator.stop()
    }

    @Test
    fun tcpReconnectConnectsWithoutAWarningWhenLocalNetworkAccessIsHeld() {
        val orchestrator =
            createOrchestrator(
                currentDeviceAddressFlow = MutableStateFlow<String?>("t192.168.1.100"),
                localNetworkAccess = LocalNetworkAccess { true },
            )

        orchestrator.start()

        verify { radioInterfaceService.connect() }
        verify(exactly(0)) { serviceRepository.setErrorMessage(any(), any()) }

        orchestrator.stop()
    }

    @Test
    fun nonTcpReconnectIsUnaffectedByLocalNetworkAccess() {
        // Guards against over-gating: ACCESS_LOCAL_NETWORK has nothing to do with a BLE radio, so a denied grant
        // must neither warn nor keep a Bluetooth device from reconnecting.
        val orchestrator =
            createOrchestrator(
                currentDeviceAddressFlow = MutableStateFlow<String?>(DEFAULT_ADDRESS),
                localNetworkAccess = LocalNetworkAccess { false },
            )

        orchestrator.start()

        verify { radioInterfaceService.connect() }
        verify(exactly(0)) { serviceRepository.setErrorMessage(any(), any()) }

        orchestrator.stop()
    }
}
