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
package com.geeksville.mesh.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.User

class MeshCommandSenderTest {

    private lateinit var commandSender: MeshCommandSender
    private lateinit var nodeManager: MeshNodeManager

    @Before
    fun setUp() {
        nodeManager = MeshNodeManager()
        commandSender = MeshCommandSender(null, nodeManager, null, null)
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
        val entity = NodeEntity(num = nodeNum, user = User(id = userId))
        nodeManager.nodeDBbyNodeNum[nodeNum] = entity
        nodeManager.nodeDBbyID[userId] = entity

        assertEquals(nodeNum, commandSender.resolveNodeNum(userId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolveNodeNum throws for unknown ID`() {
        commandSender.resolveNodeNum("unknown")
    }
}
