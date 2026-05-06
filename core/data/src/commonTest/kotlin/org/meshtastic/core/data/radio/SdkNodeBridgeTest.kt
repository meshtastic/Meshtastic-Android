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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString as KByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.StorageProvider
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SdkNodeBridgeTest {

    @Test
    fun `snapshot clears repository and reinstalls nodes`() = runTest {
        val nodeRepository = RecordingNodeRepository().apply {
            setNodes(
                listOf(
                    Node(num = 0xAAAA0001.toInt(), user = User(id = "!AAAA0001", long_name = "stale")),
                ),
            )
        }
        val topologyService = MeshTopologyService().apply {
            ingestNeighborInfo(
                org.meshtastic.sdk.NeighborInfo(
                    nodeId = NodeId(1),
                    neighbors = listOf(org.meshtastic.sdk.NeighborInfo.Neighbor(NodeId(2), 7.5f)),
                ),
            )
        }
        val bridge = SdkNodeBridge(nodeRepository, topologyService)
        val first = nodeInfo(0x11111111, "!11111111", "Alpha")
        val second = nodeInfo(0x22222222, "!22222222", "Bravo")

        bridge.handleNodeChange(
            NodeChange.Snapshot(
                mapOf(
                    NodeId(first.num) to first,
                    NodeId(second.num) to second,
                ),
            ),
        )

        assertEquals(1, nodeRepository.clearCalls)
        assertEquals(listOf(false, false), nodeRepository.installCalls.map { it.second })
        assertEquals(setOf(first.num, second.num), nodeRepository.nodeDBbyNum.value.keys)
        assertTrue(nodeRepository.isNodeDbReady.value)
        assertTrue(topologyService.edges.value.isEmpty())
        assertEquals(0, topologyService.nodeCount.value)
    }

    @Test
    fun `added event installs node with broadcast enabled`() = runTest {
        val nodeRepository = RecordingNodeRepository()
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())
        val added = nodeInfo(0x33333333, "!33333333", "Added")

        bridge.handleNodeChange(NodeChange.Added(added))

        assertEquals(listOf(true), nodeRepository.installCalls.map { it.second })
        assertTrue(nodeRepository.nodeDBbyNum.value.containsKey(added.num))
    }

    @Test
    fun `updated event reinstalls node with broadcast enabled`() = runTest {
        val nodeRepository = RecordingNodeRepository()
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())
        val updated = nodeInfo(0x44444444, "!44444444", "Updated")

        bridge.handleNodeChange(NodeChange.Updated(updated, emptySet()))

        assertEquals(listOf(true), nodeRepository.installCalls.map { it.second })
        assertTrue(nodeRepository.nodeDBbyNum.value.containsKey(updated.num))
    }

    @Test
    fun `removed event deletes node from repository`() = runTest {
        val nodeNum = 0x55555555
        val nodeRepository = RecordingNodeRepository().apply {
            setNodes(listOf(Node(num = nodeNum, user = User(id = "!55555555", long_name = "Gone"))))
        }
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())

        bridge.handleNodeChange(NodeChange.Removed(NodeId(nodeNum)))

        assertEquals(listOf(nodeNum), nodeRepository.removeCalls)
        assertFalse(nodeRepository.nodeDBbyNum.value.containsKey(nodeNum))
    }

    @Test
    fun `went offline updates last heard and marks node offline`() = runTest {
        val nodeNum = 0x66666666
        val nodeRepository = RecordingNodeRepository().apply {
            setNodes(
                listOf(
                    Node(
                        num = nodeNum,
                        user = User(id = "!66666666", long_name = "Offline"),
                        lastHeard = Clock.System.now().epochSeconds.toInt(),
                    ),
                ),
            )
        }
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())
        val staleLastHeard = onlineTimeThreshold() - 20

        bridge.handleNodeChange(NodeChange.WentOffline(NodeId(nodeNum), staleLastHeard))

        val updated = nodeRepository.nodeDBbyNum.value.getValue(nodeNum)
        assertEquals(minOf(Clock.System.now().epochSeconds.toInt(), staleLastHeard, onlineTimeThreshold()), updated.lastHeard)
        assertFalse(updated.isOnline)
    }

    @Test
    fun `went offline ignores unknown node`() = runTest {
        val nodeRepository = RecordingNodeRepository()
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())

        bridge.handleNodeChange(NodeChange.WentOffline(NodeId(0x77777777), onlineTimeThreshold() - 10))

        assertTrue(nodeRepository.nodeDBbyNum.value.isEmpty())
    }

    @Test
    fun `came online updates last heard and marks node online`() = runTest {
        val nodeNum = 0x88888888.toInt()
        val nodeRepository = RecordingNodeRepository().apply {
            setNodes(
                listOf(
                    Node(
                        num = nodeNum,
                        user = User(id = "!88888888", long_name = "Online"),
                        lastHeard = onlineTimeThreshold() - 120,
                    ),
                ),
            )
        }
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())

        bridge.handleNodeChange(NodeChange.CameOnline(NodeId(nodeNum)))

        val updated = nodeRepository.nodeDBbyNum.value.getValue(nodeNum)
        assertTrue(updated.lastHeard >= onlineTimeThreshold())
        assertTrue(updated.isOnline)
    }

    @Test
    fun `came online ignores unknown node`() = runTest {
        val nodeRepository = RecordingNodeRepository()
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())

        bridge.handleNodeChange(NodeChange.CameOnline(NodeId(0x99999999.toInt())))

        assertTrue(nodeRepository.nodeDBbyNum.value.isEmpty())
    }

    @Test
    fun `own node discovered sets my node num`() = runTest {
        val myNodeNum = 0x12345678
        val nodeRepository = RecordingNodeRepository()
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())
        val (transport, client) = connectedClient(myNodeNum = myNodeNum)

        bridge.observe(TestRadioClientAccessor(client), backgroundScope)
        client.connect()
        runCurrent()

        transport.injectFrame(encodeFromRadio(FromRadio(node_info = nodeInfo(myNodeNum, "!12345678", "Self"))))
        runCurrent()

        assertEquals(myNodeNum, nodeRepository.myNodeNum.value)
        client.disconnect()
    }

    @Test
    fun `node status packet populates node status`() = runTest {
        val nodeNum = 0xABCDEF01.toInt()
        val nodeRepository = RecordingNodeRepository().apply {
            setNodes(listOf(Node(num = nodeNum, user = User(id = "!ABCDEF01", long_name = "Status"))))
        }
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())

        bridge.handleNodeStatusPacket(
            MeshPacket(
                from = nodeNum,
                decoded = Data(
                    portnum = PortNum.NODE_STATUS_APP,
                    payload = "nomad active".encodeToByteArray().toByteString(),
                ),
            ),
        )

        assertEquals("nomad active", nodeRepository.nodeDBbyNum.value.getValue(nodeNum).nodeStatus)
    }

    @Test
    fun `node status packet with empty payload stores empty status`() = runTest {
        val nodeNum = 0x0BADF00D
        val nodeRepository = RecordingNodeRepository().apply {
            setNodes(listOf(Node(num = nodeNum, user = User(id = "!0BADF00D", long_name = "Status"))))
        }
        val bridge = SdkNodeBridge(nodeRepository, MeshTopologyService())

        bridge.handleNodeStatusPacket(
            MeshPacket(
                from = nodeNum,
                decoded = Data(portnum = PortNum.NODE_STATUS_APP),
            ),
        )

        assertEquals("", nodeRepository.nodeDBbyNum.value.getValue(nodeNum).nodeStatus)
    }

    private fun TestScope.connectedClient(
        storage: StorageProvider = NodeBridgeSeededHeartbeatStorageProvider(emptyMap()),
        myNodeNum: Int = 0x11111111,
        presenceTimeout: Duration = 1.seconds,
    ): Pair<FakeRadioTransport, RadioClient> {
        val transport = FakeRadioTransport(identity = TransportIdentity("fake:node-bridge"), autoHandshake = true, nodeNum = myNodeNum)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(storage)
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .presenceTimeout(presenceTimeout)
                .build()
        return transport to client
    }

    private fun nodeInfo(num: Int, id: String, longName: String) =
        NodeInfo(
            num = num,
            user = User(id = id, long_name = longName, short_name = longName.take(4)),
        )

    private fun encodeFromRadio(fromRadio: FromRadio): Frame {
        val proto = FromRadio.ADAPTER.encode(fromRadio)
        val frameBytes = ByteArray(4 + proto.size).apply {
            this[0] = 0x94.toByte()
            this[1] = 0xC3.toByte()
            this[2] = (proto.size shr 8).toByte()
            this[3] = (proto.size and 0xFF).toByte()
            proto.copyInto(this, destinationOffset = 4)
        }
        return Frame(KByteString(frameBytes))
    }

    private class RecordingNodeRepository(
        private val delegate: FakeNodeRepository = FakeNodeRepository(),
    ) : NodeRepository by delegate {
        val installCalls = mutableListOf<Pair<NodeInfo, Boolean>>()
        val removeCalls = mutableListOf<Int>()
        var clearCalls = 0

        override fun clear() {
            clearCalls += 1
            delegate.clear()
        }

        override fun installNodeInfo(info: NodeInfo, withBroadcast: Boolean) {
            installCalls += info to withBroadcast
            delegate.installNodeInfo(info, withBroadcast)
        }

        override fun removeByNodenum(nodeNum: Int) {
            removeCalls += nodeNum
            delegate.removeByNodenum(nodeNum)
        }

        fun setNodes(nodes: List<Node>) {
            delegate.setNodes(nodes)
        }
    }

    private class TestRadioClientAccessor(client: RadioClient) : RadioClientAccessor {
        override val client = MutableStateFlow<RadioClient?>(client)

        override fun rebuildAndConnectAsync() = Unit

        override fun disconnect() = Unit
    }
}

private class NodeBridgeSeededHeartbeatStorageProvider(
    private val heartbeats: Map<NodeId, Long>,
) : StorageProvider {
    override suspend fun activate(identity: TransportIdentity) =
        InMemoryStorage().also { storage ->
            heartbeats.forEach { (nodeId, heartbeatMs) ->
                storage.saveHeartbeat(nodeId, heartbeatMs)
            }
        }
}
