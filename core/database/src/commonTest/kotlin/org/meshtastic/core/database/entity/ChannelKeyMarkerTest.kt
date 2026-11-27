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
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A received DM filed in the public-key conversation keeps the channel it arrived on (Meshtastic-Android#8069). The
 * conversation's lock claims public-key encryption, so a reply that arrived under a channel key is marked per message.
 */
class ChannelKeyMarkerTest {

    @Test
    fun `channel-encrypted reply in the PKI conversation is marked`() = runTest {
        assertTrue(received(contactKey = "8!abcd1234", channel = 0).channelKeyEncrypted)
    }

    @Test
    fun `PKI reply in the PKI conversation is not marked`() = runTest {
        assertFalse(received(contactKey = "8!abcd1234", channel = NodeAddress.PKC_CHANNEL_INDEX).channelKeyEncrypted)
    }

    @Test
    fun `reply in a channel conversation is not marked`() = runTest {
        assertFalse(received(contactKey = "0!abcd1234", channel = 0).channelKeyEncrypted)
    }

    @Test
    fun `our own message in the PKI conversation is not marked`() = runTest {
        assertFalse(received(contactKey = "8!abcd1234", channel = 0, from = NodeAddress.ID_LOCAL).channelKeyEncrypted)
    }

    private suspend fun received(contactKey: String, channel: Int, from: String = "!abcd1234") = PacketEntity(
        packet =
        Packet(
            uuid = 1L,
            myNodeNum = MY_NODE_NUM,
            port_num = 1,
            contact_key = contactKey,
            received_time = 1_000L,
            read = true,
            data =
            DataPacket(
                bytes = null,
                dataType = 1,
                from = from,
                to = if (from == NodeAddress.ID_LOCAL) "!abcd1234" else NodeAddress.ID_LOCAL,
                time = 1_000L,
                channel = channel,
            ),
        ),
        reactions = emptyList(),
    )
        .toMessage { userId ->
            Node(num = 1, user = User.Builder().also { wb -> wb.id = userId.orEmpty() }.build())
        }

    private companion object {
        const val MY_NODE_NUM = 42
    }
}
