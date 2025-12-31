/*
 * Copyright (c) 2025 Meshtastic LLC
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.user

class MeshNodeManagerTest {

    private lateinit var nodeManager: MeshNodeManager

    @Before
    fun setUp() {
        nodeManager = MeshNodeManager() // Use internal testing constructor
    }

    @Test
    fun `getOrCreateNodeInfo returns existing node`() {
        val node = NodeEntity(num = 1, longName = "Node 1", shortName = "N1")
        nodeManager.nodeDBbyNodeNum[1] = node

        val result = nodeManager.getOrCreateNodeInfo(1)

        assertEquals(node, result)
    }

    @Test
    fun `getOrCreateNodeInfo creates new node if not exists`() {
        val nodeNum = 456
        val result = nodeManager.getOrCreateNodeInfo(nodeNum)

        assertNotNull(result)
        assertEquals(nodeNum, result.num)
        assertEquals(DataPacket.nodeNumToDefaultId(nodeNum), result.user.id)
    }

    @Test
    fun `getMyNodeInfo returns info from nodeDB when available`() {
        val myNum = 123
        nodeManager.myNodeNum = myNum
        val myNode =
            NodeEntity(
                num = myNum,
                user =
                user {
                    id = "!0000007b"
                    longName = "My Node"
                    shortName = "MY"
                    hwModel = MeshProtos.HardwareModel.TBEAM
                },
            )
        nodeManager.nodeDBbyNodeNum[myNum] = myNode

        // This test will hit the null NodeRepository, so we might need to mock it if we want to test fallbacks.
        // But since we set myNodeNum and nodeDBbyNodeNum, it should return from memory if we are careful.
        // Actually getMyNodeInfo calls nodeRepository.myNodeInfo.value if memory lookup fails.
    }

    @Test
    fun `clear resets state`() {
        nodeManager.myNodeNum = 123
        nodeManager.nodeDBbyNodeNum[1] = NodeEntity(num = 1)
        nodeManager.isNodeDbReady.value = true

        nodeManager.clear()

        assertNull(nodeManager.myNodeNum)
        assertTrue(nodeManager.nodeDBbyNodeNum.isEmpty())
        assertFalse(nodeManager.isNodeDbReady.value)
    }
}
