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
        val ni = NeighborInfo(node_id = myNodeNum, neighbors = listOf(Neighbor(node_id = 100, snr = 5.0f)))
        val packet = createPacketWithNeighborInfo(from = myNodeNum, ni = ni)

        every { nodeRepository.getUser(100) } returns User(long_name = "Alice", short_name = "AL")
        every { nodeRepository.getUser(myNodeNum) } returns User(long_name = "Me", short_name = "ME")

        handler.handleNeighborInfo(packet)

        assertEquals(ni, handler.lastNeighborInfo)
    }

    @Test
    fun `handleNeighborInfo does not store lastNeighborInfo when from remote node`() {
        val remoteNode = 99999
        val ni = NeighborInfo(node_id = remoteNode, neighbors = listOf(Neighbor(node_id = 200, snr = 3.0f)))
        val packet = createPacketWithNeighborInfo(from = remoteNode, ni = ni)

        every { nodeRepository.getUser(200) } returns User(long_name = "Bob", short_name = "BO")
        every { nodeRepository.getUser(remoteNode) } returns User(long_name = "Remote", short_name = "RM")

        handler.handleNeighborInfo(packet)

        assertNull(handler.lastNeighborInfo)
    }

    @Test
    fun `handleNeighborInfo sets response on serviceRepository`() {
        val ni =
            NeighborInfo(
                node_id = myNodeNum,
                neighbors = listOf(Neighbor(node_id = 100, snr = 5.5f), Neighbor(node_id = 200, snr = -2.0f)),
            )
        val packet = createPacketWithNeighborInfo(from = myNodeNum, ni = ni)

        every { nodeRepository.getUser(100) } returns User(long_name = "Alice", short_name = "AL")
        every { nodeRepository.getUser(200) } returns User(long_name = "Bob", short_name = "BO")
        every { nodeRepository.getUser(myNodeNum) } returns User(long_name = "Me", short_name = "ME")

        handler.handleNeighborInfo(packet)

        verify { serviceRepository.setNeighborInfoResponse(any()) }
    }

    @Test
    fun `handleNeighborInfo ignores packet with null decoded`() {
        val packet = MeshPacket(from = myNodeNum)
        handler.handleNeighborInfo(packet)
        assertNull(handler.lastNeighborInfo)
    }

    @Test
    fun `recordStartTime and handleNeighborInfo includes duration`() {
        val requestId = 42
        val ni = NeighborInfo(node_id = myNodeNum, neighbors = listOf(Neighbor(node_id = 100, snr = 1.0f)))
        val packet = createPacketWithNeighborInfo(from = myNodeNum, ni = ni, requestId = requestId)

        every { nodeRepository.getUser(100) } returns User(long_name = "Alice", short_name = "AL")
        every { nodeRepository.getUser(myNodeNum) } returns User(long_name = "Me", short_name = "ME")

        handler.recordStartTime(requestId)
        handler.handleNeighborInfo(packet)

        verify { serviceRepository.setNeighborInfoResponse(any()) }
    }

    private fun createPacketWithNeighborInfo(from: Int, ni: NeighborInfo, requestId: Int = 0): MeshPacket {
        val encoded = NeighborInfo.ADAPTER.encode(ni).toByteString()
        return MeshPacket(from = from, decoded = Data(payload = encoded, request_id = requestId))
    }
}
