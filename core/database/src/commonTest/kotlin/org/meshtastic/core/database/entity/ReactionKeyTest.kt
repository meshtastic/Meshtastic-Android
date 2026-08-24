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
package org.meshtastic.core.database.entity

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reaction dialog keys its rows on `user.id` + `emoji`. `myNodeNum` is part of the `reactions` primary key, so the
 * legacy `myNodeNum = 0` bucket and this node's rows can carry the same pair — a duplicate key there is fatal.
 */
class ReactionKeyTest {

    @Test
    fun `a legacy reaction and its current-node twin collapse to one entry`() = runTest {
        val entity =
            packetEntity(
                reactions =
                listOf(
                    reaction(myNodeNum = 0, userId = "!abcd1234", emoji = "👍"),
                    reaction(myNodeNum = MY_NODE_NUM, userId = "!abcd1234", emoji = "👍"),
                ),
            )

        val emojis = entity.toMessage(getNode).emojis

        assertEquals(1, emojis.size, "duplicate user+emoji keys crash the reaction list: $emojis")
    }

    @Test
    fun `the current-node row wins so its delivery status survives`() = runTest {
        val entity =
            packetEntity(
                reactions =
                listOf(
                    reaction(myNodeNum = 0, userId = "!abcd1234", emoji = "👍"),
                    reaction(
                        myNodeNum = MY_NODE_NUM,
                        userId = "!abcd1234",
                        emoji = "👍",
                        status = MessageStatus.DELIVERED,
                    ),
                ),
            )

        assertEquals(MessageStatus.DELIVERED, entity.toMessage(getNode).emojis.single().status)
    }

    @Test
    fun `a lone legacy reaction is still shown`() = runTest {
        val entity = packetEntity(reactions = listOf(reaction(myNodeNum = 0, userId = "!abcd1234", emoji = "🎉")))

        assertEquals(1, entity.toMessage(getNode).emojis.size)
    }

    @Test
    fun `distinct users and distinct emoji stay distinct`() = runTest {
        val entity =
            packetEntity(
                reactions =
                listOf(
                    reaction(myNodeNum = MY_NODE_NUM, userId = "!abcd1234", emoji = "👍"),
                    reaction(myNodeNum = MY_NODE_NUM, userId = "!abcd1234", emoji = "🎉"),
                    reaction(myNodeNum = MY_NODE_NUM, userId = "!beef5678", emoji = "👍"),
                ),
            )

        val keys = entity.toMessage(getNode).emojis.map { it.user.id to it.emoji }

        assertEquals(3, keys.size, "keys: $keys")
        assertEquals(3, keys.toSet().size, "keys: $keys")
    }

    @Test
    fun `another node's reactions are excluded entirely`() = runTest {
        val entity =
            packetEntity(reactions = listOf(reaction(myNodeNum = OTHER_NODE_NUM, userId = "!abcd1234", emoji = "👍")))

        assertEquals(emptyList(), entity.toMessage(getNode).emojis)
    }

    @Test
    fun `same reply id reaction attaches only to its conversation`() = runTest {
        val reaction =
            reaction(
                myNodeNum = MY_NODE_NUM,
                userId = "!bbbb0002",
                emoji = "ðŸ‘",
                status = MessageStatus.RECEIVED,
                to = "!0000002a",
            )
        val collidingParent = packetEntity(reactions = listOf(reaction), contactKey = "0!aaaa0001")
        val intendedParent = packetEntity(reactions = listOf(reaction), contactKey = "0!bbbb0002")

        assertEquals(emptyList(), collidingParent.toMessage(getNode).emojis)
        assertEquals(listOf("ðŸ‘"), intendedParent.toMessage(getNode).emojis.map { it.emoji })
    }

    @Test
    fun `normalized inbound PKI reaction attaches to its direct-message parent`() = runTest {
        val reaction =
            reaction(
                myNodeNum = MY_NODE_NUM,
                userId = "!bbbb0002",
                emoji = "PKI",
                status = MessageStatus.RECEIVED,
                to = "!0000002a",
                channel = NodeAddress.PKC_CHANNEL_INDEX,
            )

        val intendedMessage = packetEntity(listOf(reaction), contactKey = "8!bbbb0002").toMessage(getNode)
        val otherConversation = packetEntity(listOf(reaction), contactKey = "8!aaaa0001").toMessage(getNode)
        val otherChannel = packetEntity(listOf(reaction), contactKey = "7!bbbb0002").toMessage(getNode)

        assertEquals(listOf("PKI"), intendedMessage.emojis.map { it.emoji })
        assertEquals(emptyList(), otherConversation.emojis)
        assertEquals(emptyList(), otherChannel.emojis)
    }

    @Test
    fun `pre-fix raw-channel inbound PKI reaction remains visible`() = runTest {
        val legacyReaction =
            reaction(
                myNodeNum = MY_NODE_NUM,
                userId = "!bbbb0002",
                emoji = "legacy PKI",
                status = MessageStatus.RECEIVED,
                to = "!0000002a",
                channel = 0,
            )

        val intendedMessage = packetEntity(listOf(legacyReaction), contactKey = "8!bbbb0002").toMessage(getNode)
        val otherConversation = packetEntity(listOf(legacyReaction), contactKey = "8!aaaa0001").toMessage(getNode)

        assertEquals(listOf("legacy PKI"), intendedMessage.emojis.map { it.emoji })
        assertEquals(emptyList(), otherConversation.emojis)
    }

    @Test
    fun `wrong non-PKI reaction channel remains excluded`() = runTest {
        val reaction =
            reaction(
                myNodeNum = MY_NODE_NUM,
                userId = "!bbbb0002",
                emoji = "wrong channel",
                status = MessageStatus.RECEIVED,
                to = "!0000002a",
                channel = 1,
            )

        val message = packetEntity(listOf(reaction), contactKey = "8!bbbb0002").toMessage(getNode)

        assertEquals(emptyList(), message.emojis)
    }

    private fun packetEntity(reactions: List<ReactionEntity>, contactKey: String = "0^all") = PacketEntity(
        packet =
        Packet(
            uuid = 1L,
            myNodeNum = MY_NODE_NUM,
            port_num = 1,
            contact_key = contactKey,
            received_time = 1_000L,
            read = true,
            data = DataPacket(bytes = null, dataType = 1, from = "!abcd1234", time = 1_000L),
            packetId = PACKET_ID,
        ),
        reactions = reactions,
    )

    private fun reaction(
        myNodeNum: Int,
        userId: String,
        emoji: String,
        status: MessageStatus = MessageStatus.UNKNOWN,
        to: String? = null,
        channel: Int = 0,
    ) = ReactionEntity(
        myNodeNum = myNodeNum,
        replyId = PACKET_ID,
        userId = userId,
        emoji = emoji,
        timestamp = 1_000L,
        packetId = PACKET_ID,
        status = status,
        to = to,
        channel = channel,
    )

    private val getNode: suspend (String?) -> Node = { userId -> Node(num = 1, user = User(id = userId.orEmpty())) }

    private companion object {
        const val MY_NODE_NUM = 42
        const val OTHER_NODE_NUM = 99
        const val PACKET_ID = 7
    }
}
