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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.network.radio.NodeStatusRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.node.host.Bearer
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.host.NodeStatus
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.NetworkConfig.ProtocolFlags
import org.meshtastic.proto.LocalConfig
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

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()
    private val radioController = FakeRadioController()
    private val radioConfigRepository = FakeRadioConfigRepository()

    private fun viewModel() = NodeStatusViewModel(repository, radioController, radioConfigRepository)

    private fun cacheNetwork(flags: Int, ntpServer: String = "") {
        val network =
            Config.NetworkConfig.Builder()
                .also { wb ->
                    wb.enabled_protocols = flags
                    wb.ntp_server = ntpServer
                }
                .build()
        radioConfigRepository.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.network = network }.build())
    }

    private val allFlags =
        ProtocolFlags.UDP_BROADCAST.value or ProtocolFlags.BLE_BROADCAST.value or ProtocolFlags.BLE_GATT_PEER.value

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
        val viewModel = viewModel()
        viewModel.status.test {
            assertNull(awaitItem())
            repository.status.value = NodeStatus(NodePhase.Running, 7L, "Phone", "PHON", emptyMap(), 2)
            val ui = awaitItem()
            assertEquals(NodePhase.Running, ui?.phase)
            assertEquals("!00000007", ui?.nodeId)
            repository.status.value = null
            assertNull(awaitItem())
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
        assertTrue(radioController.localConfigs.isEmpty())
    }

    @Test
    fun `a config bearer switch writes its enabled_protocols bit as the Network screen does`() = runTest(dispatcher) {
        cacheNetwork(allFlags, ntpServer = "pool.example")
        viewModel().setBearer(Bearer.UDP, false)
        val network = radioController.localConfigs.single().network
        assertEquals(
            ProtocolFlags.BLE_BROADCAST.value or ProtocolFlags.BLE_GATT_PEER.value,
            network?.enabled_protocols,
        )
        assertEquals("pool.example", network?.ntp_server)
        assertTrue(repository.hostBearerWrites.isEmpty())
    }

    @Test
    fun `quick switches build on the last write while the cache lags`() = runTest(dispatcher) {
        cacheNetwork(allFlags)
        val viewModel = viewModel()
        viewModel.setBearer(Bearer.UDP, false)
        viewModel.setBearer(Bearer.GATT, false)
        assertEquals(
            listOf(
                ProtocolFlags.BLE_BROADCAST.value or ProtocolFlags.BLE_GATT_PEER.value,
                ProtocolFlags.BLE_BROADCAST.value,
            ),
            radioController.localConfigs.map { it.network?.enabled_protocols },
        )
    }

    @Test
    fun `a changed cache wins over the last write`() = runTest(dispatcher) {
        cacheNetwork(allFlags)
        val viewModel = viewModel()
        viewModel.setBearer(Bearer.UDP, false)
        cacheNetwork(ProtocolFlags.UDP_BROADCAST.value)
        viewModel.setBearer(Bearer.BLE_ADV, true)
        assertEquals(
            ProtocolFlags.UDP_BROADCAST.value or ProtocolFlags.BLE_BROADCAST.value,
            radioController.localConfigs.last().network?.enabled_protocols,
        )
    }

    @Test
    fun `LoRa and MQTT switch nothing`() = runTest(dispatcher) {
        cacheNetwork(allFlags)
        val viewModel = viewModel()
        viewModel.setBearer(Bearer.LORA, false)
        viewModel.setBearer(Bearer.MQTT, true)
        assertTrue(radioController.localConfigs.isEmpty())
        assertTrue(repository.hostBearerWrites.isEmpty())
    }
}
