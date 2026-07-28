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

    private fun packetEntity(reactions: List<ReactionEntity>) = PacketEntity(
        packet =
        Packet(
            uuid = 1L,
            myNodeNum = MY_NODE_NUM,
            port_num = 1,
            contact_key = "0^all",
            received_time = 1_000L,
            read = true,
            data = DataPacket(bytes = null, dataType = 1, from = "!abcd1234", time = 1_000L),
            packetId = PACKET_ID,
        ),
        reactions = reactions,
    )

    private fun reaction(myNodeNum: Int, userId: String, emoji: String, status: MessageStatus = MessageStatus.UNKNOWN) =
        ReactionEntity(
            myNodeNum = myNodeNum,
            replyId = PACKET_ID,
            userId = userId,
            emoji = emoji,
            timestamp = 1_000L,
            packetId = PACKET_ID,
            status = status,
        )

    private val getNode: suspend (String?) -> Node = { userId -> Node(num = 1, user = User(id = userId.orEmpty())) }

    private companion object {
        const val MY_NODE_NUM = 42
        const val OTHER_NODE_NUM = 99
        const val PACKET_ID = 7
    }
}
