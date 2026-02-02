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
package com.geeksville.mesh.service

import io.mockk.every
import io.mockk.mockk
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum

class MeshDataMapperTest {

    private val nodeManager: MeshNodeManager = mockk()
    private lateinit var mapper: MeshDataMapper

    @Before
    fun setUp() {
        mapper = MeshDataMapper(nodeManager)
    }

    @Test
    fun `toNodeID resolves broadcast correctly`() {
        assertEquals(DataPacket.ID_BROADCAST, mapper.toNodeID(DataPacket.NODENUM_BROADCAST))
    }

    @Test
    fun `toNodeID resolves known node correctly`() {
        val nodeNum = 1234
        val nodeId = "!1234abcd"
        val nodeEntity = mockk<NodeEntity>()
        every { nodeEntity.user.id } returns nodeId
        every { nodeManager.nodeDBbyNodeNum[nodeNum] } returns nodeEntity

        assertEquals(nodeId, mapper.toNodeID(nodeNum))
    }

    @Test
    fun `toNodeID resolves unknown node to default ID`() {
        val nodeNum = 1234
        every { nodeManager.nodeDBbyNodeNum[nodeNum] } returns null

        assertEquals(DataPacket.nodeNumToDefaultId(nodeNum), mapper.toNodeID(nodeNum))
    }

    @Test
    fun `toDataPacket returns null when no decoded data`() {
        val packet = MeshPacket()
        assertNull(mapper.toDataPacket(packet))
    }

    @Test
    fun `toDataPacket maps basic fields correctly`() {
        val nodeNum = 1234
        val nodeId = "!1234abcd"
        val nodeEntity = mockk<NodeEntity>()
        every { nodeEntity.user.id } returns nodeId
        every { nodeManager.nodeDBbyNodeNum[any()] } returns nodeEntity

        val proto =
            MeshPacket(
                id = 42,
                from = nodeNum,
                to = DataPacket.NODENUM_BROADCAST,
                rx_time = 1600000000,
                rx_snr = 5.5f,
                rx_rssi = -100,
                hop_limit = 3,
                hop_start = 3,
                decoded =
                Data(
                    portnum = PortNum.TEXT_MESSAGE_APP,
                    payload = "hello".encodeToByteArray().toByteString(),
                    reply_id = 123,
                ),
            )

        val result = mapper.toDataPacket(proto)
        assertNotNull(result)
        assertEquals(42, result!!.id)
        assertEquals(nodeId, result.from)
        assertEquals(DataPacket.ID_BROADCAST, result.to)
        assertEquals(1600000000000L, result.time)
        assertEquals(5.5f, result.snr)
        assertEquals(-100, result.rssi)
        assertEquals(PortNum.TEXT_MESSAGE_APP.value, result.dataType)
        assertEquals("hello", result.bytes?.utf8())
        assertEquals(123, result.replyId)
    }

    @Test
    fun `toDataPacket maps PKC channel correctly for encrypted packets`() {
        val proto = MeshPacket(pki_encrypted = true, channel = 1, decoded = Data())

        every { nodeManager.nodeDBbyNodeNum[any()] } returns null

        val result = mapper.toDataPacket(proto)
        assertEquals(DataPacket.PKC_CHANNEL_INDEX, result!!.channel)
    }
}
