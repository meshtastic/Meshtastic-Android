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

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.data.repository.PacketRepositoryImpl
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.toFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/** Queue receipts for a message sent from the UI must settle the row the UI stored, through the real DAO match. */
@OptIn(ExperimentalCoroutinesApi::class)
class PacketHandlerRoomStatusTest {
    @Test
    fun `an accepted UI message goes ENROUTE in Room and times out if no ACK follows`() = runTest {
        val fixture = fixture()
        val (id, packet) = fixture.saveUiMessage(packetId = 901)

        fixture.handler.sendData(packet)
        runCurrent()
        fixture.queueStatus(901, result = 0)
        runCurrent()
        assertEquals(MessageStatus.ENROUTE, fixture.repository.getPacketByPersistedId(id)?.status)

        advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT)
        runCurrent()
        val row = fixture.db.packetDao().getPacketByPersistedId(id.myNodeNum, id.uuid)
        assertEquals(MessageStatus.ERROR, row?.data?.status)
        assertEquals(Routing.Error.TIMEOUT.value, row?.routingError)
        fixture.client.disconnect()
        fixture.db.close()
    }

    @Test
    fun `a queue rejection of a UI message marks its Room row failed`() = runTest {
        val fixture = fixture()
        val (id, packet) = fixture.saveUiMessage(packetId = 902)

        fixture.handler.sendData(packet)
        runCurrent()
        // ERRNO_UNKNOWN in firmware's MeshTypes.h.
        fixture.queueStatus(902, result = 32)
        runCurrent()

        assertEquals(MessageStatus.ERROR, fixture.repository.getPacketByPersistedId(id)?.status)
        fixture.client.disconnect()
        fixture.db.close()
    }

    private suspend fun TestScope.fixture(): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val db = getInMemoryDatabaseBuilder().setQueryCoroutineContext(dispatcher).build()
        // Persisted-row reads are scoped to the selected radio's my_node row.
        db.nodeInfoDao()
            .setMyNodeInfo(
                MyNodeEntity(
                    myNodeNum = LOCAL_NODE,
                    model = null,
                    firmwareVersion = null,
                    couldUpdate = false,
                    shouldUpdate = false,
                    currentPacketId = 0L,
                    messageTimeoutMsec = 0,
                    minAppVersion = 0,
                    maxChannels = 8,
                    hasWifi = false,
                ),
            )
        val repository =
            PacketRepositoryImpl(SingleDatabaseProvider(db), CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        val transport =
            FakeRadioTransport(TransportIdentity("fake:outgoing"), autoHandshake = true, nodeNum = LOCAL_NODE)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .build()
        client.connect()
        val radio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress("tselected")
                connect()
                this.client = client
            }
        val radioService =
            object : RadioInterfaceService by radio {
                override fun sendPaced(session: RadioSessionContext, packet: MeshPacket): PacedMessageHandle? =
                    if (radio.isSessionActive(session)) client.sendPaced(packet) else null
            }
        val nodeManager =
            mock<NodeManager>().also {
                every { it.myNodeNum } returns MutableStateFlow(LOCAL_NODE)
                every { it.nodeDBbyNodeNum } returns emptyMap()
            }
        val handler =
            PacketHandlerImpl(
                lazy { repository },
                radioService,
                FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) },
                nodeManager,
                backgroundScope.asServiceScope(),
            )
        return Fixture(handler, repository, db, transport, client)
    }

    private class Fixture(
        val handler: PacketHandlerImpl,
        val repository: PacketRepositoryImpl,
        val db: MeshtasticDatabase,
        val transport: FakeRadioTransport,
        val client: RadioClient,
    ) {
        /** Stores a text the way SendMessageUseCase does: sender is our "!id", status QUEUED. */
        suspend fun saveUiMessage(packetId: Int): Pair<PersistedPacketId, DataPacket> {
            val peer = NodeAddress.numToDefaultId(PEER)
            val packet =
                DataPacket(peer, 0, "hello", null).apply {
                    from = NodeAddress.numToDefaultId(LOCAL_NODE)
                    id = packetId
                    status = MessageStatus.QUEUED
                }
            return repository.savePacket(LOCAL_NODE, "0$peer", packet, nowMillis) to packet
        }

        fun queueStatus(id: Int, result: Int) {
            val status =
                QueueStatus.Builder()
                    .also {
                        it.mesh_packet_id = id
                        it.res = result
                    }
                    .build()
            transport.injectFrame(FromRadio.Builder().also { it.queueStatus = status }.build().toFrame())
        }
    }

    private class SingleDatabaseProvider(private val db: MeshtasticDatabase) : DatabaseProvider {
        override val currentDb: StateFlow<MeshtasticDatabase> = MutableStateFlow(db)

        override fun <T> observeCurrentDb(name: String?, query: (MeshtasticDatabase) -> Flow<T>): Flow<T> =
            currentDb.flatMapLatest(query)

        override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = block(db)

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = block(db)
    }

    private companion object {
        const val LOCAL_NODE = 123
        const val PEER = 456
    }
}
