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
import dev.mokkery.answering.returns
import dev.mokkery.every
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
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshActionHandler
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.TakPrefs
import org.meshtastic.core.takserver.TAKMeshIntegration
import org.meshtastic.core.takserver.TAKServerManager
import org.meshtastic.core.takserver.fountain.CoTHandler
import org.meshtastic.proto.LocalModuleConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshServiceOrchestratorTest {

    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val messageProcessor: MeshMessageProcessor = mock(MockMode.autofill)
    private val commandSender: CommandSender = mock(MockMode.autofill)
    private val router: MeshRouter = mock(MockMode.autofill)
    private val actionHandler: MeshActionHandler = mock(MockMode.autofill)
    private val meshConfigHandler: MeshConfigHandler = mock(MockMode.autofill)
    private val serviceNotifications: MeshServiceNotifications = mock(MockMode.autofill)
    private val takServerManager: TAKServerManager = mock(MockMode.autofill)
    private val takPrefs: TakPrefs = mock(MockMode.autofill)
    private val cotHandler: CoTHandler = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val connectionManager: MeshConnectionManager = mock(MockMode.autofill)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    /** Stubs the shared flow dependencies used by every test and returns an orchestrator. */
    private fun createOrchestrator(
        receivedData: MutableSharedFlow<ByteArray> = MutableSharedFlow(),
        connectionError: MutableSharedFlow<String> = MutableSharedFlow(),
        serviceAction: MutableSharedFlow<ServiceAction> = MutableSharedFlow(),
        takEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        takRunningFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): MeshServiceOrchestrator {
        every { radioInterfaceService.receivedData } returns receivedData
        every { radioInterfaceService.connectionError } returns connectionError
        every { serviceRepository.serviceAction } returns serviceAction
        every { serviceRepository.meshPacketFlow } returns MutableSharedFlow()
        every { meshConfigHandler.moduleConfig } returns MutableStateFlow(LocalModuleConfig())
        every { takPrefs.isTakServerEnabled } returns takEnabledFlow
        every { takServerManager.isRunning } returns takRunningFlow
        every { takServerManager.inboundMessages } returns MutableSharedFlow()
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { router.actionHandler } returns actionHandler

        val takMeshIntegration =
            TAKMeshIntegration(
                takServerManager = takServerManager,
                commandSender = commandSender,
                nodeRepository = nodeRepository,
                serviceRepository = serviceRepository,
                meshConfigHandler = meshConfigHandler,
                cotHandler = cotHandler,
            )

        return MeshServiceOrchestrator(
            radioInterfaceService = radioInterfaceService,
            serviceRepository = serviceRepository,
            nodeManager = nodeManager,
            messageProcessor = messageProcessor,
            router = router,
            serviceNotifications = serviceNotifications,
            takServerManager = takServerManager,
            takMeshIntegration = takMeshIntegration,
            takPrefs = takPrefs,
            databaseManager = databaseManager,
            connectionManager = connectionManager,
            dispatchers = dispatchers,
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
        every { radioInterfaceService.getDeviceAddress() } returns "tcp:192.168.1.100"

        val orchestrator = createOrchestrator()
        orchestrator.start()

        verifySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.100") }
        verify { radioInterfaceService.connect() }

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
    fun testServiceActionDispatchedToActionHandler() {
        val serviceAction = MutableSharedFlow<ServiceAction>(extraBufferCapacity = 1)

        val orchestrator = createOrchestrator(serviceAction = serviceAction)
        orchestrator.start()

        val action = ServiceAction.Favorite(Node(num = 42))
        serviceAction.tryEmit(action)

        verifySuspend { actionHandler.onServiceAction(action) }

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
        val receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        val orchestrator = createOrchestrator(receivedData = receivedData)
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        orchestrator.start()
        val packet1 = byteArrayOf(1, 2, 3)
        receivedData.tryEmit(packet1)
        verifySuspend(exactly(1)) { messageProcessor.handleFromRadio(packet1, null) }

        orchestrator.stop()
        val packet2 = byteArrayOf(4, 5, 6)
        receivedData.tryEmit(packet2)
        // After stop(), the collector must be gone - the handler should not be invoked for packet2.
        verifySuspend(exactly(0)) { messageProcessor.handleFromRadio(packet2, null) }

        orchestrator.start()
        val packet3 = byteArrayOf(7, 8, 9)
        receivedData.tryEmit(packet3)
        // After restart, a single fresh collector must process packet3 exactly once (not twice).
        verifySuspend(exactly(1)) { messageProcessor.handleFromRadio(packet3, null) }

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
        val receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        val orchestrator = createOrchestrator(receivedData = receivedData)
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        repeat(5) {
            orchestrator.start()
            orchestrator.stop()
        }

        orchestrator.start()
        val packet = byteArrayOf(42)
        receivedData.tryEmit(packet)

        // Despite six total start() calls, only the most recent collector is live.
        verifySuspend(exactly(1)) { messageProcessor.handleFromRadio(packet, null) }

        orchestrator.stop()
    }
}
