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
package org.meshtastic.core.data.manager

class FromRadioPacketHandlerImplTest {
    /*


    private lateinit var handler: FromRadioPacketHandlerImpl

    @Before
    fun setup() {
        mockkStatic("org.meshtastic.core.resources.GetStringKt")

        handler =
            FromRadioPacketHandlerImpl(
                serviceRepository,
                lazy { router },
                mqttManager,
                packetHandler,
                notificationManager,
            )
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

        every { router.configFlowManager.newNodeCount } returns 1

        handler.handleFromRadio(proto)

        verify { router.configFlowManager.handleNodeInfo(nodeInfo) }
        verify { serviceRepository.setConnectionProgress("Nodes (1)") }
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
        verify { packetHandler.removeResponse(0, complete = false) }
    }

     */
}
