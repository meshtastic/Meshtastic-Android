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
import dev.mokkery.verify.VerifyMode
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
import kotlin.test.assertNotNull

class AdminPacketHandlerImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val configHandler = mock<MeshConfigHandler>(MockMode.autofill)
    private val configFlowManager = mock<MeshConfigFlowManager>(MockMode.autofill)
    private val sessionManager = mock<SessionManager>(MockMode.autofill)
    private val adminRequestRegistry = AdminRequestRegistryImpl()

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
                adminRequestRegistry = adminRequestRegistry,
            )
    }

    /**
     * Builds a response to a request this app sent to [from] unless [requestId] names one it did not. Remote responses
     * are PKI-encrypted by default.
     */
    private fun makePacket(
        from: Int,
        adminMessage: AdminMessage,
        requestId: Int = SENT_REQUEST_ID,
        pkiEncrypted: Boolean = from != myNodeNum,
        channel: Int = 0,
    ): MeshPacket {
        if (requestId == SENT_REQUEST_ID) adminRequestRegistry.register(requestId, from, ADMIN_CHANNEL_INDEX)
        val payload = adminMessage.encode().toByteString()
        return MeshPacket.Builder()
            .also { wb ->
                wb.from = from
                wb.pki_encrypted = pkiEncrypted
                wb.channel = channel
                wb.decoded =
                    Data.Builder()
                        .also { wb ->
                            wb.portnum = PortNum.ADMIN_APP
                            wb.payload = payload
                            wb.request_id = requestId
                        }
                        .build()
            }
            .build()
    }

    private fun handle(packet: MeshPacket) = handler.handleAdminMessage(packet, myNodeNum, session)

    // ---------- Request correlation ----------

    @Test
    fun `config response claiming own node without an outstanding request is ignored`() {
        val config = Config.Builder().also { wb -> wb.security = Config.SecurityConfig.Builder().build() }.build()
        val adminMsg =
            AdminMessage.Builder()
                .also { wb ->
                    wb.get_config_response = config
                    wb.session_passkey = ByteString.of(9, 9, 9, 9)
                }
                .build()
        val packet = makePacket(myNodeNum, adminMsg, requestId = UNSOLICITED_REQUEST_ID)

        handle(packet)

        verify(VerifyMode.not) { configHandler.handleDeviceConfig(any(), any()) }
        verify(VerifyMode.not) { sessionManager.recordSession(any(), any()) }
    }

    @Test
    fun `channel response claiming own node without an outstanding request is ignored`() {
        val channel = Channel.Builder().also { wb -> wb.index = 0 }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_channel_response = channel }.build()
        val packet = makePacket(myNodeNum, adminMsg, requestId = UNSOLICITED_REQUEST_ID)

        handle(packet)

        verify(VerifyMode.not) { configHandler.handleChannel(any(), any()) }
    }

    @Test
    fun `metadata from a remote node without an outstanding request is ignored`() {
        val metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "9.9.9" }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_device_metadata_response = metadata }.build()
        val packet = makePacket(99999, adminMsg, requestId = UNSOLICITED_REQUEST_ID)

        handle(packet)

        verify(VerifyMode.not) { nodeManager.insertMetadata(any(), any(), any()) }
    }

    @Test
    fun `a request id answers once`() {
        val config = Config.Builder().also { wb -> wb.device = Config.DeviceConfig.Builder().build() }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_config_response = config }.build()

        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)
        handle(packet)

        verify(VerifyMode.exactly(1)) { configHandler.handleDeviceConfig(config, session) }
    }

    @Test
    fun `response from a node the request was not sent to is ignored and the genuine response still completes it`() {
        val passkey = ByteString.of(4, 3, 2, 1)
        val metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.5.0" }.build()
        val adminMsg =
            AdminMessage.Builder()
                .also { wb ->
                    wb.session_passkey = passkey
                    wb.get_device_metadata_response = metadata
                }
                .build()
        adminRequestRegistry.register(OTHER_REQUEST_ID, REMOTE_NODE, ADMIN_CHANNEL_INDEX)

        handle(makePacket(OTHER_NODE, adminMsg, requestId = OTHER_REQUEST_ID, pkiEncrypted = true))

        verify(VerifyMode.not) { sessionManager.recordSession(any(), any()) }
        verify(VerifyMode.not) { nodeManager.insertMetadata(any(), any(), any()) }

        handle(makePacket(REMOTE_NODE, adminMsg, requestId = OTHER_REQUEST_ID, pkiEncrypted = true))

        verify { sessionManager.recordSession(REMOTE_NODE, passkey) }
        verify { nodeManager.insertMetadata(REMOTE_NODE, metadata, session) }
    }

    @Test
    fun `remote response that is neither PKI encrypted nor on the admin channel is ignored and stays outstanding`() {
        val passkey = ByteString.of(7, 7, 7, 7)
        val adminMsg = AdminMessage.Builder().also { wb -> wb.session_passkey = passkey }.build()
        adminRequestRegistry.register(OTHER_REQUEST_ID, REMOTE_NODE, ADMIN_CHANNEL_INDEX)

        handle(makePacket(REMOTE_NODE, adminMsg, OTHER_REQUEST_ID, pkiEncrypted = false, channel = 0))

        verify(VerifyMode.not) { sessionManager.recordSession(any(), any()) }

        handle(makePacket(REMOTE_NODE, adminMsg, OTHER_REQUEST_ID, pkiEncrypted = true))

        verify { sessionManager.recordSession(REMOTE_NODE, passkey) }
    }

    @Test
    fun `remote response on the admin channel without PKI is handled`() {
        val passkey = ByteString.of(8, 8, 8, 8)
        val adminMsg = AdminMessage.Builder().also { wb -> wb.session_passkey = passkey }.build()
        adminRequestRegistry.register(OTHER_REQUEST_ID, REMOTE_NODE, ADMIN_CHANNEL_INDEX)

        handle(makePacket(REMOTE_NODE, adminMsg, OTHER_REQUEST_ID, pkiEncrypted = false, channel = ADMIN_CHANNEL_INDEX))

        verify { sessionManager.recordSession(REMOTE_NODE, passkey) }
    }

    @Test
    fun `remote response without PKI is ignored when no admin channel existed at send`() {
        val adminMsg = AdminMessage.Builder().also { wb -> wb.session_passkey = ByteString.of(6, 6, 6, 6) }.build()
        adminRequestRegistry.register(OTHER_REQUEST_ID, REMOTE_NODE, adminChannelIndex = null)

        handle(makePacket(REMOTE_NODE, adminMsg, OTHER_REQUEST_ID, pkiEncrypted = false, channel = 0))

        verify(VerifyMode.not) { sessionManager.recordSession(any(), any()) }
    }

    @Test
    fun `local response from the previous node number is ignored and stays pending after a renumber`() {
        val passkey = ByteString.of(1, 1, 1, 1)
        val config = Config.Builder().also { wb -> wb.device = Config.DeviceConfig.Builder().build() }.build()
        val adminMsg =
            AdminMessage.Builder()
                .also { wb ->
                    wb.session_passkey = passkey
                    wb.get_config_response = config
                }
                .build()
        adminRequestRegistry.register(OTHER_REQUEST_ID, PREVIOUS_NODE_NUM, null)

        handle(makePacket(PREVIOUS_NODE_NUM, adminMsg, OTHER_REQUEST_ID, pkiEncrypted = false))

        verify(VerifyMode.not) { sessionManager.recordSession(any(), any()) }
        verify(VerifyMode.not) { configHandler.handleDeviceConfig(any(), any()) }
        assertNotNull(adminRequestRegistry.find(OTHER_REQUEST_ID))
    }

    @Test
    fun `response from the current node number to a request addressed to the previous one is ignored`() {
        val adminMsg = AdminMessage.Builder().also { wb -> wb.session_passkey = ByteString.of(2, 2, 2, 2) }.build()
        adminRequestRegistry.register(OTHER_REQUEST_ID, PREVIOUS_NODE_NUM, null)

        handle(makePacket(myNodeNum, adminMsg, OTHER_REQUEST_ID, pkiEncrypted = true))

        verify(VerifyMode.not) { sessionManager.recordSession(any(), any()) }
        assertNotNull(adminRequestRegistry.find(OTHER_REQUEST_ID))
    }

    @Test
    fun `non PKI response on a channel other than the admin channel is ignored and stays pending`() {
        val passkey = ByteString.of(3, 3, 3, 3)
        val adminMsg = AdminMessage.Builder().also { wb -> wb.session_passkey = passkey }.build()
        adminRequestRegistry.register(OTHER_REQUEST_ID, REMOTE_NODE, ADMIN_CHANNEL_INDEX)

        val otherChannel = ADMIN_CHANNEL_INDEX + 1

        handle(makePacket(REMOTE_NODE, adminMsg, OTHER_REQUEST_ID, pkiEncrypted = false, channel = otherChannel))

        verify(VerifyMode.not) { sessionManager.recordSession(any(), any()) }
        assertNotNull(adminRequestRegistry.find(OTHER_REQUEST_ID))
    }

    // ---------- Session passkey ----------

    @Test
    fun `session passkey is updated when present`() {
        val passkey = ByteString.of(1, 2, 3, 4)
        val adminMsg = AdminMessage.Builder().also { wb -> wb.session_passkey = passkey }.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { sessionManager.recordSession(myNodeNum, passkey) }
    }

    @Test
    fun `empty session passkey does not record refresh`() {
        val adminMsg = AdminMessage.Builder().also { wb -> wb.session_passkey = ByteString.EMPTY }.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)
        // recordSession should NOT be called for empty passkey
    }

    // ---------- get_config_response ----------

    @Test
    fun `get_config_response from own node delegates to configHandler`() {
        val config =
            Config.Builder()
                .also { wb ->
                    wb.device =
                        Config.DeviceConfig.Builder().also { wb -> wb.role = Config.DeviceConfig.Role.CLIENT }.build()
                }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_config_response = config }.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configHandler.handleDeviceConfig(config, session) }
    }

    @Test
    fun `get_config_response from remote node is ignored`() {
        val config = Config.Builder().also { wb -> wb.device = Config.DeviceConfig.Builder().build() }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_config_response = config }.build()
        val packet = makePacket(99999, adminMsg)

        handle(packet)
        // configHandler.handleDeviceConfig should NOT be called
    }

    // ---------- get_module_config_response ----------

    @Test
    fun `get_module_config_response from own node delegates to configHandler`() {
        val moduleConfig =
            ModuleConfig.Builder()
                .also { wb -> wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build() }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_module_config_response = moduleConfig }.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configHandler.handleModuleConfig(moduleConfig, session) }
    }

    @Test
    fun `get_module_config_response from remote node updates node status`() {
        val moduleConfig =
            ModuleConfig.Builder()
                .also { wb ->
                    wb.statusmessage =
                        ModuleConfig.StatusMessageConfig.Builder().also { wb -> wb.node_status = "Battery Low" }.build()
                }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_module_config_response = moduleConfig }.build()
        val remoteNode = 99999
        val packet = makePacket(remoteNode, adminMsg)

        handle(packet)

        verify { nodeManager.updateNodeForSession(remoteNode, session, channel = 0, transform = any()) }
    }

    @Test
    fun `get_module_config_response from remote without status message does not crash`() {
        val moduleConfig =
            ModuleConfig.Builder()
                .also { wb -> wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build() }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_module_config_response = moduleConfig }.build()
        val packet = makePacket(99999, adminMsg)

        handle(packet)
        // No crash, no session-bound node update
    }

    // ---------- get_channel_response ----------

    @Test
    fun `get_channel_response from own node delegates to configHandler`() {
        val channel = Channel.Builder().also { wb -> wb.index = 0 }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_channel_response = channel }.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configHandler.handleChannel(channel, session) }
    }

    @Test
    fun `get_channel_response from remote node is ignored`() {
        val channel = Channel.Builder().also { wb -> wb.index = 0 }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_channel_response = channel }.build()
        val packet = makePacket(99999, adminMsg)

        handle(packet)
        // configHandler.handleChannel should NOT be called
    }

    // ---------- get_device_metadata_response ----------

    @Test
    fun `device metadata from own node delegates to configFlowManager`() {
        val metadata =
            DeviceMetadata.Builder()
                .also { wb ->
                    wb.firmware_version = "2.6.0"
                    wb.hw_model = HardwareModel.HELTEC_V3
                }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_device_metadata_response = metadata }.build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { configFlowManager.handleLocalMetadata(metadata, session) }
    }

    @Test
    fun `device metadata from remote node delegates to nodeManager`() {
        val metadata =
            DeviceMetadata.Builder()
                .also { wb ->
                    wb.firmware_version = "2.5.0"
                    wb.hw_model = HardwareModel.TBEAM
                }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_device_metadata_response = metadata }.build()
        val remoteNode = 99999
        val packet = makePacket(remoteNode, adminMsg)

        handle(packet)

        verify { nodeManager.insertMetadata(remoteNode, metadata, session) }
    }

    // ---------- Edge cases ----------

    @Test
    fun `packet with null decoded payload is ignored`() {
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = myNodeNum
                    wb.decoded = null
                }
                .build()
        handle(packet)
        // No crash
    }

    @Test
    fun `packet with empty payload bytes is ignored`() {
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = myNodeNum
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.payload = ByteString.EMPTY
                            }
                            .build()
                }
                .build()
        handle(packet)
        // No crash — decodes as default AdminMessage with no fields set
    }

    @Test
    fun `combined admin message with passkey and config response`() {
        val passkey = ByteString.of(5, 6, 7, 8)
        val config = Config.Builder().also { wb -> wb.lora = Config.LoRaConfig.Builder().build() }.build()
        val adminMsg =
            AdminMessage.Builder()
                .also { wb ->
                    wb.session_passkey = passkey
                    wb.get_config_response = config
                }
                .build()
        val packet = makePacket(myNodeNum, adminMsg)

        handle(packet)

        verify { sessionManager.recordSession(myNodeNum, passkey) }
        verify { configHandler.handleDeviceConfig(config, session) }
    }

    private companion object {
        const val SENT_REQUEST_ID = 4242
        const val UNSOLICITED_REQUEST_ID = 777
        const val OTHER_REQUEST_ID = 555
        const val REMOTE_NODE = 99999
        const val OTHER_NODE = 88888
        const val PREVIOUS_NODE_NUM = 11111
        const val ADMIN_CHANNEL_INDEX = 3
    }
}
