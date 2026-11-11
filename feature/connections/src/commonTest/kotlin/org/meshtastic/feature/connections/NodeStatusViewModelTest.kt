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
package org.meshtastic.feature.connections

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.network.radio.NodeStatusRepository
import org.meshtastic.node.MeshLogger
import org.meshtastic.node.NodeIdentityStore
import org.meshtastic.node.NodeSettingsStore
import org.meshtastic.node.NodeStores
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.EmbeddedNode
import org.meshtastic.node.host.HostCapabilities
import org.meshtastic.node.host.HostState
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.host.NodeStatus
import org.meshtastic.node.host.Requirement
import org.meshtastic.node.loadOrCreate
import org.meshtastic.node.transport.Bearer
import org.meshtastic.node.transport.ExperimentalTransportApi
import org.meshtastic.node.transport.MeshTransport
import org.meshtastic.proto.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NodeStatusViewModelTest {

    private class FakeRepository : NodeStatusRepository {
        override val status = MutableStateFlow<NodeStatus?>(null)
        val hostBearerWrites = mutableListOf<Pair<Bearer, Boolean>>()
        var refreshes = 0
        var usbRequests = 0

        override suspend fun setHostBearer(bearer: Bearer, on: Boolean) {
            hostBearerWrites += bearer to on
        }

        override fun refreshRequirements() {
            refreshes++
        }

        override fun requestUsbPermission() {
            usbRequests++
        }
    }

    /** A host with no bearers: enough for the node to run and report its status. */
    @OptIn(ExperimentalTransportApi::class)
    private class NoBearers : BearerFactory {
        override val hostCapabilities = HostCapabilities(hasWifi = false, hasBluetooth = false)
        override val requirements: StateFlow<Map<Bearer, Requirement>> = MutableStateFlow(emptyMap())

        override fun build(lora: Config.LoRaConfig?, logger: MeshLogger): List<MeshTransport> = emptyList()
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()

    private fun viewModel() = NodeStatusViewModel(repository)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `status mirrors the repository and is null without a node`() = runTest(dispatcher) {
        // The node's loops never complete, so it runs in a scope of its own on the test scheduler.
        val nodeScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            val identity = NodeIdentityStore.inMemory().also { it.loadOrCreate() }
            val stores = NodeStores(identity, NodeSettingsStore.inMemory())
            val node = EmbeddedNode(nodeScope, stores, NoBearers(), MutableStateFlow(HostState()))
            val running: NodeStatus = node.status.first { it.phase == NodePhase.Running }

            val viewModel = viewModel()
            viewModel.status.test {
                assertNull(awaitItem())
                repository.status.value = running
                val ui = awaitItem()
                assertEquals(NodePhase.Running, ui?.phase)
                assertEquals(running.longName, ui?.longName)
                assertEquals(NODE_ID_LENGTH, ui?.nodeId?.length)
                repository.status.value = null
                assertNull(awaitItem())
            }
            node.close()
        } finally {
            nodeScope.cancel()
        }
    }

    @Test
    fun `actions reach the repository`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setBearer(Bearer.AUDIO, true)
        viewModel.refreshRequirements()
        viewModel.requestUsbPermission()
        assertEquals(listOf(Bearer.AUDIO to true), repository.hostBearerWrites)
        assertEquals(1, repository.refreshes)
        assertEquals(1, repository.usbRequests)
    }

    @Test
    fun `every bearer with a transport entry is switched in the node`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setBearer(Bearer.UDP, false)
        viewModel.setBearer(Bearer.GATT, false)
        viewModel.setBearer(Bearer.LORA, true)
        assertEquals(
            listOf(Bearer.UDP to false, Bearer.GATT to false, Bearer.LORA to true),
            repository.hostBearerWrites,
        )
    }

    @Test
    fun `MQTT switches nothing`() = runTest(dispatcher) {
        viewModel().setBearer(Bearer.MQTT, true)
        assertTrue(repository.hostBearerWrites.isEmpty())
    }

    private companion object {
        /** `!` and eight hex digits, firmware's node id. */
        const val NODE_ID_LENGTH = 9
    }
}
