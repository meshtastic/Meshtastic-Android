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
import dev.mokkery.verify.VerifyMode.Companion.atLeast
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ReceivedRadioFrame
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.TakPrefs
import org.meshtastic.core.takserver.TAKMeshIntegration
import org.meshtastic.core.takserver.TAKServerManager
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MyNodeInfo
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

    private val messageProcessor: MeshMessageProcessor = mock(MockMode.autofill)
    private val commandSender: CommandSender = mock(MockMode.autofill)
    private val meshConfigHandler: MeshConfigHandler = mock(MockMode.autofill)
    private val serviceNotifications: MeshNotificationManager = mock(MockMode.autofill)
    private val takServerManager: TAKServerManager = mock(MockMode.autofill)
    private val takPrefs: TakPrefs = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val connectionManager: MeshConnectionManager = mock(MockMode.autofill)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    /** Stubs the shared flow dependencies used by every test and returns an orchestrator. */
    private fun createOrchestrator(
        receivedData: MutableSharedFlow<ReceivedRadioFrame> = MutableSharedFlow(),
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
    ): MeshServiceOrchestrator {
        every { radioInterfaceService.receivedData } returns receivedData
        every { radioInterfaceService.connectionError } returns connectionError
        every { radioInterfaceService.connectionState } returns connectionState
        every { radioInterfaceService.currentDeviceAddressFlow } returns currentDeviceAddressFlow
        every { radioInterfaceService.sessionGeneration } returns sessionGeneration
        every { radioInterfaceService.activeSession } returns activeSession
        every { radioInterfaceService.isSessionActive(any()) } calls
            {
                isSessionActive(it.args[0] as RadioSessionContext)
            }
        every { serviceRepository.meshPacketFlow } returns MutableSharedFlow()
        every { meshConfigHandler.moduleConfig } returns MutableStateFlow(LocalModuleConfig())
        every { takPrefs.isTakServerEnabled } returns takEnabledFlow
        every { takServerManager.isRunning } returns takRunningFlow
        every { takServerManager.inboundMessages } returns MutableSharedFlow()
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        val takMeshIntegration =
            TAKMeshIntegration(
                takServerManager = takServerManager,
                commandSender = commandSender,
                serviceRepository = serviceRepository,
                meshConfigHandler = meshConfigHandler,
                nodeRepository = nodeRepository,
            )

        return MeshServiceOrchestrator(
            radioInterfaceService = radioInterfaceService,
            serviceStateWriter = serviceRepository,
            nodeManager = nodeManager,
            messageProcessor = messageProcessor,
            serviceNotifications = serviceNotifications,
            takServerManager = takServerManager,
            takMeshIntegration = takMeshIntegration,
            takPrefs = takPrefs,
            databaseManager = databaseManager,
            connectionManager = connectionManager,
            dispatchers = dispatchers,
        )
    }

    private fun frame(
        bytes: ByteArray,
        session: RadioSessionContext = RadioSessionContext(generation = 0L, address = DEFAULT_ADDRESS),
    ) = ReceivedRadioFrame(bytes.toByteString(), session)

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
        verify { takServerManager.start(any()) }

        // Update mock state to reflect it's running
        takRunningFlow.value = true

        // Toggle off
        takEnabledFlow.value = false
        verify { takServerManager.stop() }

        orchestrator.stop()
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
        // other calls on the same mocks (resetReceivedBuffer, currentDeviceAddressFlow
        // collectors, the mid-session DB switch replay, etc.) that interleave with the
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
     * Regression test for a bug where `stop()` did not actually tear down the FromRadio collectors. Collectors were
     * attached to an injected process-wide ServiceScope rather than a per-start scope, so `start() -> stop() ->
     * start()` caused duplicate collectors and every FromRadio packet was handled 2x (then 3x, etc.).
     */
    @Test
    fun testFromRadioCollectorsTornDownOnStopAndRestartedCleanlyOnStart() {
        val receivedData = MutableSharedFlow<ReceivedRadioFrame>(extraBufferCapacity = 8)
        val orchestrator = createOrchestrator(receivedData = receivedData)
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        orchestrator.start()
        val packet1 = frame(byteArrayOf(1, 2, 3))
        receivedData.tryEmit(packet1)
        verifySuspend(exactly(1)) { messageProcessor.handleFromRadio(packet1, null) }

        orchestrator.stop()
        val packet2 = frame(byteArrayOf(4, 5, 6))
        receivedData.tryEmit(packet2)
        // After stop(), the collector must be gone - the handler should not be invoked for packet2.
        verifySuspend(exactly(0)) { messageProcessor.handleFromRadio(packet2, null) }

        orchestrator.start()
        val packet3 = frame(byteArrayOf(7, 8, 9))
        receivedData.tryEmit(packet3)
        // After restart, a single fresh collector must process packet3 exactly once (not twice).
        verifySuspend(exactly(1)) { messageProcessor.handleFromRadio(packet3, null) }

        orchestrator.stop()
    }

    @Test
    fun frameIsDiscardedAfterAdmissionClosesEvenWhileLifecycleTokenIsDraining() {
        val receivedData = MutableSharedFlow<ReceivedRadioFrame>(extraBufferCapacity = 1)
        val session = RadioSessionContext(generation = 1L, address = DEFAULT_ADDRESS)
        val activeSession = MutableStateFlow<RadioSessionContext?>(session)
        var admissionOpen = true
        val orchestrator =
            createOrchestrator(
                receivedData = receivedData,
                sessionGeneration = MutableStateFlow(1L),
                activeSession = activeSession,
                isSessionActive = { admissionOpen && activeSession.value == it },
            )
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)
        val frame = frame(byteArrayOf(1, 2, 3), session)

        orchestrator.start()
        admissionOpen = false
        receivedData.tryEmit(frame)

        verifySuspend(exactly(0)) { messageProcessor.handleFromRadio(frame, null) }
        assertEquals(session, activeSession.value, "the lifecycle token may remain published while leases drain")

        orchestrator.stop()
    }

    @Test
    fun queuedMyNodeInfoFromOldSessionIsDiscardedAfterSameAddressReconnect() {
        val receivedData = MutableSharedFlow<ReceivedRadioFrame>(extraBufferCapacity = 2)
        val activeGeneration = MutableStateFlow(1L)
        val activeSession =
            MutableStateFlow<RadioSessionContext?>(RadioSessionContext(generation = 1L, address = DEFAULT_ADDRESS))
        val orchestrator =
            createOrchestrator(
                receivedData = receivedData,
                sessionGeneration = activeGeneration,
                activeSession = activeSession,
            )
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)
        val payload = FromRadio(my_info = MyNodeInfo(my_node_num = 42)).encode()
        val staleFrame = frame(payload, RadioSessionContext(generation = 1L, address = DEFAULT_ADDRESS))
        val freshFrame = frame(payload, RadioSessionContext(generation = 2L, address = DEFAULT_ADDRESS))

        orchestrator.start()
        activeGeneration.value = 2L
        activeSession.value = RadioSessionContext(generation = 2L, address = DEFAULT_ADDRESS)
        receivedData.tryEmit(staleFrame)
        verifySuspend(exactly(0)) { messageProcessor.handleFromRadio(staleFrame, null) }

        receivedData.tryEmit(freshFrame)
        verifySuspend(exactly(1)) { messageProcessor.handleFromRadio(freshFrame, null) }

        orchestrator.stop()
    }

    /**
     * Regression test for a channel-buffer-replay bug: the production [RadioInterfaceService] buffers inbound bytes in
     * a process-lifetime `Channel(UNLIMITED)`. Between `stop()` and the next `start()`, any bytes that arrive sit in
     * the channel and would be replayed to the fresh collector — prepending stale packets to the next session's
     * firmware handshake. `start()` must call [RadioInterfaceService.resetReceivedBuffer] before attaching the
     * collector.
     */
    @Test
    fun testStartDrainsReceivedBufferBeforeAttachingCollector() {
        val orchestrator = createOrchestrator()
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        orchestrator.start()
        orchestrator.stop()
        orchestrator.start()

        // resetReceivedBuffer must be invoked at least once per start() (twice total for two starts).
        verify(atLeast(2)) { radioInterfaceService.resetReceivedBuffer() }

        orchestrator.stop()
    }

    /** Additional regression: after many start/stop cycles, collectors must not accumulate. */
    @Test
    fun testRepeatedStartStopDoesNotAccumulateCollectors() {
        val receivedData = MutableSharedFlow<ReceivedRadioFrame>(extraBufferCapacity = 8)
        val orchestrator = createOrchestrator(receivedData = receivedData)
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        repeat(5) {
            orchestrator.start()
            orchestrator.stop()
        }

        orchestrator.start()
        val packet = frame(byteArrayOf(42))
        receivedData.tryEmit(packet)

        // Despite six total start() calls, only the most recent collector is live.
        verifySuspend(exactly(1)) { messageProcessor.handleFromRadio(packet, null) }

        orchestrator.stop()
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
     * unguarded observer launched in `init {}` would resurrect the orchestrator after a deliberate stop — collecting
     * from [RadioInterfaceService.receivedData] in the background with no foreground service, no wake lock, and no UI.
     *
     * This also preserves the documented invariant that [MeshConnectionManagerImpl] is the only consumer of
     * [RadioInterfaceService.connectionState]. Recovery after stop is the host's responsibility: it must call `start()`
     * explicitly (e.g. via a fresh `MeshService.onCreate()`).
     *
     * Replaces the previous "orphan-Connected recovery" behavior that was removed for violating both invariants.
     */
    @Test
    fun testConnectedWhileStoppedDoesNotRestartWithoutExplicitStart() {
        val receivedData = MutableSharedFlow<ReceivedRadioFrame>(extraBufferCapacity = 8)
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val orchestrator = createOrchestrator(receivedData = receivedData, connectionState = connectionState)
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        // Stopped; never call start() explicitly.
        assertFalse(orchestrator.isRunning)

        // Transport reaches Connected — orchestrator must stay stopped.
        connectionState.value = ConnectionState.Connected
        assertFalse(orchestrator.isRunning)

        // No collector may have been attached: a packet emitted now is unhandled.
        val packet = frame(byteArrayOf(7, 7, 7))
        receivedData.tryEmit(packet)
        verifySuspend(exactly(0)) { messageProcessor.handleFromRadio(packet, null) }

        // Likewise, subsequent transport state cycles must not restart the orchestrator.
        connectionState.value = ConnectionState.Disconnected
        connectionState.value = ConnectionState.Connected
        assertFalse(orchestrator.isRunning)
    }
}
