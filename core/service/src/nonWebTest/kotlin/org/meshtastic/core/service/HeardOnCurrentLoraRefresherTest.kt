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
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.FakeCommandSender
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.test.Test

class HeardOnCurrentLoraRefresherTest {

    private val commandSender = FakeCommandSender()
    private val serviceRepository = ServiceRepositoryImpl().apply { setConnectionState(ConnectionState.Connected) }
    private val meshConfigFlowManager: MeshConfigFlowManager = mock(MockMode.autofill)
    private val radioOperationLock = RadioOperationLock()
    private val reportsHeard = MutableStateFlow(true)
    private val nodeManager: NodeManager =
        mock<NodeManager>(MockMode.autofill).also {
            every { it.myNodeNum } returns MutableStateFlow(MY_NODE)
            every { it.reportsHeardOnCurrentLora } returns reportsHeard
        }
    private val radioConfigRepository: RadioConfigRepository =
        mock<RadioConfigRepository>(MockMode.autofill).also {
            every { it.localConfigFlow } returns flowOf(LocalConfig.Builder().also { wb -> wb.lora = homeLora }.build())
            every { it.channelSetFlow } returns
                flowOf(ChannelSet.Builder().also { wb -> wb.settings = listOf(settings(name = "Home")) }.build())
        }

    private fun TestScope.controller() = AdminControllerImpl(
        commandSender = commandSender,
        nodeManager = nodeManager,
        radioConfigRepository = radioConfigRepository,
        connectionStateProvider = serviceRepository,
        heardOnCurrentLoraRefresher =
        HeardOnCurrentLoraRefresher(
            nodeManager = nodeManager,
            meshConfigFlowManager = lazy { meshConfigFlowManager },
            serviceRepository = serviceRepository,
            radioConfigRepository = radioConfigRepository,
            radioOperationLock = radioOperationLock,
            scope = backgroundScope,
        ),
        scope = backgroundScope,
    )

    private fun lora(build: Config.LoRaConfig.Builder.() -> Unit) =
        Config.Builder().also { wb -> wb.lora = homeLora.newBuilder().apply(build).build() }.build()

    private val turbo = lora { modem_preset = Config.LoRaConfig.ModemPreset.LONG_TURBO }

    private fun settings(name: String, psk: Byte = 1) = ChannelSettings.Builder()
        .also { wb ->
            wb.name = name
            wb.psk = byteArrayOf(psk).toByteString()
        }
        .build()

    private fun channel(role: Channel.Role, settings: ChannelSettings) = Channel.Builder()
        .also { wb ->
            wb.role = role
            wb.settings = settings
        }
        .build()

    private suspend fun TestScope.ackLastAdmin() {
        serviceRepository.emitMeshPacket(routingResponse(commandSender.adminRequests.last().requestId))
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(30_000)
        runCurrent()
    }

    @Test
    fun `an acked local preset change that does not reboot refreshes the node info once`() = runTest {
        controller().setLocalConfig(turbo)
        runCurrent()
        ackLastAdmin()
        settle()

        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a LoRa save the radio never acks does not refresh`() = runTest {
        controller().setConfig(MY_NODE, turbo, packetId = 77)
        advanceTimeBy(10 * 60_000L)
        runCurrent()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a LoRa save followed by a reboot leaves the refresh to the next handshake`() = runTest {
        controller().setConfig(MY_NODE, turbo, packetId = 77)
        runCurrent()
        ackLastAdmin()
        advanceTimeBy(2_000)
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `firmware that does not report heard flags is never refreshed`() = runTest {
        reportsHeard.value = false
        controller().setConfig(MY_NODE, turbo, packetId = 77)
        runCurrent()
        ackLastAdmin()
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `saves that leave the slot unchanged do not refresh`() = runTest {
        val controller = controller()
        val device =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { d -> d.led_heartbeat_disabled = true }.build()
                }
                .build()
        for (config in listOf(device, lora { tx_power = 10 }, lora { bandwidth = 500 })) {
            controller.setConfig(MY_NODE, config, packetId = commandSender.generatePacketId())
            runCurrent()
            ackLastAdmin()
            settle()
        }

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a LoRa save to a remote node does not refresh`() = runTest {
        controller().setConfig(REMOTE_NODE, turbo, packetId = 77)
        runCurrent()
        ackLastAdmin()
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `only renaming the primary channel refreshes`() = runTest {
        val controller = controller()
        controller.setLocalChannel(channel(Channel.Role.SECONDARY, settings(name = "Away")))
        runCurrent()
        ackLastAdmin()
        controller.setLocalChannel(channel(Channel.Role.PRIMARY, settings(name = "Home", psk = 2)))
        runCurrent()
        ackLastAdmin()
        settle()
        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }

        controller.setLocalChannel(channel(Channel.Role.PRIMARY, settings(name = "Away")))
        runCurrent()
        ackLastAdmin()
        settle()
        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `back to back acked LoRa saves refresh once for the newest`() = runTest {
        val controller = controller()
        controller.setConfig(MY_NODE, turbo, packetId = 77)
        controller.setConfig(MY_NODE, lora { channel_num = 3 }, packetId = 78)
        runCurrent()
        serviceRepository.emitMeshPacket(routingResponse(77))
        serviceRepository.emitMeshPacket(routingResponse(78))
        runCurrent()
        settle()

        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a rejected later LoRa save does not cancel the refresh for an applied one`() = runTest {
        val controller = controller()
        controller.setConfig(MY_NODE, turbo, packetId = 77)
        runCurrent()
        serviceRepository.emitMeshPacket(routingResponse(77))
        runCurrent()
        controller.setConfig(MY_NODE, lora { channel_num = 3 }, packetId = 78)
        runCurrent()
        serviceRepository.emitMeshPacket(routingResponse(78, Routing.Error.BAD_REQUEST))
        runCurrent()
        settle()

        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `preset shifts during a discovery scan do not refresh`() = runTest {
        radioOperationLock.acquire(RadioOperation.DiscoveryScan)
        controller().setLocalConfig(turbo)
        runCurrent()
        ackLastAdmin()
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    private fun routingResponse(requestId: Int, error: Routing.Error = Routing.Error.NONE) = MeshPacket.Builder()
        .also { wb ->
            wb.from = MY_NODE
            wb.decoded =
                Data.Builder()
                    .also { d ->
                        d.portnum = PortNum.ROUTING_APP
                        d.request_id = requestId
                        d.payload =
                            Routing.Builder().also { r -> r.error_reason = error }.build().encode().toByteString()
                    }
                    .build()
        }
        .build()

    private companion object {
        const val MY_NODE = 1234
        const val REMOTE_NODE = 5678

        val homeLora: Config.LoRaConfig =
            Config.LoRaConfig.Builder()
                .also { wb ->
                    wb.region = Config.LoRaConfig.RegionCode.US
                    wb.use_preset = true
                    wb.modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST
                    wb.bandwidth = 250
                }
                .build()
    }
}
