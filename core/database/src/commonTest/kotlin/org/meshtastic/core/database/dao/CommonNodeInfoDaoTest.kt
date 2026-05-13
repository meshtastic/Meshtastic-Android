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
package org.meshtastic.core.database.dao

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class CommonNodeInfoDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var dao: NodeInfoDao

    private val myNodeInfo: MyNodeEntity =
        MyNodeEntity(
            myNodeNum = 42424242,
            model = "TBEAM",
            firmwareVersion = "2.5.0",
            couldUpdate = false,
            shouldUpdate = false,
            currentPacketId = 1L,
            messageTimeoutMsec = 300000,
            minAppVersion = 1,
            maxChannels = 8,
            hasWifi = false,
        )

    suspend fun createDb() {
        database = getInMemoryDatabaseBuilder().build()
        dao = database.nodeInfoDao()
        dao.setMyNodeInfo(myNodeInfo)
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    @Test
    fun testGetMyNodeInfo() = runTest {
        val info = dao.getMyNodeInfo().first()
        assertNotNull(info)
        assertEquals(myNodeInfo.myNodeNum, info.myNodeNum)
    }

    @Test
    fun testUpsertNode() = runTest {
        val node =
            NodeEntity(
                num = 1234,
                user = User(long_name = "Test Node", id = "!test", hw_model = org.meshtastic.proto.HardwareModel.TBEAM),
                lastHeard = (nowMillis / 1000).toInt(),
            )
        dao.upsert(node)
        val result = dao.getNodeByNum(1234)
        assertNotNull(result)
        assertEquals("Test Node", result.node.longName)
    }

    @Test
    fun testNodeDBbyNum() = runTest {
        val node1 = NodeEntity(num = 1, user = User(id = "!1"))
        val node2 = NodeEntity(num = 2, user = User(id = "!2"))
        dao.putAll(listOf(node1, node2))

        val nodes = dao.nodeDBbyNum().first()
        assertEquals(2, nodes.size)
        assertTrue(nodes.containsKey(1))
        assertTrue(nodes.containsKey(2))
    }

    @Test
    fun testDeleteNode() = runTest {
        val node = NodeEntity(num = 1, user = User(id = "!1"))
        dao.upsert(node)
        dao.deleteNode(1)
        val result = dao.getNodeByNum(1)
        assertEquals(null, result)
    }

    @Test
    fun testClearNodeInfo() = runTest {
        val node1 = NodeEntity(num = 1, user = User(id = "!1"), isFavorite = true)
        val node2 = NodeEntity(num = 2, user = User(id = "!2"), isFavorite = false)
        dao.putAll(listOf(node1, node2))

        dao.clearNodeInfo(preserveFavorites = true)
        val nodes = dao.nodeDBbyNum().first()
        assertEquals(1, nodes.size)
        assertTrue(nodes.containsKey(1))
    }
}
