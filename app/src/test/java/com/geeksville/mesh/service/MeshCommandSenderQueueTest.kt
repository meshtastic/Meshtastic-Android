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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.proto.PortNum

class MeshCommandSenderQueueTest {

    private val packetHandler = mockk<PacketHandler>(relaxed = true)
    private val connectionStateHandler = mockk<ConnectionStateHandler>(relaxed = true)
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private lateinit var commandSender: MeshCommandSender

    @Before
    fun setUp() {
        every { connectionStateHandler.connectionState } returns connectionStateFlow.asStateFlow()
        commandSender = MeshCommandSender(packetHandler, null, connectionStateHandler, null)
    }

    @Test
    fun `sendData queues TEXT_MESSAGE_APP when disconnected`() {
        val packet = DataPacket(dataType = PortNum.TEXT_MESSAGE_APP.value, bytes = ByteString.EMPTY)
        commandSender.sendData(packet)

        verify(exactly = 0) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }

        connectionStateFlow.value = ConnectionState.Connected
        commandSender.processQueuedPackets()

        verify(exactly = 1) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }
    }

    @Test
    fun `sendData queues ATAK_PLUGIN when disconnected`() {
        val packet = DataPacket(dataType = PortNum.ATAK_PLUGIN.value, bytes = ByteString.EMPTY)
        commandSender.sendData(packet)

        verify(exactly = 0) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }

        connectionStateFlow.value = ConnectionState.Connected
        commandSender.processQueuedPackets()

        verify(exactly = 1) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }
    }

    @Test
    fun `sendData queues ATAK_FORWARDER when disconnected`() {
        val packet = DataPacket(dataType = PortNum.ATAK_FORWARDER.value, bytes = ByteString.EMPTY)
        commandSender.sendData(packet)

        verify(exactly = 0) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }

        connectionStateFlow.value = ConnectionState.Connected
        commandSender.processQueuedPackets()

        verify(exactly = 1) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }
    }

    @Test
    fun `sendData queues DETECTION_SENSOR_APP when disconnected`() {
        val packet = DataPacket(dataType = PortNum.DETECTION_SENSOR_APP.value, bytes = ByteString.EMPTY)
        commandSender.sendData(packet)

        verify(exactly = 0) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }

        connectionStateFlow.value = ConnectionState.Connected
        commandSender.processQueuedPackets()

        verify(exactly = 1) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }
    }

    @Test
    fun `sendData queues PRIVATE_APP when disconnected`() {
        val packet = DataPacket(dataType = PortNum.PRIVATE_APP.value, bytes = ByteString.EMPTY)
        commandSender.sendData(packet)

        verify(exactly = 0) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }

        connectionStateFlow.value = ConnectionState.Connected
        commandSender.processQueuedPackets()

        verify(exactly = 1) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }
    }

    @Test
    fun `sendData does NOT queue IP_TUNNEL_APP when disconnected`() {
        val packet = DataPacket(dataType = PortNum.IP_TUNNEL_APP.value, bytes = ByteString.EMPTY)
        commandSender.sendData(packet)

        verify(exactly = 0) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }

        connectionStateFlow.value = ConnectionState.Connected
        commandSender.processQueuedPackets()

        verify(exactly = 0) { packetHandler.sendToRadio(any<org.meshtastic.proto.MeshPacket>()) }
    }
}
