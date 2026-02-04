/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.QueueStatus

class FromRadioPacketHandlerTest {
    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private val router: MeshRouter = mockk(relaxed = true)
    private val mqttManager: MeshMqttManager = mockk(relaxed = true)
    private val packetHandler: PacketHandler = mockk(relaxed = true)
    private val serviceNotifications: MeshServiceNotifications = mockk(relaxed = true)

    private lateinit var handler: FromRadioPacketHandler

    @Before
    fun setup() {
        handler = FromRadioPacketHandler(serviceRepository, router, mqttManager, packetHandler, serviceNotifications)
    }

    @Test
    fun `handleFromRadio routes MY_INFO to configFlowManager`() {
        val myInfo = MyNodeInfo(my_node_num = 1234)
        val proto = FromRadio(my_info = myInfo)

        handler.handleFromRadio(proto)

        verify { router.configFlowManager.handleMyInfo(myInfo) }
    }

    @Test
    fun `handleFromRadio routes METADATA to configFlowManager`() {
        val metadata = DeviceMetadata(firmware_version = "v1.0")
        val proto = FromRadio(metadata = metadata)

        handler.handleFromRadio(proto)

        verify { router.configFlowManager.handleLocalMetadata(metadata) }
    }

    @Test
    fun `handleFromRadio routes NODE_INFO to configFlowManager and updates status`() {
        val nodeInfo = NodeInfo(num = 1234)
        val proto = FromRadio(node_info = nodeInfo)

        handler.handleFromRadio(proto)

        verify { router.configFlowManager.handleNodeInfo(nodeInfo) }
        verify { serviceRepository.setStatusMessage(any()) }
    }

    @Test
    fun `handleFromRadio routes CONFIG_COMPLETE_ID to configFlowManager`() {
        val nonce = 69420
        val proto = FromRadio(config_complete_id = nonce)

        handler.handleFromRadio(proto)

        verify { router.configFlowManager.handleConfigComplete(nonce) }
    }

    @Test
    fun `handleFromRadio routes QUEUESTATUS to packetHandler`() {
        val queueStatus = QueueStatus(free = 10)
        val proto = FromRadio(queueStatus = queueStatus)

        handler.handleFromRadio(proto)

        verify { packetHandler.handleQueueStatus(queueStatus) }
    }

    @Test
    fun `handleFromRadio routes CONFIG to configHandler`() {
        val config = Config(lora = Config.LoRaConfig(use_preset = true))
        val proto = FromRadio(config = config)

        handler.handleFromRadio(proto)

        verify { router.configHandler.handleDeviceConfig(config) }
    }

    @Test
    fun `handleFromRadio routes CLIENTNOTIFICATION to serviceRepository and notifications`() {
        val notification = ClientNotification(message = "test")
        val proto = FromRadio(clientNotification = notification)

        handler.handleFromRadio(proto)

        verify { serviceRepository.setClientNotification(notification) }
        verify { serviceNotifications.showClientNotification(notification) }
        verify { packetHandler.removeResponse(0, complete = false) }
    }
}
