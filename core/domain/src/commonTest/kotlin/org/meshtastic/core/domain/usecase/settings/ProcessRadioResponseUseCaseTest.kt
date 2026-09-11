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
import kotlin.test.assertIs
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
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ROUTING_APP
                                wb.request_id = 42
                                wb.payload =
                                    Routing.Builder()
                                        .also { wb -> wb.error_reason = Routing.Error.NO_ROUTE }
                                        .build()
                                        .encode()
                                        .toByteString()
                            }
                            .build()
                }
                .build()

        // Act
        val result = useCase(packet, 123, setOf(42))

        // Assert
        val error = assertIs<RadioResponseResult.Error>(result)
        assertEquals(Routing.Error.NO_ROUTE, error.routingError)
    }

    @Test
    fun `routing response without error reason is not treated as an error`() {
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ROUTING_APP
                                wb.request_id = 42
                                wb.payload = Routing.Builder().build().encode().toByteString()
                            }
                            .build()
                }
                .build()

        val result = useCase(packet, 123, setOf(42))

        assertEquals(RadioResponseResult.Success, result)
    }

    @Test
    fun `routing ack from a node other than the addressed one is reported instead of dropped`() {
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 456
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ROUTING_APP
                                wb.request_id = 42
                                wb.payload =
                                    Routing.Builder()
                                        .also { wb -> wb.error_reason = Routing.Error.NONE }
                                        .build()
                                        .encode()
                                        .toByteString()
                            }
                            .build()
                }
                .build()

        val result = useCase(packet, 123, setOf(42))

        assertEquals(RadioResponseResult.UnexpectedAckSender(from = 456), result)
    }

    @Test
    fun `invoke with metadata response returns metadata result`() {
        // Arrange
        val metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.5.0" }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_device_metadata_response = metadata }.build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.request_id = 42
                                wb.payload = adminMsg.encode().toByteString()
                            }
                            .build()
                }
                .build()

        // Act
        val result = useCase(packet, 123, setOf(42))

        // Assert
        assertTrue(result is RadioResponseResult.Metadata)
        assertEquals("2.5.0", result.metadata.firmware_version)
    }

    @Test
    fun `invoke with canned messages response returns canned messages result`() {
        // Arrange
        val adminMsg =
            AdminMessage.Builder().also { wb -> wb.get_canned_message_module_messages_response = "Hello World" }.build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.request_id = 42
                                wb.payload = adminMsg.encode().toByteString()
                            }
                            .build()
                }
                .build()

        // Act
        val result = useCase(packet, 123, setOf(42))

        // Assert
        assertTrue(result is RadioResponseResult.CannedMessages)
        assertEquals("Hello World", result.messages)
    }

    @Test
    fun `invoke with unexpected sender returns error`() {
        val adminMsg = AdminMessage.Builder().build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 456
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.request_id = 42
                                wb.payload = adminMsg.encode().toByteString()
                            }
                            .build()
                }
                .build()
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.Error)
    }

    @Test
    fun `invoke with owner response returns owner result`() {
        val owner = org.meshtastic.proto.User.Builder().also { wb -> wb.long_name = "Owner" }.build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_owner_response = owner }.build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.request_id = 42
                                wb.payload = adminMsg.encode().toByteString()
                            }
                            .build()
                }
                .build()
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.Owner)
        assertEquals("Owner", result.user.long_name)
    }

    @Test
    fun `invoke with config response returns config result`() {
        val config =
            org.meshtastic.proto.Config.Builder()
                .also { wb ->
                    wb.lora =
                        org.meshtastic.proto.Config.LoRaConfig.Builder().also { wb -> wb.use_preset = true }.build()
                }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_config_response = config }.build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.request_id = 42
                                wb.payload = adminMsg.encode().toByteString()
                            }
                            .build()
                }
                .build()
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.ConfigResponse)
    }

    @Test
    fun `invoke with module config response returns module config result`() {
        val config =
            org.meshtastic.proto.ModuleConfig.Builder()
                .also { wb ->
                    wb.mqtt =
                        org.meshtastic.proto.ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build()
                }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_module_config_response = config }.build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.request_id = 42
                                wb.payload = adminMsg.encode().toByteString()
                            }
                            .build()
                }
                .build()
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.ModuleConfigResponse)
    }

    @Test
    fun `invoke with channel response returns channel result`() {
        val channel =
            org.meshtastic.proto.Channel.Builder()
                .also { wb ->
                    wb.settings = org.meshtastic.proto.ChannelSettings.Builder().also { wb -> wb.name = "Main" }.build()
                }
                .build()
        val adminMsg = AdminMessage.Builder().also { wb -> wb.get_channel_response = channel }.build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 123
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.request_id = 42
                                wb.payload = adminMsg.encode().toByteString()
                            }
                            .build()
                }
                .build()
        val result = useCase(packet, 123, setOf(42))
        assertTrue(result is RadioResponseResult.ChannelResponse)
        assertEquals("Main", result.channel.settings?.name)
    }

    private fun ByteArray.toByteString() = okio.ByteString.of(*this)
}
