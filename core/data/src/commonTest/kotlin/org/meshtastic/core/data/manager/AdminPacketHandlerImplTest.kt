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
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioSessionContext
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
    private val session = RadioSessionContext(generation = 7L, address = "tcp:test")

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
        val payload = adminMessage.encode().toByteString()
        return MeshPacket.Builder().also { wb ->wb.from = from; wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.ADMIN_APP; wb.payload = payload}.build()}.build()
    }

    private fun handle(packet: MeshPacket) = handler.handleAdminMessage(packet, myNodeNum, session)

    // ---------- Session passkey ----------

    @Test
    fun `session passkey is updated when present`() {
        val passkey = ByteString.of(1, 2, 3, 4)
        val adminMsg = AdminMessage.Builder().also { wb ->wb.session_passkey = passkey}.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { sessionManager.recordSession(myNodeNum, passkey) }
    }

    @Test
    fun `empty session passkey does not record refresh`() {
        val adminMsg = AdminMessage.Builder().also { wb ->wb.session_passkey = ByteString.EMPTY}.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)
        // recordSession should NOT be called for empty passkey
    }

    // ---------- get_config_response ----------

    @Test
    fun `get_config_response from own node delegates to configHandler`() {
        val config = Config.Builder().also { wb ->wb.device = Config.DeviceConfig.Builder().also { wb ->wb.role = Config.DeviceConfig.Role.CLIENT}.build()}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_config_response = config}.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configHandler.handleDeviceConfig(config, session) }
    }

    @Test
    fun `get_config_response from remote node is ignored`() {
        val config = Config.Builder().also { wb ->wb.device = Config.DeviceConfig.Builder().build()}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_config_response = config}.build()
        val packet = makePacket(99999, adminMsg)

        handle(packet)
        // configHandler.handleDeviceConfig should NOT be called
    }

    // ---------- get_module_config_response ----------

    @Test
    fun `get_module_config_response from own node delegates to configHandler`() {
        val moduleConfig = ModuleConfig.Builder().also { wb ->wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb ->wb.enabled = true}.build()}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_module_config_response = moduleConfig}.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configHandler.handleModuleConfig(moduleConfig, session) }
    }

    @Test
    fun `get_module_config_response from remote node updates node status`() {
        val moduleConfig = ModuleConfig.Builder().also { wb ->wb.statusmessage = ModuleConfig.StatusMessageConfig.Builder().also { wb ->wb.node_status = "Battery Low"}.build()}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_module_config_response = moduleConfig}.build()
        val remoteNode = 99999
        val packet = makePacket(remoteNode, adminMsg)

        handle(packet)

        verify { nodeManager.updateNodeForSession(remoteNode, session, channel = 0, transform = any()) }
    }

    @Test
    fun `get_module_config_response from remote without status message does not crash`() {
        val moduleConfig = ModuleConfig.Builder().also { wb ->wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb ->wb.enabled = true}.build()}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_module_config_response = moduleConfig}.build()
        val packet = makePacket(99999, adminMsg)

        handle(packet)
        // No crash, no session-bound node update
    }

    // ---------- get_channel_response ----------

    @Test
    fun `get_channel_response from own node delegates to configHandler`() {
        val channel = Channel.Builder().also { wb ->wb.index = 0}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_channel_response = channel}.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configHandler.handleChannel(channel, session) }
    }

    @Test
    fun `get_channel_response from remote node is ignored`() {
        val channel = Channel.Builder().also { wb ->wb.index = 0}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_channel_response = channel}.build()
        val packet = makePacket(99999, adminMsg)

        handle(packet)
        // configHandler.handleChannel should NOT be called
    }

    // ---------- get_device_metadata_response ----------

    @Test
    fun `device metadata from own node delegates to configFlowManager`() {
        val metadata = DeviceMetadata.Builder().also { wb ->wb.firmware_version = "2.6.0"; wb.hw_model = HardwareModel.HELTEC_V3}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_device_metadata_response = metadata}.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configFlowManager.handleLocalMetadata(metadata, session) }
    }

    @Test
    fun `device metadata from remote node delegates to nodeManager`() {
        val metadata = DeviceMetadata.Builder().also { wb ->wb.firmware_version = "2.5.0"; wb.hw_model = HardwareModel.TBEAM}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.get_device_metadata_response = metadata}.build()
        val remoteNode = 99999
        val packet = makePacket(remoteNode, adminMsg)

        handle(packet)

        verify { nodeManager.insertMetadata(remoteNode, metadata, session) }
    }

    // ---------- Edge cases ----------

    @Test
    fun `packet with null decoded payload is ignored`() {
        val packet = MeshPacket.Builder().also { wb ->wb.from = myNodeNum; wb.decoded = null}.build()
        handle(packet)
        // No crash
    }

    @Test
    fun `packet with empty payload bytes is ignored`() {
        val packet =
            MeshPacket.Builder().also { wb ->wb.from = myNodeNum; wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.ADMIN_APP; wb.payload = ByteString.EMPTY}.build()}.build()
        handle(packet)
        // No crash — decodes as default AdminMessage with no fields set
    }

    @Test
    fun `combined admin message with passkey and config response`() {
        val passkey = ByteString.of(5, 6, 7, 8)
        val config = Config.Builder().also { wb ->wb.lora = Config.LoRaConfig.Builder().build()}.build()
        val adminMsg = AdminMessage.Builder().also { wb ->wb.session_passkey = passkey; wb.get_config_response = config}.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { sessionManager.recordSession(myNodeNum, passkey) }
        verify { configHandler.handleDeviceConfig(config, session) }
    }
}
