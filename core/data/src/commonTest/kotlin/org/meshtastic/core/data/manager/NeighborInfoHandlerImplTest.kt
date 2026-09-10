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
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.flow.MutableStateFlow
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NeighborInfoHandlerImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val nodeRepository = mock<NodeRepository>(MockMode.autofill)

    private lateinit var handler: NeighborInfoHandlerImpl

    private val myNodeNum = 12345

    @BeforeTest
    fun setUp() {
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(myNodeNum)
        handler = NeighborInfoHandlerImpl(nodeManager, serviceRepository, nodeRepository)
    }

    @Test
    fun `handleNeighborInfo stores lastNeighborInfo when from own node`() {
        val ni = NeighborInfo.Builder().also { wb ->wb.node_id = myNodeNum; wb.neighbors = listOf(Neighbor.Builder().also { wb ->wb.node_id = 100; wb.snr = 5.0f}.build())}.build()
        val packet = createPacketWithNeighborInfo(from = myNodeNum, ni = ni)

        every { nodeRepository.getUser(100) } returns User.Builder().also { wb ->wb.long_name = "Alice"; wb.short_name = "AL"}.build()
        every { nodeRepository.getUser(myNodeNum) } returns User.Builder().also { wb ->wb.long_name = "Me"; wb.short_name = "ME"}.build()

        handler.handleNeighborInfo(packet)

        assertEquals(ni, handler.lastNeighborInfo)
    }

    @Test
    fun `handleNeighborInfo does not store lastNeighborInfo when from remote node`() {
        val remoteNode = 99999
        val ni = NeighborInfo.Builder().also { wb ->wb.node_id = remoteNode; wb.neighbors = listOf(Neighbor.Builder().also { wb ->wb.node_id = 200; wb.snr = 3.0f}.build())}.build()
        val packet = createPacketWithNeighborInfo(from = remoteNode, ni = ni)

        every { nodeRepository.getUser(200) } returns User.Builder().also { wb ->wb.long_name = "Bob"; wb.short_name = "BO"}.build()
        every { nodeRepository.getUser(remoteNode) } returns User.Builder().also { wb ->wb.long_name = "Remote"; wb.short_name = "RM"}.build()

        handler.handleNeighborInfo(packet)

        assertNull(handler.lastNeighborInfo)
    }

    @Test
    fun `handleNeighborInfo sets response on serviceRepository`() {
        val ni =
            NeighborInfo.Builder().also { wb ->
            wb.node_id = myNodeNum
            wb.neighbors = listOf(Neighbor.Builder().also { wb ->wb.node_id = 100; wb.snr = 5.5f}.build(), Neighbor.Builder().also { wb ->wb.node_id = 200; wb.snr = -2.0f}.build())
            }.build()
        val packet = createPacketWithNeighborInfo(from = myNodeNum, ni = ni)

        every { nodeRepository.getUser(100) } returns User.Builder().also { wb ->wb.long_name = "Alice"; wb.short_name = "AL"}.build()
        every { nodeRepository.getUser(200) } returns User.Builder().also { wb ->wb.long_name = "Bob"; wb.short_name = "BO"}.build()
        every { nodeRepository.getUser(myNodeNum) } returns User.Builder().also { wb ->wb.long_name = "Me"; wb.short_name = "ME"}.build()

        handler.handleNeighborInfo(packet)

        verify { serviceRepository.setNeighborInfoResponse(any()) }
    }

    @Test
    fun `handleNeighborInfo ignores packet with null decoded`() {
        val packet = MeshPacket.Builder().also { wb ->wb.from = myNodeNum}.build()
        handler.handleNeighborInfo(packet)
        assertNull(handler.lastNeighborInfo)
    }

    @Test
    fun `recordStartTime and handleNeighborInfo includes duration`() {
        val requestId = 42
        val ni = NeighborInfo.Builder().also { wb ->wb.node_id = myNodeNum; wb.neighbors = listOf(Neighbor.Builder().also { wb ->wb.node_id = 100; wb.snr = 1.0f}.build())}.build()
        val packet = createPacketWithNeighborInfo(from = myNodeNum, ni = ni, requestId = requestId)

        every { nodeRepository.getUser(100) } returns User.Builder().also { wb ->wb.long_name = "Alice"; wb.short_name = "AL"}.build()
        every { nodeRepository.getUser(myNodeNum) } returns User.Builder().also { wb ->wb.long_name = "Me"; wb.short_name = "ME"}.build()

        handler.recordStartTime(requestId)
        handler.handleNeighborInfo(packet)

        verify { serviceRepository.setNeighborInfoResponse(any()) }
    }

    private fun createPacketWithNeighborInfo(from: Int, ni: NeighborInfo, requestId: Int = 0): MeshPacket {
        val encoded = NeighborInfo.ADAPTER.encode(ni).toByteString()
        return MeshPacket.Builder().also { wb ->wb.from = from; wb.decoded = Data.Builder().also { wb ->wb.payload = encoded; wb.request_id = requestId}.build()}.build()
    }
}
