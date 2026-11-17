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

import androidx.compose.material3.SnackbarDuration
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
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.node_extra_radio_not_kept
import org.meshtastic.core.resources.node_extra_radio_switch_failed
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.connections.model.SensorsUi
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
import org.meshtastic.node.sensors.I2cBusState
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
        override val sensors = MutableStateFlow<I2cBusState?>(null)
        val hostBearerWrites = mutableListOf<Pair<Bearer, Boolean>>()
        var refreshes = 0
        var usbRequests = 0

        override val extraRadiosOff = MutableStateFlow<Set<String>>(emptySet())
        val extraRadioWrites = mutableListOf<Pair<String, Boolean>>()
        var extraRadioFailure: Exception? = null

        override suspend fun setHostBearer(bearer: Bearer, on: Boolean) {
            hostBearerWrites += bearer to on
        }

        override suspend fun setExtraRadio(name: String, on: Boolean) {
            extraRadioWrites += name to on
            extraRadioFailure?.let { throw it }
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

    private class RecordingSnackbarManager : SnackbarManager() {
        val messages = mutableListOf<String>()

        override fun showSnackbar(
            message: String,
            actionLabel: String?,
            withDismissAction: Boolean,
            duration: SnackbarDuration,
            onAction: (() -> Unit)?,
        ) {
            messages += message
        }
    }

    private val snackbars = RecordingSnackbarManager()
    private val resolved = mutableListOf<UiText>()

    private fun viewModel() = NodeStatusViewModel(repository, snackbars) { text ->
        resolved += text
        RESOLVED
    }

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
                assertNull(ui?.sensors, "a host with no bus has no sensors section")
                repository.sensors.value = I2cBusState.Scanning
                assertEquals(SensorsUi.Scanning, awaitItem()?.sensors)
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

    @Test
    fun `an extra radio switch reaches the repository and says nothing when it is kept`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setExtraRadio("lora-2", false)
        viewModel.setExtraRadio("lora-3", true)
        assertEquals(listOf("lora-2" to false, "lora-3" to true), repository.extraRadioWrites)
        assertTrue(snackbars.messages.isEmpty())
    }

    @Test
    fun `a switch the node could not keep tells the user a restart will forget it`() = runTest(dispatcher) {
        repository.extraRadioFailure = IllegalStateException("the choice for 'lora-2' was not kept")
        viewModel().setExtraRadio("lora-2", false)
        assertEquals(listOf("lora-2" to false), repository.extraRadioWrites)
        assertEquals(listOf<UiText>(UiText.Resource(Res.string.node_extra_radio_not_kept, 2)), resolved)
        assertEquals(listOf(RESOLVED), snackbars.messages)
    }

    @Test
    fun `a radio the host does not have is reported as a failed switch`() = runTest(dispatcher) {
        repository.extraRadioFailure = IllegalArgumentException("this host has no extra radio 'lora-9'")
        viewModel().setExtraRadio("lora-9", false)
        assertEquals(listOf("lora-9" to false), repository.extraRadioWrites)
        assertEquals(listOf<UiText>(UiText.Resource(Res.string.node_extra_radio_switch_failed)), resolved)
        assertEquals(listOf(RESOLVED), snackbars.messages)
    }

    private companion object {
        const val RESOLVED = "resolved"

        /** `!` and eight hex digits, firmware's node id. */
        const val NODE_ID_LENGTH = 9
    }
}
