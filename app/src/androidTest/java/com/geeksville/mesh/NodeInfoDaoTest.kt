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

package com.geeksville.mesh

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geeksville.mesh.database.MeshtasticDatabase
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.NodeSortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeInfoDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var nodeInfoDao: NodeInfoDao

    private val unknownNode = NodeEntity(
        num = 7,
        user = user {
            id = "!a1b2c3d4"
            longName = "Meshtastic c3d4"
            shortName = "c3d4"
            hwModel = MeshProtos.HardwareModel.UNSET
        },
        longName = "Meshtastic c3d4",
        shortName = null // Dao filter for includeUnknown
    )

    private val ourNode = NodeEntity(
        num = 8,
        user = user {
            id = "+16508765308".format(8)
            longName = "Kevin Mester"
            shortName = "KLO"
            hwModel = MeshProtos.HardwareModel.ANDROID_SIM
            isLicensed = false
        },
        longName = "Kevin Mester", shortName = "KLO",
        latitude = 30.267153, longitude = -97.743057 // Austin
    )

    private val myNodeInfo: MyNodeEntity = MyNodeEntity(
        myNodeNum = ourNode.num,
        model = null,
        firmwareVersion = null,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 1L,
        messageTimeoutMsec = 5 * 60 * 1000,
        minAppVersion = 1,
        maxChannels = 8,
        hasWifi = false,
    )

    private val testPositions = arrayOf(
        0.0 to 0.0,
        32.776665 to -96.796989,  // Dallas
        32.960758 to -96.733521,  // Richardson
        32.912901 to -96.781776,  // North Dallas
        29.760427 to -95.369804,  // Houston
        33.748997 to -84.387985,  // Atlanta
        34.052235 to -118.243683, // Los Angeles
        40.712776 to -74.005974,  // New York City
        41.878113 to -87.629799,  // Chicago
        39.952583 to -75.165222,  // Philadelphia
    )

    private val testNodes = listOf(ourNode, unknownNode) + testPositions.mapIndexed { index, pos ->
        NodeEntity(
            num = 9 + index,
            user = user {
                id = "+165087653%02d".format(9 + index)
                longName = "Kevin Mester$index"
                shortName = "KM$index"
                hwModel = MeshProtos.HardwareModel.ANDROID_SIM
                isLicensed = false
            },
            longName = "Kevin Mester$index", shortName = "KM$index",
            latitude = pos.first, longitude = pos.second,
            lastHeard = 9 + index,
        )
    }

    @Before
    fun createDb(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MeshtasticDatabase::class.java).build()
        nodeInfoDao = database.nodeInfoDao()

        nodeInfoDao.apply {
            putAll(testNodes)
            setMyNodeInfo(myNodeInfo)
        }
    }

    @After
    fun closeDb() {
        database.close()
    }

    /**
     * Retrieves a list of nodes based on [sort], [filter] and [includeUnknown] parameters.
     * The list excludes [ourNode] to ensure consistency in the results.
     */
    private suspend fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
    ) = nodeInfoDao.getNodes(
        sort = sort.sqlValue,
        filter = filter,
        includeUnknown = includeUnknown,
    ).map { list -> list.map { it.toModel() } }.first().filter { it.num != ourNode.num }

    @Test // node list size
    fun testNodeListSize() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(12, nodes.size)
    }

    @Test // nodeDBbyNum() re-orders our node at the top of the list
    fun testOurNodeInfoIsFirst() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(ourNode.num, nodes.values.first().node.num)
    }

    @Test
    fun testSortByLastHeard() = runBlocking {
        val nodes = getNodes(sort = NodeSortOption.LAST_HEARD)
        val sortedNodes = nodes.sortedByDescending { it.lastHeard }
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testSortByAlpha() = runBlocking {
        val nodes = getNodes(sort = NodeSortOption.ALPHABETICAL)
        val sortedNodes = nodes.sortedBy { it.user.longName.uppercase() }
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testSortByDistance() = runBlocking {
        val nodes = getNodes(sort = NodeSortOption.DISTANCE)
        fun NodeEntity.toNode() = Node(num = num, user = user, position = position)
        val sortedNodes = nodes.sortedWith( // nodes with invalid (null) positions at the end
            compareBy<Node> { it.validPosition == null }.thenBy { it.distance(ourNode.toNode()) }
        )
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testSortByChannel() = runBlocking {
        val nodes = getNodes(sort = NodeSortOption.CHANNEL)
        val sortedNodes = nodes.sortedBy { it.channel }
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testSortByViaMqtt() = runBlocking {
        val nodes = getNodes(sort = NodeSortOption.VIA_MQTT)
        val sortedNodes = nodes.sortedBy { it.user.longName.contains("(MQTT)") }
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testIncludeUnknownIsFalse() = runBlocking {
        val nodes = getNodes(includeUnknown = false)
        val containsUnsetNode = nodes.any { it.isUnknownUser }
        assertFalse(containsUnsetNode)
    }

    @Test
    fun testIncludeUnknownIsTrue() = runBlocking {
        val nodes = getNodes(includeUnknown = true)
        val containsUnsetNode = nodes.any { it.isUnknownUser }
        assertTrue(containsUnsetNode)
    }
}
