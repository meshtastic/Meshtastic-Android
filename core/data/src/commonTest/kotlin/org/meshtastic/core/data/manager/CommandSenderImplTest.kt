/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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


import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.proto.User

class CommandSenderImplTest {
/*


    private lateinit var commandSender: CommandSenderImpl
    private lateinit var nodeManager: NodeManager

    @Before
    fun setUp() {
    }

    @Test
    fun `generatePacketId produces unique non-zero IDs`() {
        val ids = mutableSetOf<Int>()
        repeat(1000) {
            val id = commandSender.generatePacketId()
            assertNotEquals(0, id)
            ids.add(id)
        }
        assertEquals(1000, ids.size)
    }

    @Test
    fun `resolveNodeNum handles broadcast ID`() {
        assertEquals(DataPacket.NODENUM_BROADCAST, commandSender.resolveNodeNum(DataPacket.ID_BROADCAST))
    }

    @Test
    fun `resolveNodeNum handles hex ID with exclamation mark`() {
        assertEquals(123, commandSender.resolveNodeNum("!0000007b"))
    }

    @Test
    fun `resolveNodeNum handles custom node ID from database`() {
        val nodeNum = 456
        val userId = "custom_id"
        val node = Node(num = nodeNum, user = User(id = userId))
        every { nodeManager.nodeDBbyID } returns mapOf(userId to node)

        assertEquals(nodeNum, commandSender.resolveNodeNum(userId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolveNodeNum throws for unknown ID`() {
        commandSender.resolveNodeNum("unknown")
    }

*/
}
