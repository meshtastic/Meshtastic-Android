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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.TransparentModeController
import org.meshtastic.core.testing.FakeCommandSender
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SendLocalConfigTest {

    private val commandSender = FakeCommandSender()
    private val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val myNodeNum = MutableStateFlow<Int?>(NODE_NUM)
    private val connectionIdentity =
        MutableStateFlow<ConnectionIdentity?>(ConnectionIdentity(1L, ADDRESS, NODE_NUM, null))
    private val nodeManager: NodeManager =
        mock<NodeManager>(MockMode.autofill).also {
            every { it.myNodeNum } returns myNodeNum
            every { it.connectionIdentity } returns connectionIdentity
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

    private fun TestScope.adminController(
        sender: CommandSender = commandSender,
        refresherConfig: RadioConfigRepository = radioConfigRepository,
    ) = AdminControllerImpl(
        commandSender = sender,
        nodeManager = nodeManager,
        radioConfigRepository = radioConfigRepository,
        connectionStateProvider = serviceRepository,
        heardOnCurrentLoraRefresher =
        HeardOnCurrentLoraRefresher(
            nodeManager = nodeManager,
            meshConfigFlowManager = lazy { mock<MeshConfigFlowManager>(MockMode.autofill) },
            serviceRepository = serviceRepository,
            radioConfigRepository = refresherConfig,
            radioOperationLock = RadioOperationLock(),
            scope = backgroundScope,
        ),
        scope = backgroundScope,
    )

    private fun config(lora: Config.LoRaConfig) = Config.Builder().also { wb -> wb.lora = lora }.build()

    private fun version() = serviceRepository.connectionLifecycle.value.version

    private fun setConfigWrites() = commandSender.adminRequests.filter { it.message.set_config != null }

    @Test
    fun `sends set_config to the expected node on its connection without writing the cache`() = runTest {
        val sent = config(transparentLora.newBuilder().also { wb -> wb.transparent_mode = false }.build())

        adminController().sendLocalConfig(sent, expectedNodeNum = NODE_NUM, expectedConnectionVersion = version())
        runCurrent()

        val request = setConfigWrites().single()
        assertEquals(NODE_NUM, request.destNum)
        assertEquals(sent, request.message.set_config)
        assertEquals(version(), request.expectedConnectionVersion)
        assertNull(radioConfigRepository.lastSetLocalConfig)
    }

    @Test
    fun `a write for a node that is no longer local or a connection that moved on sends nothing`() = runTest {
        val controller = adminController()
        val readOn = version()
        myNodeNum.value = OTHER_NODE_NUM

        assertFailsWith<PacketQueueRejectedException> {
            controller.sendLocalConfig(config(transparentLora), NODE_NUM, readOn)
        }

        myNodeNum.value = NODE_NUM
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        serviceRepository.setConnectionState(ConnectionState.Connected)

        assertFailsWith<PacketQueueRejectedException> {
            controller.sendLocalConfig(config(transparentLora), NODE_NUM, readOn)
        }
        assertTrue(commandSender.adminRequests.isEmpty())
    }

    @Test
    fun `a reconnect after the session check but before queue admission admits nothing to the new connection`() =
        runTest {
            // Admits a write only on the lifecycle version it is bound to, as PacketHandlerImpl does.
            val admittingSender =
                object : CommandSender by commandSender {
                    override suspend fun sendAdminForConnection(
                        destNum: Int,
                        expectedConnectionVersion: Long,
                        requestId: Int,
                        wantResponse: Boolean,
                        initFn: () -> AdminMessage,
                    ) {
                        val lifecycle = serviceRepository.connectionLifecycle.value
                        if (
                            lifecycle.state != ConnectionState.Connected ||
                            lifecycle.version != expectedConnectionVersion
                        ) {
                            throw PacketQueueRejectedException("Admin command")
                        }
                        commandSender.sendAdminForConnection(
                            destNum,
                            expectedConnectionVersion,
                            requestId,
                            wantResponse,
                            initFn,
                        )
                    }
                }
            // The reconnect lands while the write awaits the cached LoRa config, after its up-front check passed.
            val reconnectingConfig =
                object : RadioConfigRepository by radioConfigRepository {
                    override val localConfigFlow: Flow<LocalConfig> = flow {
                        serviceRepository.setConnectionState(ConnectionState.Disconnected)
                        serviceRepository.setConnectionState(ConnectionState.Connected)
                        emitAll(radioConfigRepository.localConfigFlow)
                    }
                }
            val controller = adminController(sender = admittingSender, refresherConfig = reconnectingConfig)
            val readOn = version()

            assertFailsWith<PacketQueueRejectedException> {
                controller.sendLocalConfig(config(transparentLora), NODE_NUM, readOn)
            }
            assertTrue(readOn != version())
            assertTrue(setConfigWrites().isEmpty())
        }

    @Test
    fun `a radio switch after the turn-off read but before the session check sends nothing`() = runTest {
        val admin = adminController()
        val fakeRadio = FakeRadioController().apply { nextPacketId = FIRST_REQUEST_ID }
        var writesCalled = 0
        // The switch lands once the controller has checked its session and called the write.
        val radio =
            object : RadioController by fakeRadio {
                override suspend fun sendLocalConfig(
                    config: Config,
                    expectedNodeNum: Int,
                    expectedConnectionVersion: Long,
                ) {
                    writesCalled++
                    serviceRepository.setConnectionState(ConnectionState.Disconnected)
                    myNodeNum.value = OTHER_NODE_NUM
                    connectionIdentity.value = ConnectionIdentity(2L, ADDRESS, OTHER_NODE_NUM, null)
                    serviceRepository.setConnectionState(ConnectionState.Connected)
                    admin.sendLocalConfig(config, expectedNodeNum, expectedConnectionVersion)
                }
            }
        radioConfigRepository.setLocalConfigDirect(
            LocalConfig.Builder().also { wb -> wb.lora = transparentLora }.build(),
        )
        val controller =
            TransparentModeController(
                radioConfigRepository,
                serviceRepository,
                radio,
                nodeManager,
                CoroutineScope(StandardTestDispatcher(testScheduler)),
            )

        controller.turnOff()
        runCurrent()
        val firstRead = checkNotNull(fakeRadio.adminRequests.last { it.kind == "getConfig" }.packetId)
        serviceRepository.emitMeshPacket(loraResponse(transparentLora, firstRead))
        advanceUntilIdle()

        assertEquals(1, writesCalled)
        assertTrue(setConfigWrites().isEmpty())
    }

    private fun loraResponse(lora: Config.LoRaConfig, requestId: Int): MeshPacket {
        val admin = AdminMessage.Builder().also { wb -> wb.get_config_response = config(lora) }.build()
        return MeshPacket.Builder()
            .also { wb ->
                wb.from = NODE_NUM
                wb.decoded =
                    Data.Builder()
                        .also { db ->
                            db.portnum = PortNum.ADMIN_APP
                            db.request_id = requestId
                            db.payload = admin.encodeByteString()
                        }
                        .build()
            }
            .build()
    }

    private companion object {
        const val NODE_NUM = 0x1234
        const val OTHER_NODE_NUM = 0x5678
        const val FIRST_REQUEST_ID = 42
        const val ADDRESS = "x12:34"
    }
}
