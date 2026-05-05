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
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MeshConnectionManager
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
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val serviceNotifications: MeshServiceNotifications = mock(MockMode.autofill)
    private val takServerManager: TAKServerManager = mock(MockMode.autofill)
    private val takPrefs: TakPrefs = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val connectionManager: MeshConnectionManager = mock(MockMode.autofill)

    // TAKMeshIntegration deps (final class — constructed directly)
    private val radioController: RadioController = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val meshConfigHandler: MeshConfigHandler = mock(MockMode.autofill)
    private val cotHandler: CoTHandler = mock(MockMode.autofill)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    private fun createOrchestrator(
        takEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        takRunningFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): MeshServiceOrchestrator {
        every { takPrefs.isTakServerEnabled } returns takEnabledFlow
        every { takServerManager.isRunning } returns takRunningFlow
        every { takServerManager.inboundMessages } returns MutableSharedFlow()
        every { meshConfigHandler.moduleConfig } returns MutableStateFlow(LocalModuleConfig())
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { serviceRepository.meshPacketFlow } returns MutableSharedFlow()

        val takMeshIntegration = TAKMeshIntegration(
            takServerManager = takServerManager,
            radioController = radioController,
            nodeRepository = nodeRepository,
            serviceRepository = serviceRepository,
            meshConfigHandler = meshConfigHandler,
            cotHandler = cotHandler,
        )

        return MeshServiceOrchestrator(
            radioInterfaceService = radioInterfaceService,
            nodeManager = nodeManager,
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
}
