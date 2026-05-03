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
package org.meshtastic.core.domain.usecase.settings

import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessRadioResponseUseCaseTest {

    private lateinit var useCase: ProcessRadioResponseUseCase

    @BeforeTest
    fun setUp() {
        useCase = ProcessRadioResponseUseCase()
    }

    @Test
    fun `invoke with routing error returns error result`() {
        // Arrange
        val packet =
            MeshPacket(
                from = 123,
                decoded =
                Data(
                    portnum = PortNum.ROUTING_APP,
                    request_id = 42,
                    payload = Routing(error_reason = Routing.Error.NO_ROUTE).encode().toByteString(),
                ),
            )

        // Act
        val result = useCase(packet, 123, setOf(42))

        // Assert
        assertTrue(result is RadioResponseResult.Error)
    }

    @Test
    fun `invoke with metadata response returns metadata result`() {
        // Arrange
        val metadata = DeviceMetadata(firmware_version = "2.5.0")
        val adminMsg = AdminMessage(get_device_metadata_response = metadata)
        val packet =
            MeshPacket(
                from = 123,
                decoded = Data(
                    portnum = PortNum.ADMIN_APP,
                    request_id = 42,
                    payload = adminMsg.encode().toByteString(),
                ),
            )

        // Act
        val result = useCase(packet, 123, setOf(42))

        // Assert
        assertTrue(result is RadioResponseResult.Metadata)
        assertEquals("2.5.0", result.metadata.firmware_version)
    }

    @Test
    fun `invoke with canned messages response returns canned messages result`() {
        // Arrange
        val adminMsg = AdminMessage(get_canned_message_module_messages_response = "Hello World")
        val packet =
            MeshPacket(
                from = 123,
                decoded = Data(
                    portnum = PortNum.ADMIN_APP,
                    request_id = 42,
                    payload = adminMsg.encode().toByteString(),
                ),
            )

        // Act
        val result = useCase(packet, 123, setOf(42))

        // Assert
        assertTrue(result is RadioResponseResult.CannedMessages)
        assertEquals("Hello World", result.messages)
    }

    @Test
    fun `invoke with unexpected sender returns error`() {
        val adminMsg = AdminMessage()
        val packet =
            MeshPacket(
                from = 456,
                decoded = Data(
                    portnum = PortNum.ADMIN_APP,
                    request_id = 42,
                    payload = adminMsg.encode().toByteString(),
                ),
            )
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.Error)
    }

    @Test
    fun `invoke with owner response returns owner result`() {
        val owner = org.meshtastic.proto.User(long_name = "Owner")
        val adminMsg = AdminMessage(get_owner_response = owner)
        val packet =
            MeshPacket(
                from = 123,
                decoded = Data(
                    portnum = PortNum.ADMIN_APP,
                    request_id = 42,
                    payload = adminMsg.encode().toByteString(),
                ),
            )
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.Owner)
        assertEquals("Owner", result.user.long_name)
    }

    @Test
    fun `invoke with config response returns config result`() {
        val config = org.meshtastic.proto.Config(lora = org.meshtastic.proto.Config.LoRaConfig(use_preset = true))
        val adminMsg = AdminMessage(get_config_response = config)
        val packet =
            MeshPacket(
                from = 123,
                decoded = Data(
                    portnum = PortNum.ADMIN_APP,
                    request_id = 42,
                    payload = adminMsg.encode().toByteString(),
                ),
            )
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.ConfigResponse)
    }

    @Test
    fun `invoke with module config response returns module config result`() {
        val config =
            org.meshtastic.proto.ModuleConfig(mqtt = org.meshtastic.proto.ModuleConfig.MQTTConfig(enabled = true))
        val adminMsg = AdminMessage(get_module_config_response = config)
        val packet =
            MeshPacket(
                from = 123,
                decoded = Data(
                    portnum = PortNum.ADMIN_APP,
                    request_id = 42,
                    payload = adminMsg.encode().toByteString(),
                ),
            )
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.ModuleConfigResponse)
    }

    @Test
    fun `invoke with channel response returns channel result`() {
        val channel = org.meshtastic.proto.Channel(settings = org.meshtastic.proto.ChannelSettings(name = "Main"))
        val adminMsg = AdminMessage(get_channel_response = channel)
        val packet =
            MeshPacket(
                from = 123,
                decoded = Data(
                    portnum = PortNum.ADMIN_APP,
                    request_id = 42,
                    payload = adminMsg.encode().toByteString(),
                ),
            )
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.ChannelResponse)
        assertEquals("Main", result.channel.settings?.name)
    }

    private fun ByteArray.toByteString() = okio.ByteString.of(*this)
}
