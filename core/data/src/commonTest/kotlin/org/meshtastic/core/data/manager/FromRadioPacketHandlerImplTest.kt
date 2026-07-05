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
import dev.mokkery.mock
import dev.mokkery.verify
import org.meshtastic.core.model.util.isOtaStatusNotification
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.XModemManager
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LockdownStatus
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.QueueStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo

class FromRadioPacketHandlerImplTest {

    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val mqttManager: MqttManager = mock(MockMode.autofill)
    private val packetHandler: PacketHandler = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val configFlowManager: MeshConfigFlowManager = mock(MockMode.autofill)
    private val configHandler: MeshConfigHandler = mock(MockMode.autofill)
    private val xmodemManager: XModemManager = mock(MockMode.autofill)
    private val lockdownCoordinator = FakeLockdownCoordinator()

    private lateinit var handler: FromRadioPacketHandlerImpl

    @BeforeTest
    fun setup() {
        handler =
            FromRadioPacketHandlerImpl(
                serviceRepository,
                lazy { configFlowManager },
                lazy { configHandler },
                lazy { xmodemManager },
                mqttManager,
                packetHandler,
                notificationManager,
                lockdownCoordinator,
            )
    }

    @Test
    fun `handleFromRadio routes MY_INFO to configFlowManager`() {
        val myInfo = MyNodeInfo(my_node_num = 1234)
        val proto = FromRadio(my_info = myInfo)

        handler.handleFromRadio(proto)

        verify { configFlowManager.handleMyInfo(myInfo) }
    }

    @Test
    fun `handleFromRadio routes METADATA to configFlowManager`() {
        val metadata = DeviceMetadata(firmware_version = "v1.0")
        val proto = FromRadio(metadata = metadata)

        handler.handleFromRadio(proto)

        verify { configFlowManager.handleLocalMetadata(metadata) }
    }

    @Test
    fun `handleFromRadio routes NODE_INFO to configFlowManager and updates status`() {
        val nodeInfo = ProtoNodeInfo(num = 1234)
        val proto = FromRadio(node_info = nodeInfo)

        every { configFlowManager.newNodeCount } returns 1

        handler.handleFromRadio(proto)

        verify { configFlowManager.handleNodeInfo(nodeInfo) }
        verify { serviceRepository.setConnectionProgress("Nodes (1)") }
    }

    @Test
    fun `handleFromRadio routes CONFIG_COMPLETE_ID to configFlowManager`() {
        val nonce = 69420
        val proto = FromRadio(config_complete_id = nonce)

        handler.handleFromRadio(proto)

        verify { configFlowManager.handleConfigComplete(nonce) }
        assertTrue(lockdownCoordinator.configCompleteCalled)
    }

    @Test
    fun `handleFromRadio routes LOCKDOWN_STATUS to lockdownCoordinator`() {
        val lockdownStatus = LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "token_missing")
        val proto = FromRadio(lockdown_status = lockdownStatus)

        handler.handleFromRadio(proto)

        assertEquals(lockdownStatus, lockdownCoordinator.lastStatus)
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

        verify { configHandler.handleDeviceConfig(config) }
    }

    @Test
    fun `handleFromRadio routes MODULE_CONFIG to configHandler`() {
        val moduleConfig = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        val proto = FromRadio(moduleConfig = moduleConfig)

        handler.handleFromRadio(proto)

        verify { configHandler.handleModuleConfig(moduleConfig) }
    }

    @Test
    fun `handleFromRadio routes CHANNEL to configHandler`() {
        val channel = Channel(index = 0)
        val proto = FromRadio(channel = channel)

        handler.handleFromRadio(proto)

        verify { configHandler.handleChannel(channel) }
    }

    @Test
    fun `handleFromRadio routes REGION_PRESETS to configHandler`() {
        val map = LoRaRegionPresetMap()
        val proto = FromRadio(region_presets = map)

        handler.handleFromRadio(proto)

        verify { configHandler.handleRegionPresets(map) }
    }

    @Test
    fun `handleFromRadio routes MQTT_CLIENT_PROXY_MESSAGE to mqttManager`() {
        val proxyMsg = MqttClientProxyMessage(topic = "test/topic")
        val proto = FromRadio(mqttClientProxyMessage = proxyMsg)

        handler.handleFromRadio(proto)

        verify { mqttManager.handleMqttProxyMessage(proxyMsg) }
    }

    @Test
    fun `handleFromRadio routes CLIENTNOTIFICATION to serviceRepository`() {
        val notification = ClientNotification(message = "test")
        val proto = FromRadio(clientNotification = notification)

        // Note: getString() from Compose Resources requires Skiko native lib which
        // is not available in headless JVM tests. We test the parts that don't trigger it.
        try {
            handler.handleFromRadio(proto)
        } catch (_: Throwable) {
            // Expected: Skiko can't load in headless JVM/native
        }

        verify { serviceRepository.setClientNotification(notification) }
    }

    @Test
    fun `OTA status client notifications are identified`() {
        assertTrue(ClientNotification(message = "Rebooting to WiFi OTA").isOtaStatusNotification())
        assertTrue(ClientNotification(message = "OTA Loader does not support WiFi").isOtaStatusNotification())
        assertTrue(
            ClientNotification(message = "Cannot start OTA: OTA Loader partition not found.").isOtaStatusNotification(),
        )
        assertTrue(ClientNotification(message = "Unable to switch to the OTA partition.").isOtaStatusNotification())
    }

    @Test
    fun `non OTA client notifications are not identified as OTA status`() {
        assertFalse(ClientNotification(message = "test").isOtaStatusNotification())
        assertFalse(ClientNotification(message = "Low battery").isOtaStatusNotification())
        assertFalse(ClientNotification(message = "ROTATE credentials").isOtaStatusNotification())
        assertFalse(ClientNotification(message = "Quota exceeded").isOtaStatusNotification())
    }
}
