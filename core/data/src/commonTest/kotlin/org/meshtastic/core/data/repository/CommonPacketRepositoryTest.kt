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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class CommonPacketRepositoryTest {

    protected lateinit var dbProvider: FakeDatabaseProvider
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    protected lateinit var repository: PacketRepositoryImpl

    @BeforeTest
    fun setupRepo() {
        dbProvider = FakeDatabaseProvider()
        repository = PacketRepositoryImpl(dbProvider, dispatchers)
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun `savePacket persists and retrieves waypoints`() = runTest(testDispatcher) {
        val myNodeNum = 1
        val contact = "contact"

        // Ensure my_node is present so getMessageCount finds the packet
        dbProvider.currentDb.value
            .nodeInfoDao()
            .setMyNodeInfo(
                MyNodeEntity(
                    myNodeNum = myNodeNum,
                    model = "model",
                    firmwareVersion = "1.0",
                    couldUpdate = false,
                    shouldUpdate = false,
                    currentPacketId = 0L,
                    messageTimeoutMsec = 0,
                    minAppVersion = 0,
                    maxChannels = 0,
                    hasWifi = false,
                ),
            )

        val packet = DataPacket(to = "0!ffffffff", bytes = okio.ByteString.EMPTY, dataType = 1, id = 123)

        repository.savePacket(myNodeNum, contact, packet, 1000L)

        // Verify it was saved.
        val count = repository.getMessageCount(contact)
        assertEquals(1, count)
    }

    @Test
    fun `clearAllUnreadCounts works with real DB`() = runTest(testDispatcher) {
        repository.clearAllUnreadCounts()
        // No exception thrown
    }

    @Test
    fun `same mesh packet id persists as distinct durable rows`() = runTest(testDispatcher) {
        val first =
            DataPacket(
                to = "!aaaa0001",
                bytes = "first".encodeToByteArray().toByteString(),
                dataType = 1,
                id = 77,
                status = MessageStatus.QUEUED,
            )
        val second =
            DataPacket(
                to = "!aaaa0001",
                bytes = "second".encodeToByteArray().toByteString(),
                dataType = 1,
                id = 77,
                status = MessageStatus.QUEUED,
            )

        val firstId = repository.savePacket(0, "0!aaaa0001", first, 1L)
        val secondId = repository.savePacket(0, "0!aaaa0001", second, 2L)
        val meshPacket =
            MeshPacket(
                from = 1,
                to = 0xAAAA0001.toInt(),
                id = 77,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP),
            )

        assertNotEquals(firstId, secondId)
        assertEquals("first", repository.getPacketByPersistedId(firstId)?.text)
        assertEquals("second", repository.getPacketByPersistedId(secondId)?.text)
        assertEquals(setOf(firstId, secondId), repository.getQueuedPackets().map { it.id }.toSet())
        assertNull(repository.getPacketByPacketIdIfUnique(77))
        assertNull(repository.updateOutgoingMessageStatus(meshPacket, MessageStatus.ENROUTE))
        assertEquals(MessageStatus.QUEUED, repository.getPacketByPersistedId(firstId)?.status)
        assertEquals(MessageStatus.QUEUED, repository.getPacketByPersistedId(secondId)?.status)

        repository.updateMessageStatus(firstId, MessageStatus.ENROUTE)

        assertEquals(firstId, repository.updateOutgoingMessageStatus(meshPacket, MessageStatus.ENROUTE))
        assertEquals(MessageStatus.ENROUTE, repository.getPacketByPersistedId(firstId)?.status)
        assertEquals(MessageStatus.QUEUED, repository.getPacketByPersistedId(secondId)?.status)
    }

    @Test
    fun `queued row can be claimed for send only once`() = runTest(testDispatcher) {
        val queued =
            DataPacket(
                to = "!aaaa0001",
                bytes = "claim".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 81,
                status = MessageStatus.QUEUED,
            )
        val id = repository.savePacket(0, "0!aaaa0001", queued, 1L)

        val firstClaim = assertNotNull(repository.claimQueuedPacket(id))
        val secondClaim = assertNotNull(repository.claimQueuedPacket(id))

        assertEquals(MessageStatus.QUEUED, firstClaim.packet.status, "first caller owns the send")
        assertEquals(MessageStatus.ENROUTE, secondClaim.packet.status, "later caller must not send again")
        assertEquals(MessageStatus.ENROUTE, repository.getPacketByPersistedId(id)?.status)
    }

    @Test
    fun `concurrent UUID and legacy claims produce one send owner`() = runTest(testDispatcher) {
        val racingRepository =
            PacketRepositoryImpl(
                dbProvider,
                CoroutineDispatchers(
                    main = testDispatcher,
                    io = Dispatchers.Default,
                    default = Dispatchers.Default,
                ),
            )
        val queued =
            DataPacket(
                to = "!aaaa0001",
                bytes = "upgrade race".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 86,
                status = MessageStatus.QUEUED,
            )
        val id = repository.savePacket(0, "0!aaaa0001", queued, 1L)
        val start = CompletableDeferred<Unit>()
        val enteredProvider = Channel<Unit>(capacity = 2)
        val releaseProvider = CompletableDeferred<Unit>()
        dbProvider.beforeWithDb = {
            enteredProvider.send(Unit)
            releaseProvider.await()
        }
        val stableClaim =
            async(Dispatchers.Default) {
                start.await()
                racingRepository.claimQueuedPacket(id)
            }
        val legacyClaim =
            async(Dispatchers.Default) {
                start.await()
                racingRepository.claimQueuedPacketByPacketIdIfUnique(queued.id)
            }

        start.complete(Unit)
        repeat(2) { enteredProvider.receive() }
        releaseProvider.complete(Unit)
        val claims = listOf(stableClaim, legacyClaim).awaitAll().map { assertNotNull(it) }
        dbProvider.beforeWithDb = {}

        assertEquals(setOf(id), claims.map { it.id }.toSet())
        assertEquals(1, claims.count { it.packet.status == MessageStatus.QUEUED }, "one caller owns the send")
        assertEquals(1, claims.count { it.packet.status == MessageStatus.ENROUTE }, "one caller observes the claim")
        assertEquals(MessageStatus.ENROUTE, racingRepository.getPacketByPersistedId(id)?.status)
    }

    @Test
    fun `legacy claim returns exact row identity and fails closed when ambiguous`() = runTest(testDispatcher) {
        val unique =
            DataPacket(
                to = "!aaaa0001",
                bytes = "unique".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 82,
                status = MessageStatus.QUEUED,
            )
        val uniqueId = repository.savePacket(0, "0!aaaa0001", unique, 1L)

        val claim = assertNotNull(repository.claimQueuedPacketByPacketIdIfUnique(unique.id))

        assertEquals(uniqueId, claim.id)
        assertEquals(MessageStatus.QUEUED, claim.packet.status)
        assertEquals(MessageStatus.ENROUTE, repository.getPacketByPersistedId(uniqueId)?.status)

        val colliding = unique.copy(id = 83, bytes = "first".encodeToByteArray().toByteString())
        val firstId = repository.savePacket(0, "0!aaaa0001", colliding, 2L)
        val secondId =
            repository.savePacket(
                0,
                "0!aaaa0001",
                colliding.copy(bytes = "second".encodeToByteArray().toByteString()),
                3L,
            )

        assertNull(repository.claimQueuedPacketByPacketIdIfUnique(colliding.id))
        assertEquals(MessageStatus.QUEUED, repository.getPacketByPersistedId(firstId)?.status)
        assertEquals(MessageStatus.QUEUED, repository.getPacketByPersistedId(secondId)?.status)
    }

    @Test
    fun `failed-send rollback is exact and preserves a racing ACK`() = runTest(testDispatcher) {
        val packet =
            DataPacket(
                to = "!aaaa0001",
                bytes = "rollback".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 84,
                status = MessageStatus.QUEUED,
            )
        val ackedId = repository.savePacket(0, "0!aaaa0001", packet, 1L)
        val failedId = repository.savePacket(0, "0!aaaa0001", packet.copy(id = 85), 2L)
        repository.claimQueuedPacket(ackedId)
        repository.claimQueuedPacket(failedId)

        repository.updateMessageStatus(ackedId, MessageStatus.DELIVERED)

        assertFalse(repository.rollbackEnroutePacket(ackedId), "ACK advanced the row beyond ENROUTE")
        assertTrue(repository.rollbackEnroutePacket(failedId), "the exact failed row is still ENROUTE")
        assertEquals(MessageStatus.DELIVERED, repository.getPacketByPersistedId(ackedId)?.status)
        assertEquals(MessageStatus.QUEUED, repository.getPacketByPersistedId(failedId)?.status)
    }

    @Test
    fun `outgoing status update ignores inbound and different-port ID collisions`() = runTest(testDispatcher) {
        val packetId = 91
        val outgoing =
            DataPacket(
                from = "^local",
                to = "!22222222",
                bytes = "outgoing".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = packetId,
                status = MessageStatus.QUEUED,
            )
        val inbound =
            DataPacket(
                from = "!33333333",
                to = "!11111111",
                bytes = "inbound".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = packetId,
                status = MessageStatus.RECEIVED,
            )
        val otherPort =
            DataPacket(
                from = "^local",
                to = "!22222222",
                bytes = byteArrayOf(1).toByteString(),
                dataType = PortNum.ADMIN_APP.value,
                id = packetId,
                status = MessageStatus.QUEUED,
            )
        val inboundId = repository.savePacket(0, "0!33333333", inbound, 1L)
        val otherPortId = repository.savePacket(0, "0!22222222", otherPort, 2L)
        val outgoingId = repository.savePacket(0, "0!22222222", outgoing, 3L)

        val updated =
            repository.updateOutgoingMessageStatus(
                MeshPacket(
                    from = 0x11111111,
                    to = 0x22222222,
                    id = packetId,
                    decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP),
                ),
                MessageStatus.ENROUTE,
            )

        assertEquals(outgoingId, updated)
        assertEquals(MessageStatus.ENROUTE, repository.getPacketByPersistedId(outgoingId)?.status)
        assertEquals(MessageStatus.RECEIVED, repository.getPacketByPersistedId(inboundId)?.status)
        assertEquals(MessageStatus.QUEUED, repository.getPacketByPersistedId(otherPortId)?.status)
    }

    @Test
    fun `reply lookups stay in the current conversation when packet IDs collide`() = runTest(testDispatcher) {
        val contactA = "0!aaaa0001"
        val contactB = "0!bbbb0002"
        val parentId = 77
        val parentB =
            DataPacket(
                from = "!bbbb0002",
                to = "^local",
                bytes = "parent B".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = parentId,
                status = MessageStatus.RECEIVED,
            )
        val parentA = parentB.copy(from = "!aaaa0001", bytes = "parent A".encodeToByteArray().toByteString())
        val replyB =
            DataPacket(
                from = "!bbbb0002",
                to = "^local",
                bytes = "reply B".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 78,
                status = MessageStatus.RECEIVED,
                replyId = parentId,
            )
        // Insert the wrong-conversation parent first so an old global LIMIT 1 lookup selects the wrong row.
        repository.savePacket(0, contactA, parentA, 1L)
        repository.savePacket(0, contactB, parentB, 2L)
        repository.savePacket(0, contactB, replyB, 3L)

        val messages = repository.getMessagesFrom(contactB, getNode = ::testNode).first()
        val reply = messages.single { it.packetId == replyB.id }
        val pagedParent =
            dbProvider.currentDb.value.packetDao().getPacketsByPacketIdAndContact(parentId, contactB).singleOrNull()

        assertEquals("parent B", reply.originalMessage?.text)
        assertEquals("parent B", pagedParent?.packet?.data?.text)
    }

    @Test
    fun `reply parent fails closed for same-conversation ID collisions`() = runTest(testDispatcher) {
        val contact = "0^all"
        val parentId = 44
        val firstParent =
            DataPacket(
                from = "!aaaa0001",
                to = "^all",
                bytes = "first parent".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = parentId,
                status = MessageStatus.RECEIVED,
            )
        val secondParent =
            firstParent.copy(from = "!bbbb0002", bytes = "second parent".encodeToByteArray().toByteString())
        val reply =
            DataPacket(
                from = "!cccc0003",
                to = "^all",
                bytes = "ambiguous reply".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 45,
                status = MessageStatus.RECEIVED,
                replyId = parentId,
            )
        repository.savePacket(0, contact, firstParent, 1L)
        repository.savePacket(0, contact, secondParent, 2L)
        repository.savePacket(0, contact, reply, 3L)

        val replyMessage =
            repository.getMessagesFrom(contact, getNode = ::testNode).first().single { it.packetId == reply.id }
        val pagedCandidates =
            dbProvider.currentDb.value.packetDao().getPacketsByPacketIdAndContact(parentId, contact)

        assertNull(replyMessage.originalMessage)
        assertEquals(2, pagedCandidates.size)
        assertNull(pagedCandidates.singleOrNull())
    }

    private fun testNode(id: String?): Node = Node(num = 0, user = User(id = id.orEmpty()))
}
