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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.NodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SdkTopologyBridgeTest {

    @Test
    fun `neighbor info packet updates topology graph`() = runTest {
        val topologyService = MeshTopologyService()
        val bridge = SdkTopologyBridge(topologyService)

        bridge.handleNeighborInfoPacket(
            neighborInfoPacket(
                from = 0x11111111,
                info = NeighborInfo(
                    last_sent_by_id = 1234,
                    neighbors = listOf(
                        Neighbor(node_id = 0x22222222, snr = 7.5f),
                        Neighbor(node_id = 0x33333333, snr = -2.25f),
                    ),
                ),
            ),
        )

        val edges = topologyService.edges.value
        assertEquals(2, edges.size)
        assertEquals(3, topologyService.nodeCount.value)
        assertEquals(NodeId(0x11111111), edges[0].from)
        assertEquals(NodeId(0x22222222), edges[0].to)
        assertEquals(7.5f, edges[0].snr)
        assertEquals(1234, edges[0].lastUpdated)
        assertEquals(NodeId(0x33333333), edges[1].to)
    }

    @Test
    fun `malformed proto is handled without crashing`() = runTest {
        val topologyService = MeshTopologyService()
        val bridge = SdkTopologyBridge(topologyService)

        bridge.handleNeighborInfoPacket(
            MeshPacket(
                from = 0x44444444,
                decoded = Data(
                    portnum = PortNum.NEIGHBORINFO_APP,
                    payload = byteArrayOf(0x01, 0x02, 0x03).toByteString(),
                ),
            ),
        )

        assertTrue(topologyService.edges.value.isEmpty())
        assertEquals(0, topologyService.nodeCount.value)
    }

    @Test
    fun `empty neighbor list tracks node without edges`() = runTest {
        val topologyService = MeshTopologyService()
        val bridge = SdkTopologyBridge(topologyService)

        bridge.handleNeighborInfoPacket(
            neighborInfoPacket(
                from = 0x55555555,
                info = NeighborInfo(last_sent_by_id = 999, neighbors = emptyList()),
            ),
        )

        assertTrue(topologyService.edges.value.isEmpty())
        assertEquals(1, topologyService.nodeCount.value)
    }

    @Test
    fun `subsequent reports replace edges from the same reporter`() = runTest {
        val topologyService = MeshTopologyService()
        val bridge = SdkTopologyBridge(topologyService)

        bridge.handleNeighborInfoPacket(
            neighborInfoPacket(
                from = 0x66666666,
                info = NeighborInfo(
                    neighbors = listOf(
                        Neighbor(node_id = 0x11110000, snr = 1f),
                        Neighbor(node_id = 0x22220000, snr = 2f),
                    ),
                ),
            ),
        )
        bridge.handleNeighborInfoPacket(
            neighborInfoPacket(
                from = 0x66666666,
                info = NeighborInfo(neighbors = listOf(Neighbor(node_id = 0x33330000, snr = 3f))),
            ),
        )

        val edges = topologyService.edges.value
        assertEquals(1, edges.size)
        assertEquals(NodeId(0x33330000), edges.single().to)
        assertEquals(2, topologyService.nodeCount.value)
    }

    @Test
    fun `empty payload tracks reporter without edges`() = runTest {
        val topologyService = MeshTopologyService()
        val bridge = SdkTopologyBridge(topologyService)

        bridge.handleNeighborInfoPacket(MeshPacket(from = 0x77777777, decoded = Data(portnum = PortNum.NEIGHBORINFO_APP)))

        assertTrue(topologyService.edges.value.isEmpty())
        assertEquals(1, topologyService.nodeCount.value)
    }

    private fun neighborInfoPacket(from: Int, info: NeighborInfo) =
        MeshPacket(
            from = from,
            decoded = Data(
                portnum = PortNum.NEIGHBORINFO_APP,
                payload = NeighborInfo.ADAPTER.encode(info).toByteString(),
            ),
        )
}
