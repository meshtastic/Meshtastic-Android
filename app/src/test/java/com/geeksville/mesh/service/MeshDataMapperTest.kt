/*
 * Copyright (c) 2025 Meshtastic LLC
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

import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.user

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
        nodeManager.nodeDBbyNodeNum[nodeNum] = NodeEntity(num = nodeNum, user = user { id = userId })

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
        val packet = MeshProtos.MeshPacket.newBuilder().build()
        assertNull(dataMapper.toDataPacket(packet))
    }

    @Test
    fun `toDataPacket correctly maps protobuf to DataPacket`() {
        val payload = "Hello".encodeToByteArray()
        val packet =
            MeshProtos.MeshPacket.newBuilder()
                .apply {
                    from = 1
                    to = 2
                    id = 12345
                    rxTime = 1600000000
                    decoded =
                        MeshProtos.Data.newBuilder()
                            .apply {
                                portnumValue = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE
                                setPayload(ByteString.copyFrom(payload))
                            }
                            .build()
                }
                .build()

        val dataPacket = dataMapper.toDataPacket(packet)

        assertEquals("!00000001", dataPacket?.from)
        assertEquals("!00000002", dataPacket?.to)
        assertEquals(12345, dataPacket?.id)
        assertEquals(1600000000000L, dataPacket?.time)
        assertEquals(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE, dataPacket?.dataType)
        assertEquals("Hello", dataPacket?.bytes?.decodeToString())
    }
}
