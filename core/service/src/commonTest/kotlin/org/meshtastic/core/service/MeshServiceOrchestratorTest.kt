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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshServiceOrchestratorTest {

    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val packetHandler: PacketHandler = mock(MockMode.autofill)
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val messageProcessor: MeshMessageProcessor = mock(MockMode.autofill)
    private val commandSender: CommandSender = mock(MockMode.autofill)
    private val connectionManager: MeshConnectionManager = mock(MockMode.autofill)
    private val router: MeshRouter = mock(MockMode.autofill)
    private val serviceNotifications: MeshServiceNotifications = mock(MockMode.autofill)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)

    @Test
    fun testStartWiresComponents() {
        every { radioInterfaceService.receivedData } returns MutableSharedFlow()
        every { serviceRepository.serviceAction } returns MutableSharedFlow()

        val orchestrator =
            MeshServiceOrchestrator(
                radioInterfaceService = radioInterfaceService,
                serviceRepository = serviceRepository,
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                messageProcessor = messageProcessor,
                commandSender = commandSender,
                connectionManager = connectionManager,
                router = router,
                serviceNotifications = serviceNotifications,
                dispatchers = dispatchers,
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
