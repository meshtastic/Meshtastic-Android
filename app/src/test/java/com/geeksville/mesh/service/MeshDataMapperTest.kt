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

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User

class MeshDataMapperTest {

    private lateinit var dataMapper: MeshDataMapper
    private lateinit var nodeManager: MeshNodeManager

    @Before
    fun setUp() {
        nodeManager = MeshNodeManager() // Use internal testing constructor
        dataMapper = MeshDataMapper(nodeManager)
    }

    @Test
    fun `toNodeID returns broadcast ID for broadcast num`() {
        assertEquals(DataPacket.ID_BROADCAST, dataMapper.toNodeID(DataPacket.NODENUM_BROADCAST))
    }

    @Test
    fun `toNodeID returns user ID from node database`() {
        val nodeNum = 123
        val userId = "!0000007b" // hex for 123
        nodeManager.nodeDBbyNodeNum[nodeNum] = NodeEntity(num = nodeNum, user = User(id = userId))

        assertEquals(userId, dataMapper.toNodeID(nodeNum))
    }

    @Test
    fun `toNodeID returns default ID if node not in database`() {
        val nodeNum = 123
        val expectedId = "!0000007b"
        assertEquals(expectedId, dataMapper.toNodeID(nodeNum))
    }

    @Test
    fun `toDataPacket returns null if no decoded payload`() {
        val packet = MeshPacket()
        assertNull(dataMapper.toDataPacket(packet))
    }

    @Test
    fun `toDataPacket correctly maps protobuf to DataPacket`() {
        val payload = "Hello".encodeToByteArray().toByteString()
        val packet =
            MeshPacket(
                from = 1,
                to = 2,
                id = 12345,
                rx_time = 1600000000,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = payload),
            )

        val dataPacket = dataMapper.toDataPacket(packet)

        assertEquals("!00000001", dataPacket?.from)
        assertEquals("!00000002", dataPacket?.to)
        assertEquals(12345, dataPacket?.id)
        assertEquals(1600000000000L, dataPacket?.time)
        assertEquals(PortNum.TEXT_MESSAGE_APP.value, dataPacket?.dataType)
        assertEquals("Hello", dataPacket?.bytes?.decodeToString())
    }
}
