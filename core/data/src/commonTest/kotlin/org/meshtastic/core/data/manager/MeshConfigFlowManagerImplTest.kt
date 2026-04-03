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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifyNoMoreCalls
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.ToRadio
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MeshConfigFlowManagerImplTest {

    private val connectionManager = mock<MeshConnectionManager>(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val serviceRepository = FakeServiceRepository()
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val serviceBroadcasts = mock<ServiceBroadcasts>(MockMode.autofill)
    private val analytics = mock<PlatformAnalytics>(MockMode.autofill)
    private val commandSender = mock<CommandSender>(MockMode.autofill)
    private val packetHandler = mock<PacketHandler>(MockMode.autofill)
    private val nodeManager = mock<NodeManager>(MockMode.autofill)

    // Tracks nodes installed via nodeManager.installNodeInfo so assertions can inspect them
    private val installedNodes: MutableMap<Int, Node> = mutableMapOf()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var manager: MeshConfigFlowManagerImpl

    @BeforeTest
    fun setUp() {
        every { connectionManager.startNodeInfoOnly() } returns Unit
        every { connectionManager.onRadioConfigLoaded() } returns Unit
        every { connectionManager.onNodeDbReady() } returns Unit
        every { packetHandler.sendToRadio(any<ToRadio>()) } returns Unit
        every { serviceBroadcasts.broadcastConnection() } returns Unit
        every { nodeManager.nodeDBbyNodeNum } returns installedNodes
        every { nodeManager.myNodeNum } returns null
        every { nodeManager.setNodeDbReady(any<Boolean>()) } returns Unit
        every { nodeManager.setAllowNodeDbWrites(any<Boolean>()) } returns Unit
        every { nodeManager.installNodeInfo(any<NodeInfo>(), any<Boolean>()) } returns Unit

        manager =
            MeshConfigFlowManagerImpl(
                nodeManager = nodeManager,
                connectionManager = lazy { connectionManager },
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfigRepository,
                serviceRepository = serviceRepository,
                serviceBroadcasts = serviceBroadcasts,
                analytics = analytics,
                commandSender = commandSender,
                packetHandler = packetHandler,
            )
    }

    // -------------------------------------------------------------------------
    // handleNodeInfo / handleNodeInfoBatch — accumulation
    // -------------------------------------------------------------------------

    @Test
    fun `handleNodeInfo accumulates nodes and increments newNodeCount`() {
        manager.handleNodeInfo(NodeInfo(num = 1))
        manager.handleNodeInfo(NodeInfo(num = 2))

        assertEquals(2, manager.newNodeCount)
    }

    @Test
    fun `handleNodeInfoBatch adds all items in one shot`() {
        val items = listOf(NodeInfo(num = 10), NodeInfo(num = 11), NodeInfo(num = 12))

        manager.handleNodeInfoBatch(items)

        assertEquals(3, manager.newNodeCount)
    }

    @Test
    fun `handleNodeInfoBatch with empty list leaves count at zero`() {
        manager.handleNodeInfoBatch(emptyList())

        assertEquals(0, manager.newNodeCount)
    }

    @Test
    fun `handleNodeInfoBatch with single item equals count of one`() {
        manager.handleNodeInfoBatch(listOf(NodeInfo(num = 7)))

        assertEquals(1, manager.newNodeCount)
    }

    @Test
    fun `handleNodeInfoBatch and handleNodeInfo accumulate together`() {
        manager.handleNodeInfo(NodeInfo(num = 1))
        manager.handleNodeInfoBatch(listOf(NodeInfo(num = 2), NodeInfo(num = 3)))

        assertEquals(3, manager.newNodeCount)
    }

    // -------------------------------------------------------------------------
    // handleConfigComplete — nonce routing
    // -------------------------------------------------------------------------

    @Test
    fun `handleConfigComplete with BATCH_NODE_INFO_NONCE triggers Stage 2 completion`() = runTest(testDispatcher) {
        manager.start(backgroundScope)
        manager.handleNodeInfoBatch(listOf(NodeInfo(num = 100)))
        // installNodeInfo is a no-op mock; seed the map so nodeDBbyNodeNum[num] is non-null
        installedNodes[100] = Node(num = 100)

        manager.handleConfigComplete(HandshakeConstants.BATCH_NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { connectionManager.onNodeDbReady() }
    }

    @Test
    fun `handleConfigComplete with NODE_INFO_NONCE (legacy) also triggers Stage 2 completion`() =
        runTest(testDispatcher) {
            manager.start(backgroundScope)
            manager.handleNodeInfo(NodeInfo(num = 200))
            installedNodes[200] = Node(num = 200)

            manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
            advanceUntilIdle()

            verify { connectionManager.onNodeDbReady() }
        }

    @Test
    fun `handleConfigComplete with unknown nonce takes no action`() = runTest(testDispatcher) {
        manager.start(backgroundScope)

        manager.handleConfigComplete(99999)
        advanceUntilIdle()

        verifyNoMoreCalls(connectionManager)
    }

    // -------------------------------------------------------------------------
    // handleConfigComplete Stage 2 — newNodes cleared after completion
    // -------------------------------------------------------------------------

    @Test
    fun `newNodeCount resets to zero after Stage 2 completion`() = runTest(testDispatcher) {
        manager.start(backgroundScope)
        manager.handleNodeInfoBatch(listOf(NodeInfo(num = 1), NodeInfo(num = 2)))
        installedNodes[1] = Node(num = 1)
        installedNodes[2] = Node(num = 2)
        assertEquals(2, manager.newNodeCount)

        manager.handleConfigComplete(HandshakeConstants.BATCH_NODE_INFO_NONCE)
        advanceUntilIdle()

        assertEquals(0, manager.newNodeCount)
    }

    // -------------------------------------------------------------------------
    // handleConfigComplete Stage 2 — empty batch edge case
    // -------------------------------------------------------------------------

    @Test
    fun `Stage 2 completion with empty batch signals readiness with no installed nodes`() = runTest(testDispatcher) {
        manager.start(backgroundScope)
        // Intentionally skip any handleNodeInfo* calls — simulates an empty NodeInfoBatch

        manager.handleConfigComplete(HandshakeConstants.BATCH_NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { connectionManager.onNodeDbReady() }
    }
}
