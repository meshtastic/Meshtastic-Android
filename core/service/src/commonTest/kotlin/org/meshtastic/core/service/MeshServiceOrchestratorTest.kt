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

class MeshServiceOrchestratorTest {
    /*


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

        val orchestrator =
            MeshServiceOrchestrator(
                radioInterfaceService,
                serviceRepository,
                packetHandler,
                nodeManager,
                messageProcessor,
                commandSender,
                connectionManager,
                router,
                serviceNotifications,
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

     */
}
