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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.core.testing.setupTestContext
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import org.meshtastic.proto.Waypoint
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class CommonPacketRepositoryTest {

    protected lateinit var dbProvider: FakeDatabaseProvider
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)
    protected val nodeRepository = FakeNodeRepository()

    protected lateinit var repository: PacketRepositoryImpl

    private val myNodeNum = 1
    private val broadcastContact = "0${DataPacket.nodeNumToId(DataPacket.BROADCAST)}"

    fun setupRepo() {
        setupTestContext()
        dbProvider = FakeDatabaseProvider()
        repository = PacketRepositoryImpl(dbProvider, dispatchers, nodeRepository)
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = myNodeNum))
    }

    @AfterTest
    fun tearDown() {
        if (::dbProvider.isInitialized) {
            dbProvider.close()
        }
    }

    @Test
    fun `savePacket persists and retrieves waypoints`() = runTest(testDispatcher) {
        val packet = DataPacket(to = DataPacket.BROADCAST, bytes = ByteString.EMPTY, dataType = PortNum.TEXT_MESSAGE_APP.value, id = 123)

        repository.savePacket(myNodeNum, broadcastContact, packet, 1000L)

        assertEquals(1, repository.getMessageCount(broadcastContact))
    }

    @Test
    fun `clearAllUnreadCounts works with real DB`() = runTest(testDispatcher) {
        repository.clearAllUnreadCounts()
    }

    @Test
    fun `getMessagesFrom limit keeps newest messages within boundary`() = runTest(testDispatcher) {
        val contact = "1!abcd1234"
        repeat(55) { index ->
            saveTextPacket(contact = contact, id = index + 1, receivedTime = 1_000L + index, text = "Message $index", read = false)
        }

        val messages = repository.getMessagesFrom(contact = contact, limit = 50, getNode = ::lookupNode).first()

        assertEquals(50, messages.size)
        assertEquals("Message 54", messages.first().text)
        assertEquals("Message 5", messages.last().text)
    }

    @Test
    fun `unread counts track read and unread transitions`() = runTest(testDispatcher) {
        val contact = "2!feedbeef"
        saveTextPacket(contact = contact, id = 1, receivedTime = 100L, read = false)
        saveTextPacket(contact = contact, id = 2, receivedTime = 200L, read = false)
        saveTextPacket(contact = contact, id = 3, receivedTime = 300L, read = false)

        assertEquals(3, repository.getUnreadCount(contact))
        assertEquals(3, repository.getUnreadCountTotal().first())
        assertTrue(repository.hasUnreadMessages(contact).first())
        assertNotNull(repository.getFirstUnreadMessageUuid(contact).first())

        repository.clearUnreadCount(contact, 200L)

        assertEquals(1, repository.getUnreadCount(contact))
        assertEquals(1, repository.getUnreadCountTotal().first())
        assertTrue(repository.hasUnreadMessages(contact).first())

        repository.clearAllUnreadCounts()

        assertEquals(0, repository.getUnreadCount(contact))
        assertEquals(0, repository.getUnreadCountTotal().first())
        assertFalse(repository.hasUnreadMessages(contact).first())
        assertEquals(null, repository.getFirstUnreadMessageUuid(contact).first())
    }

    @Test
    fun `reactions can be added listed and removed with parent message`() = runTest(testDispatcher) {
        val contact = "3!react000"
        val replyId = 501
        saveTextPacket(contact = contact, id = replyId, receivedTime = 1_000L, text = "Original", read = true)

        val reaction =
            Reaction(
                replyId = replyId,
                user = User(id = "!reactor"),
                emoji = "👍",
                timestamp = 2_000L,
                snr = 1.5f,
                rssi = -70,
                hopsAway = 1,
                packetId = replyId,
                to = "!abcd1234",
                channel = 3,
            )

        repository.insertReaction(reaction, myNodeNum)

        val storedReaction = repository.getReactionByPacketId(replyId)
        assertNotNull(storedReaction)
        assertEquals("👍", storedReaction.emoji)
        assertEquals("!reactor", storedReaction.user.id)

        val reactions = repository.findReactionsWithId(replyId)
        assertEquals(1, reactions.size)
        assertEquals(replyId, reactions.single().replyId)
        assertEquals("👍", reactions.single().emoji)

        val messageUuid = repository.getMessagesFrom(contact = contact, getNode = ::lookupNode).first().single().uuid
        repository.deleteMessages(listOf(messageUuid))

        assertTrue(repository.findReactionsWithId(replyId).isEmpty())
        assertEquals(null, repository.getReactionByPacketId(replyId))
    }

    @Test
    fun `getWaypoints preserves channel data for filtering`() = runTest(testDispatcher) {
        saveWaypointPacket(contact = broadcastContact, channel = 0, waypointId = 101, receivedTime = 1_000L)
        saveWaypointPacket(contact = "2${DataPacket.nodeNumToId(DataPacket.BROADCAST)}", channel = 2, waypointId = 202, receivedTime = 2_000L)
        saveTextPacket(contact = broadcastContact, id = 77, receivedTime = 3_000L)

        val waypoints = repository.getWaypoints().first()
        val channelTwoWaypoints = waypoints.filter { it.channel == 2 }

        assertEquals(2, waypoints.size)
        assertEquals(setOf(0, 2), waypoints.map { it.channel }.toSet())
        assertEquals(listOf(202), channelTwoWaypoints.mapNotNull { it.waypoint?.id })
    }

    @Test
    fun `contact keys keep channel and destination formats distinct`() = runTest(testDispatcher) {
        val channelBroadcast = broadcastContact
        val secondaryBroadcast = "1${DataPacket.nodeNumToId(DataPacket.BROADCAST)}"
        val directMessage = "${DataPacket.PKC_CHANNEL_INDEX}!70fdde9b"

        saveTextPacket(contact = channelBroadcast, id = 1, receivedTime = 100L, channel = 0)
        saveTextPacket(contact = secondaryBroadcast, id = 2, receivedTime = 200L, channel = 1)
        saveTextPacket(
            contact = directMessage,
            id = 3,
            receivedTime = 300L,
            channel = DataPacket.PKC_CHANNEL_INDEX,
            to = 0x70fdde9b,
        )

        val contacts = repository.getContacts().first()

        assertEquals(setOf(channelBroadcast, secondaryBroadcast, directMessage), contacts.keys)
        assertEquals(0, contacts.getValue(channelBroadcast).channel)
        assertEquals(1, contacts.getValue(secondaryBroadcast).channel)
        assertEquals(DataPacket.PKC_CHANNEL_INDEX, contacts.getValue(directMessage).channel)
        assertEquals(1, repository.getMessageCount(channelBroadcast))
        assertEquals(1, repository.getMessageCount(secondaryBroadcast))
        assertEquals(1, repository.getMessageCount(directMessage))
    }

    @Test
    fun `getMessagesFrom returns empty flow for unknown contact`() = runTest(testDispatcher) {
        val messages = repository.getMessagesFrom(contact = "7!missing", getNode = ::lookupNode).first()

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `concurrent writes keep all packets intact`() = runTest {
        val concurrentRepository = PacketRepositoryImpl(
            dbManager = dbProvider,
            dispatchers = CoroutineDispatchers(main = testDispatcher, io = Dispatchers.Default, default = Dispatchers.Default),
            nodeRepository = nodeRepository,
        )
        val contact = "4!concur00"

        coroutineScope {
            repeat(100) { index ->
                launch(Dispatchers.Default) {
                    concurrentRepository.savePacket(
                        myNodeNum = myNodeNum,
                        contactKey = contact,
                        packet = textPacket(id = index + 1, text = "Concurrent $index", channel = 4),
                        receivedTime = 10_000L + index,
                        read = false,
                    )
                }
            }
        }

        val messages = concurrentRepository.getMessagesFrom(contact = contact, getNode = ::lookupNode).first()

        assertEquals(100, concurrentRepository.getMessageCount(contact))
        assertEquals(100, messages.size)
        assertEquals(100, messages.map { it.packetId }.distinct().size)
    }

    private suspend fun saveTextPacket(
        contact: String,
        id: Int,
        receivedTime: Long,
        text: String = "Message $id",
        read: Boolean = true,
        filtered: Boolean = false,
        channel: Int = contact.first().digitToIntOrNull() ?: 0,
        to: Int = DataPacket.BROADCAST,
        from: Int = 0x12345678,
    ) {
        repository.savePacket(
            myNodeNum = myNodeNum,
            contactKey = contact,
            packet = textPacket(id = id, text = text, channel = channel, to = to, from = from),
            receivedTime = receivedTime,
            read = read,
            filtered = filtered,
        )
    }

    private suspend fun saveWaypointPacket(contact: String, channel: Int, waypointId: Int, receivedTime: Long) {
        repository.savePacket(
            myNodeNum = myNodeNum,
            contactKey = contact,
            packet = DataPacket(to = DataPacket.BROADCAST, channel = channel, waypoint = Waypoint(id = waypointId, name = "Waypoint $waypointId")),
            receivedTime = receivedTime,
        )
    }

    private fun textPacket(
        id: Int,
        text: String,
        channel: Int,
        to: Int = DataPacket.BROADCAST,
        from: Int = 0x12345678,
    ) = DataPacket(
        to = to,
        bytes = text.encodeToByteArray().toByteString(),
        dataType = PortNum.TEXT_MESSAGE_APP.value,
        from = from,
        id = id,
        channel = channel,
    )

    private fun lookupNode(userId: String?): Node = Node(num = 0, user = User(id = userId.orEmpty(), long_name = userId.orEmpty()))
}
