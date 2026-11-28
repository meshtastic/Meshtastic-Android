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
import dev.mokkery.mock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SendLocalConfigTest {

    private val serviceRepository = ServiceRepositoryImpl().apply { setConnectionState(ConnectionState.Connected) }
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val nodeManager: NodeManager =
        mock<NodeManager>(MockMode.autofill).also {
            every { it.myNodeNum } returns MutableStateFlow(NODE_NUM)
            every { it.reportsHeardOnCurrentLora } returns MutableStateFlow(false)
        }
    private val transparentLora =
        Config.LoRaConfig.Builder()
            .also { wb ->
                wb.hop_limit = 5
                wb.tx_enabled = true
                wb.transparent_mode = true
            }
            .build()

    private lateinit var radio: FakeRadioInterfaceService
    private lateinit var transport: FakeRadioTransport

    /** An admin controller over one connected SDK client, which app session 1 owns. */
    private suspend fun TestScope.adminController(
        refresherConfig: RadioConfigRepository = radioConfigRepository,
    ): AdminControllerImpl {
        transport =
            FakeRadioTransport(
                identity = TransportIdentity("fake:local-config"),
                autoHandshake = true,
                autoQueueStatus = true,
                nodeNum = NODE_NUM,
            )
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .autoSyncTimeOnConnect(false)
                .coroutineContext(backgroundScope.coroutineContext)
                .build()
        radio = FakeRadioInterfaceService(backgroundScope).apply { this.client = client }
        radio.setDeviceAddress(ADDRESS)
        radio.connect()
        client.connect()
        runCurrent()
        return AdminControllerImpl(
            nodeManager = nodeManager,
            radio = radio,
            sessionAdmin = SessionAdmin(radio, backgroundScope.asServiceScope()),
            heardOnCurrentLoraRefresher =
            HeardOnCurrentLoraRefresher(
                nodeManager = nodeManager,
                meshConfigFlowManager = lazy { mock<MeshConfigFlowManager>(MockMode.autofill) },
                serviceRepository = serviceRepository,
                radioConfigRepository = refresherConfig,
                radioOperationLock = RadioOperationLock(),
                scope = backgroundScope,
            ),
        )
    }

    private fun config(lora: Config.LoRaConfig) = Config.Builder().also { wb -> wb.lora = lora }.build()

    private fun session() = RadioSessionContext(1L, ADDRESS)

    private fun setConfigOf(packet: MeshPacket): Config? = packet.decoded
        ?.takeIf { it.portnum == PortNum.ADMIN_APP }
        ?.let { AdminMessage.ADAPTER.decode(it.payload).set_config }

    @Test
    fun `a local config write goes out on its session and returns the answer of the node`() = runTest {
        val admin = adminController()
        val sent = config(transparentLora)

        val result = async { admin.setLocalConfigOn(session(), sent) }
        runCurrent()
        val packet = transport.outboundPackets().single { setConfigOf(it) != null }
        transport.injectRoutingAck(packet.id)
        runCurrent()

        assertEquals(NODE_NUM, packet.to)
        assertEquals(sent, setConfigOf(packet))
        assertEquals(AdminResult.Success(Unit), result.await())
    }

    @Test
    fun `a write for a session that no longer owns the radio sends nothing`() = runTest {
        val admin = adminController()
        val readOn = session()
        radio.disconnect()
        radio.connect()

        assertEquals(AdminResult.NodeUnreachable, admin.setLocalConfigOn(readOn, config(transparentLora)))
        runCurrent()

        assertTrue(transport.outboundPackets().none { setConfigOf(it) != null })
    }

    @Test
    fun `a session replaced while the write reads the cached LoRa config sends nothing`() = runTest {
        val reconnectingConfig =
            object : RadioConfigRepository by radioConfigRepository {
                override val localConfigFlow: Flow<LocalConfig> = flow {
                    radio.disconnect()
                    radio.connect()
                    emitAll(radioConfigRepository.localConfigFlow)
                }
            }
        val admin = adminController(refresherConfig = reconnectingConfig)

        assertEquals(AdminResult.NodeUnreachable, admin.setLocalConfigOn(session(), config(transparentLora)))
        runCurrent()

        assertNotEquals(session(), radio.activeSession.value)
        assertTrue(transport.outboundPackets().none { setConfigOf(it) != null })
    }

    private companion object {
        const val NODE_NUM = 0x1234
        const val ADDRESS = "x12:34"
    }
}
