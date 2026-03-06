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
        assertEquals("2.5.0", (result as RadioResponseResult.Metadata).metadata.firmware_version)
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
        assertEquals("Hello World", (result as RadioResponseResult.CannedMessages).messages)
    }

    private fun ByteArray.toByteString() = okio.ByteString.of(*this)
}
