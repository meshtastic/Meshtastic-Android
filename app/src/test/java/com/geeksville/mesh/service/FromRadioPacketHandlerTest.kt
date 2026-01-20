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
package com.geeksville.mesh.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.fromRadio

class FromRadioPacketHandlerTest {

    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private val router: MeshRouter = mockk(relaxed = true)
    private val mqttManager: MeshMqttManager = mockk(relaxed = true)
    private val packetHandler: PacketHandler = mockk(relaxed = true)
    private val serviceNotifications: MeshServiceNotifications = mockk(relaxed = true)
    private val configFlowManager: MeshConfigFlowManager = mockk(relaxed = true)
    private val configHandler: MeshConfigHandler = mockk(relaxed = true)

    private lateinit var handler: FromRadioPacketHandler

    @Before
    fun setUp() {
        every { router.configFlowManager } returns configFlowManager
        every { router.configHandler } returns configHandler
        handler = FromRadioPacketHandler(serviceRepository, router, mqttManager, packetHandler, serviceNotifications)
    }

    @Test
    fun `handleFromRadio routes MY_INFO to configFlowManager`() {
        val myInfo = MeshProtos.MyNodeInfo.newBuilder().setMyNodeNum(1234).build()
        val proto = fromRadio { this.myInfo = myInfo }

        handler.handleFromRadio(proto)

        verify { configFlowManager.handleMyInfo(myInfo) }
    }

    @Test
    fun `handleFromRadio routes METADATA to configFlowManager`() {
        val metadata = MeshProtos.DeviceMetadata.newBuilder().setFirmwareVersion("v1.0").build()
        val proto = fromRadio { this.metadata = metadata }

        handler.handleFromRadio(proto)

        verify { configFlowManager.handleLocalMetadata(metadata) }
    }

    @Test
    fun `handleFromRadio routes NODE_INFO to configFlowManager`() {
        val nodeInfo = MeshProtos.NodeInfo.newBuilder().setNum(1234).build()
        val proto = fromRadio { this.nodeInfo = nodeInfo }

        handler.handleFromRadio(proto)

        verify { configFlowManager.handleNodeInfo(nodeInfo) }
        verify { serviceRepository.setStatusMessage(any()) }
    }

    @Test
    fun `handleFromRadio routes QUEUESTATUS to packetHandler`() {
        val queueStatus = MeshProtos.QueueStatus.newBuilder().setFree(5).build()
        val proto = fromRadio { this.queueStatus = queueStatus }

        handler.handleFromRadio(proto)

        verify { packetHandler.handleQueueStatus(queueStatus) }
    }

    @Test
    fun `handleFromRadio routes CONFIG to configHandler`() {
        val config = ConfigProtos.Config.newBuilder().build()
        val proto = fromRadio { this.config = config }

        handler.handleFromRadio(proto)

        verify { configHandler.handleDeviceConfig(config) }
    }

    @Test
    fun `handleFromRadio routes CLIENTNOTIFICATION to serviceRepository and notifications`() {
        val notification = MeshProtos.ClientNotification.newBuilder().setReplyId(42).build()
        val proto = fromRadio { this.clientNotification = notification }

        handler.handleFromRadio(proto)

        verify { serviceRepository.setClientNotification(notification) }
        verify { serviceNotifications.showClientNotification(notification) }
        verify { packetHandler.removeResponse(42, false) }
    }
}
