package org.meshtastic.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meshtastic.core.repository.*

class MeshServiceOrchestratorTest {

    @Test
    fun testStartWiresComponents() {
        val radioInterfaceService = mockk<RadioInterfaceService>(relaxed = true)
        val serviceRepository = mockk<ServiceRepository>(relaxed = true)
        val packetHandler = mockk<PacketHandler>(relaxed = true)
        val nodeManager = mockk<NodeManager>(relaxed = true)
        val messageProcessor = mockk<MeshMessageProcessor>(relaxed = true)
        val commandSender = mockk<CommandSender>(relaxed = true)
        val connectionManager = mockk<MeshConnectionManager>(relaxed = true)
        val router = mockk<MeshRouter>(relaxed = true)
        val serviceNotifications = mockk<MeshServiceNotifications>(relaxed = true)
        
        every { radioInterfaceService.receivedData } returns MutableSharedFlow()
        every { serviceRepository.serviceAction } returns MutableSharedFlow()

        val orchestrator = MeshServiceOrchestrator(
            radioInterfaceService,
            serviceRepository,
            packetHandler,
            nodeManager,
            messageProcessor,
            commandSender,
            connectionManager,
            router,
            serviceNotifications
        )

        assertFalse(orchestrator.isRunning)
        orchestrator.start()
        assertTrue(orchestrator.isRunning)
        
        verify { serviceNotifications.initChannels() }
        verify { packetHandler.start(any()) }
        verify { nodeManager.loadCachedNodeDB() }
        
        orchestrator.stop()
        assertFalse(orchestrator.isRunning)
    }
}
