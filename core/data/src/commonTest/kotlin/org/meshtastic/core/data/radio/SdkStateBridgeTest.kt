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

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import org.meshtastic.sdk.DeviceStorage
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.StorageProvider
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SdkStateBridgeTest {

    @Test
    fun `went offline marks node offline in repository`() = runTest {
        val remoteNode = NodeId(0x22222222)
        val staleHeartbeatMs = Clock.System.now().toEpochMilliseconds() - 5.seconds.inWholeMilliseconds
        val nodeRepository =
            FakeNodeRepository().apply {
                setNodes(
                    listOf(
                        Node(
                            num = remoteNode.raw,
                            user = User(id = "!22222222", long_name = "Test Node"),
                            lastHeard = (Clock.System.now().toEpochMilliseconds() / 1000).toInt(),
                        ),
                    ),
                )
            }
        val (_, client) = connectedClient(SeededHeartbeatStorageProvider(mapOf(remoteNode to staleHeartbeatMs)))
        buildBridge(client, nodeRepository)

        client.connect()
        runCurrent()
        advanceTimeBy(30.seconds)
        runCurrent()

        val updated = nodeRepository.nodeDBbyNum.value.getValue(remoteNode.raw)
        assertTrue(updated.lastHeard <= (staleHeartbeatMs / 1000).toInt())
        assertFalse(updated.isOnline)

        client.disconnect()
    }

    @Test
    fun `came online marks node online in repository`() = runTest {
        val remoteNode = NodeId(0x33333333)
        val staleHeartbeatMs = Clock.System.now().toEpochMilliseconds() - 5.seconds.inWholeMilliseconds
        val nodeRepository =
            FakeNodeRepository().apply {
                setNodes(
                    listOf(
                        Node(
                            num = remoteNode.raw,
                            user = User(id = "!33333333", long_name = "Test Node"),
                            lastHeard = (staleHeartbeatMs / 1000).toInt(),
                        ),
                    ),
                )
            }
        val (transport, client) = connectedClient(SeededHeartbeatStorageProvider(mapOf(remoteNode to staleHeartbeatMs)))
        buildBridge(client, nodeRepository)

        client.connect()
        runCurrent()
        advanceTimeBy(30.seconds)
        runCurrent()

        transport.injectPacket(
            MeshPacket(
                from = remoteNode.raw,
                to = 0,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP),
            ),
        )
        runCurrent()

        val updated = nodeRepository.nodeDBbyNum.value.getValue(remoteNode.raw)
        assertTrue(updated.lastHeard > (staleHeartbeatMs / 1000).toInt())
        assertTrue(updated.isOnline)

        client.disconnect()
    }

    private fun TestScope.connectedClient(
        storage: StorageProvider,
        myNodeNum: Int = 0x11111111,
        presenceTimeout: Duration = 1.seconds,
    ): Pair<FakeRadioTransport, RadioClient> {
        val transport = FakeRadioTransport(identity = TransportIdentity("fake:state-bridge"), autoHandshake = true, nodeNum = myNodeNum)
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

    private fun TestScope.buildBridge(
        client: RadioClient,
        nodeRepository: FakeNodeRepository,
    ): SdkStateBridge =
        SdkStateBridge(
            accessor = TestRadioClientAccessor(client),
            serviceRepository = FakeServiceRepository(),
            nodeRepository = nodeRepository,
            packetRepository = lazyOf(mock<PacketRepository>(MockMode.autofill)),
            locationManager = NoOpLocationManager,
            uiPrefs = FakeUiPrefs(),
            radioController = FakeRadioController(),
            dispatchers = CoroutineDispatchers(
                io = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
                main = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
                default = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
            ),
        )

    private class TestRadioClientAccessor(client: RadioClient) : RadioClientAccessor {
        override val client = MutableStateFlow<RadioClient?>(client)

        override fun rebuildAndConnectAsync() = Unit

        override fun disconnect() = Unit
    }

    private object NoOpLocationManager : MeshLocationManager {
        override fun start(scope: CoroutineScope, sendPositionFn: (Position) -> Unit) = Unit

        override fun stop() = Unit
    }
}

private class SeededHeartbeatStorageProvider(
    private val heartbeats: Map<NodeId, Long>,
) : StorageProvider {
    override suspend fun activate(identity: TransportIdentity): DeviceStorage =
        InMemoryStorage().also { storage ->
            heartbeats.forEach { (nodeId, heartbeatMs) ->
                storage.saveHeartbeat(nodeId, heartbeatMs)
            }
        }
}
