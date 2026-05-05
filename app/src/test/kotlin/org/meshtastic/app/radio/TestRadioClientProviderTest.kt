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
package org.meshtastic.app.radio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.decodeAsNodeInfo
import org.meshtastic.sdk.testing.FakeRadioTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class TestRadioClientProviderTest {

    @Test
    fun connectInjectNodeAndDisconnect() = runTest {
        val provider = TestRadioClientProvider(coroutineContext = backgroundScope.coroutineContext)

        provider.connect()
        assertEquals(ConnectionState.Connected, provider.client.connection.value)

        val nodeInfo = NodeInfo(
            num = 0x1234,
            user = User(
                id = "!00001234",
                long_name = "Test Node",
                short_name = "TN",
            ),
        )

        val packetAwaiter = backgroundScope.async {
            provider.client.packets.first { packet ->
                packet.from == nodeInfo.num && packet.decodeAsNodeInfo()?.num == nodeInfo.num
            }
        }
        runCurrent()

        provider.transport.injectPacket(
            MeshPacket(
                from = nodeInfo.num,
                to = provider.nodeNum,
                decoded = Data(
                    portnum = PortNum.NODEINFO_APP,
                    payload = NodeInfo.ADAPTER.encode(nodeInfo).toByteString(),
                ),
            ),
        )
        runCurrent()
        runCurrent()

        assertEquals(nodeInfo.num, packetAwaiter.await().decodeAsNodeInfo()?.num)

        val nodeAwaiter = backgroundScope.async {
            provider.client.nodes.first { change ->
                change is NodeChange.Added && change.node.num == nodeInfo.num
            }
        }
        runCurrent()

        provider.transport.injectNodeInfo(nodeInfo)
        runCurrent()
        runCurrent()

        val added = assertIs<NodeChange.Added>(nodeAwaiter.await())
        assertEquals(nodeInfo.num, added.node.num)
        assertEquals(nodeInfo.user?.long_name, provider.client.nodeSnapshot()[NodeId(nodeInfo.num)]?.user?.long_name)

        provider.disconnect()
        assertEquals(ConnectionState.Disconnected, provider.client.connection.value)
        assertEquals(TransportState.Disconnected, provider.transport.state.value)
    }

    private fun FakeRadioTransport.injectNodeInfo(nodeInfo: NodeInfo) {
        val proto = FromRadio.ADAPTER.encode(FromRadio(node_info = nodeInfo))
        val frame = ByteArray(4 + proto.size).apply {
            this[0] = 0x94.toByte()
            this[1] = 0xC3.toByte()
            this[2] = (proto.size shr 8).toByte()
            this[3] = (proto.size and 0xFF).toByte()
            proto.copyInto(this, destinationOffset = 4)
        }
        injectFrame(Frame(ByteString(frame)))
    }
}
