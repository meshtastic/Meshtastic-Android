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
import dev.mokkery.mock
import dev.mokkery.verify
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import kotlin.test.BeforeTest
import kotlin.test.Test

class AdminPacketHandlerImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val configHandler = mock<MeshConfigHandler>(MockMode.autofill)
    private val configFlowManager = mock<MeshConfigFlowManager>(MockMode.autofill)
    private val sessionManager = mock<SessionManager>(MockMode.autofill)

    private lateinit var handler: AdminPacketHandlerImpl

    private val myNodeNum = 12345

    @BeforeTest
    fun setUp() {
        handler =
            AdminPacketHandlerImpl(
                nodeManager = nodeManager,
                configHandler = lazy { configHandler },
                configFlowManager = lazy { configFlowManager },
                sessionManager = sessionManager,
            )
    }

    private fun makePacket(from: Int, adminMessage: AdminMessage): MeshPacket {
        val payload = AdminMessage.ADAPTER.encode(adminMessage).toByteString()
        return MeshPacket(from = from, decoded = Data(portnum = PortNum.ADMIN_APP, payload = payload))
    }

    // ---------- Session passkey ----------

    @Test
    fun `session passkey is updated when present`() {
        val passkey = ByteString.of(1, 2, 3, 4)
        val adminMsg = AdminMessage(session_passkey = passkey)
        val packet = makePacket(myNodeNum, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { sessionManager.recordSession(myNodeNum, passkey) }
    }

    @Test
    fun `empty session passkey does not record refresh`() {
        val adminMsg = AdminMessage(session_passkey = ByteString.EMPTY)
        val packet = makePacket(myNodeNum, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)
        // recordSession should NOT be called for empty passkey
    }

    // ---------- get_config_response ----------

    @Test
    fun `get_config_response from own node delegates to configHandler`() {
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        val adminMsg = AdminMessage(get_config_response = config)
        val packet = makePacket(myNodeNum, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { configHandler.handleDeviceConfig(config) }
    }

    @Test
    fun `get_config_response from remote node is ignored`() {
        val config = Config(device = Config.DeviceConfig())
        val adminMsg = AdminMessage(get_config_response = config)
        val packet = makePacket(99999, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)
        // configHandler.handleDeviceConfig should NOT be called
    }

    // ---------- get_module_config_response ----------

    @Test
    fun `get_module_config_response from own node delegates to configHandler`() {
        val moduleConfig = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        val adminMsg = AdminMessage(get_module_config_response = moduleConfig)
        val packet = makePacket(myNodeNum, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { configHandler.handleModuleConfig(moduleConfig) }
    }

    @Test
    fun `get_module_config_response from remote node updates node status`() {
        val moduleConfig = ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = "Battery Low"))
        val adminMsg = AdminMessage(get_module_config_response = moduleConfig)
        val remoteNode = 99999
        val packet = makePacket(remoteNode, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { nodeManager.updateNodeStatus(remoteNode, "Battery Low") }
    }

    @Test
    fun `get_module_config_response from remote without status message does not crash`() {
        val moduleConfig = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        val adminMsg = AdminMessage(get_module_config_response = moduleConfig)
        val packet = makePacket(99999, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)
        // No crash, no updateNodeStatus call
    }

    // ---------- get_channel_response ----------

    @Test
    fun `get_channel_response from own node delegates to configHandler`() {
        val channel = Channel(index = 0)
        val adminMsg = AdminMessage(get_channel_response = channel)
        val packet = makePacket(myNodeNum, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { configHandler.handleChannel(channel) }
    }

    @Test
    fun `get_channel_response from remote node is ignored`() {
        val channel = Channel(index = 0)
        val adminMsg = AdminMessage(get_channel_response = channel)
        val packet = makePacket(99999, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)
        // configHandler.handleChannel should NOT be called
    }

    // ---------- get_device_metadata_response ----------

    @Test
    fun `device metadata from own node delegates to configFlowManager`() {
        val metadata = DeviceMetadata(firmware_version = "2.6.0", hw_model = HardwareModel.HELTEC_V3)
        val adminMsg = AdminMessage(get_device_metadata_response = metadata)
        val packet = makePacket(myNodeNum, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { configFlowManager.handleLocalMetadata(metadata) }
    }

    @Test
    fun `device metadata from remote node delegates to nodeManager`() {
        val metadata = DeviceMetadata(firmware_version = "2.5.0", hw_model = HardwareModel.TBEAM)
        val adminMsg = AdminMessage(get_device_metadata_response = metadata)
        val remoteNode = 99999
        val packet = makePacket(remoteNode, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { nodeManager.insertMetadata(remoteNode, metadata) }
    }

    // ---------- Edge cases ----------

    @Test
    fun `packet with null decoded payload is ignored`() {
        val packet = MeshPacket(from = myNodeNum, decoded = null)
        handler.handleAdminMessage(packet, myNodeNum)
        // No crash
    }

    @Test
    fun `packet with empty payload bytes is ignored`() {
        val packet =
            MeshPacket(from = myNodeNum, decoded = Data(portnum = PortNum.ADMIN_APP, payload = ByteString.EMPTY))
        handler.handleAdminMessage(packet, myNodeNum)
        // No crash — decodes as default AdminMessage with no fields set
    }

    @Test
    fun `combined admin message with passkey and config response`() {
        val passkey = ByteString.of(5, 6, 7, 8)
        val config = Config(lora = Config.LoRaConfig())
        val adminMsg = AdminMessage(session_passkey = passkey, get_config_response = config)
        val packet = makePacket(myNodeNum, adminMsg)

        handler.handleAdminMessage(packet, myNodeNum)

        verify { sessionManager.recordSession(myNodeNum, passkey) }
        verify { configHandler.handleDeviceConfig(config) }
    }
}
