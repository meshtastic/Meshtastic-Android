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
package org.meshtastic.core.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.NodeSortOption
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

@RunWith(AndroidJUnit4::class)
class NodeInfoDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var nodeInfoDao: NodeInfoDao

    private val onlineThreshold = onlineTimeThreshold()
    private val offlineNodeLastHeard = onlineThreshold - 30
    private val onlineNodeLastHeard = onlineThreshold + 20

    private val unknownNode =
        NodeEntity(
            num = 7,
            user =
            User(
                id = "!a1b2c3d4",
                long_name = "Meshtastic c3d4",
                short_name = "c3d4",
                hw_model = HardwareModel.UNSET,
            ),
            longName = "Meshtastic c3d4",
            shortName = null, // Dao filter for includeUnknown
        )

    private val ourNode =
        NodeEntity(
            num = 8,
            user =
            User(
                id = "+16508765308".format(8),
                long_name = "Kevin Mester",
                short_name = "KLO",
                hw_model = HardwareModel.ANDROID_SIM,
                is_licensed = false,
            ),
            longName = "Kevin Mester",
            shortName = "KLO",
            latitude = 30.267153,
            longitude = -97.743057, // Austin
            hopsAway = 0,
        )

    private val onlineNode =
        NodeEntity(
            num = 9,
            user =
            User(
                id = "!25060801",
                long_name = "Meshtastic 0801",
                short_name = "0801",
                hw_model = HardwareModel.ANDROID_SIM,
            ),
            longName = "Meshtastic 0801",
            shortName = "0801",
            hopsAway = 0,
            lastHeard = onlineNodeLastHeard,
        )

    private val offlineNode =
        NodeEntity(
            num = 10,
            user =
            User(
                id = "!25060802",
                long_name = "Meshtastic 0802",
                short_name = "0802",
                hw_model = HardwareModel.ANDROID_SIM,
            ),
            longName = "Meshtastic 0802",
            shortName = "0802",
            hopsAway = 0,
            lastHeard = offlineNodeLastHeard,
        )

    private val directNode =
        NodeEntity(
            num = 11,
            user =
            User(
                id = "!25060803",
                long_name = "Meshtastic 0803",
                short_name = "0803",
                hw_model = HardwareModel.ANDROID_SIM,
            ),
            longName = "Meshtastic 0803",
            shortName = "0803",
            hopsAway = 0,
            lastHeard = onlineNodeLastHeard,
        )

    private val relayedNode =
        NodeEntity(
            num = 12,
            user =
            User(
                id = "!25060804",
                long_name = "Meshtastic 0804",
                short_name = "0804",
                hw_model = HardwareModel.ANDROID_SIM,
            ),
            longName = "Meshtastic 0804",
            shortName = "0804",
            hopsAway = 3,
            lastHeard = onlineNodeLastHeard,
        )

    private val myNodeInfo: MyNodeEntity =
        MyNodeEntity(
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

    private val testPositions =
        arrayOf(
            0.0 to 0.0,
            32.776665 to -96.796989, // Dallas
            32.960758 to -96.733521, // Richardson
            32.912901 to -96.781776, // North Dallas
            29.760427 to -95.369804, // Houston
            33.748997 to -84.387985, // Atlanta
            34.052235 to -118.243683, // Los Angeles
            40.712776 to -74.005974, // New York City
            41.878113 to -87.629799, // Chicago
            39.952583 to -75.165222, // Philadelphia
        )
    private val testNodes =
        listOf(ourNode, unknownNode, onlineNode, offlineNode, directNode, relayedNode) +
            testPositions.mapIndexed { index, pos ->
                NodeEntity(
                    num = 1000 + index,
                    user =
                    User(
                        id = "+165087653%02d".format(9 + index),
                        long_name = "Kevin Mester$index",
                        short_name = "KM$index",
                        hw_model = HardwareModel.ANDROID_SIM,
                        is_licensed = false,
                        public_key = ByteArray(32) { index.toByte() }.toByteString(),
                    ),
                    longName = "Kevin Mester$index",
                    shortName = "KM$index",
                    latitude = pos.first,
                    longitude = pos.second,
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
     * Retrieves a list of nodes based on [sort], [filter] and [includeUnknown] parameters. The list excludes [ourNode]
     * to ensure consistency in the results.
     */
    private suspend fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
        onlyOnline: Boolean = false,
        onlyDirect: Boolean = false,
    ) = nodeInfoDao
        .getNodes(
            sort = sort.sqlValue,
            filter = filter,
            includeUnknown = includeUnknown,
            hopsAwayMax = if (onlyDirect) 0 else -1,
            lastHeardMin = if (onlyOnline) onlineTimeThreshold() else -1,
        )
        .map { list -> list.map { it.toModel() } }
        .first()
        .filter { it.num != ourNode.num }

    @Test // node list size
    fun testNodeListSize() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(6 + testPositions.size, nodes.size)
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
        val sortedNodes = nodes.sortedBy { it.user.long_name?.uppercase() ?: "" }
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testSortByDistance() = runBlocking {
        val nodes = getNodes(sort = NodeSortOption.DISTANCE)
        fun NodeEntity.toNode() = Node(num = num, user = user, position = position ?: Position())
        val sortedNodes =
            nodes.sortedWith( // nodes with invalid (null) positions at the end
                compareBy<Node> { it.validPosition == null }.thenBy { it.distance(ourNode.toNode()) },
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
        val sortedNodes = nodes.sortedBy { it.user.long_name?.contains("(MQTT)") == true }
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

    @Test
    fun testUnknownNodesKeepNamesNullAndRemainFiltered() = runBlocking {
        val updatedUnknownNode = unknownNode.copy(longName = "Should be cleared", shortName = "SHOULD")

        nodeInfoDao.upsert(updatedUnknownNode)

        val storedUnknown = nodeInfoDao.getNodeByNum(updatedUnknownNode.num)!!.node
        assertEquals(null, storedUnknown.longName)
        assertEquals(null, storedUnknown.shortName)

        val nodes = getNodes(includeUnknown = false)
        assertFalse(nodes.any { it.num == updatedUnknownNode.num })
    }

    @Test
    fun testOfflineNodesIncludedByDefault() = runBlocking {
        val nodes = getNodes()
        assertTrue(nodes.any { it.lastHeard < onlineTimeThreshold() })
    }

    @Test
    fun testOnlyOnlineExcludesOffline() = runBlocking {
        val nodes = getNodes(onlyOnline = true)
        assertFalse(nodes.any { it.lastHeard < onlineTimeThreshold() })
    }

    @Test
    fun testRelayedNodesIncludedByDefault() = runBlocking {
        val nodes = getNodes()
        assertTrue(nodes.any { it.hopsAway > 0 })
    }

    @Test
    fun testOnlyDirectExcludesRelayed() = runBlocking {
        val nodes = getNodes(onlyDirect = true)
        assertFalse(nodes.any { it.hopsAway > 0 })
    }

    @Test
    fun testPkcMismatch() = runBlocking {
        // First, ensure the node is in the DB with Key A
        val nodeA = testNodes[10].copy(publicKey = ByteArray(32) { 1 }.toByteString())
        nodeInfoDao.upsert(nodeA)

        // Now upsert with Key B (mismatch)
        val nodeB =
            nodeA.copy(
                publicKey = ByteArray(32) { 2 }.toByteString(),
                user = nodeA.user.copy(public_key = ByteArray(32) { 2 }.toByteString()),
            )
        nodeInfoDao.upsert(nodeB)

        val stored = nodeInfoDao.getNodeByNum(nodeA.num)!!.node
        assertEquals(NodeEntity.ERROR_BYTE_STRING, stored.publicKey)
        assertTrue(stored.toModel().mismatchKey)
    }

    @Test
    fun testRoutineUpdatePreservesKey() = runBlocking {
        // First, ensure the node is in the DB with Key A
        val keyA = ByteArray(32) { 1 }.toByteString()
        val nodeA = testNodes[10].copy(publicKey = keyA, user = testNodes[10].user.copy(public_key = keyA))
        nodeInfoDao.upsert(nodeA)

        // Now upsert with an empty key (common in position/telemetry updates)
        val nodeEmpty = nodeA.copy(publicKey = null, user = nodeA.user.copy(public_key = ByteString.EMPTY))
        nodeInfoDao.upsert(nodeEmpty)

        val stored = nodeInfoDao.getNodeByNum(nodeA.num)!!.node
        assertEquals(keyA, stored.publicKey)
        assertFalse(stored.toModel().mismatchKey)
    }

    @Test
    fun testRecoveryFromErrorState() = runBlocking {
        // Start in Error state
        val nodeError =
            testNodes[10].copy(
                publicKey = NodeEntity.ERROR_BYTE_STRING,
                user = testNodes[10].user.copy(public_key = NodeEntity.ERROR_BYTE_STRING),
            )
        nodeInfoDao.doUpsert(nodeError)
        assertTrue(nodeInfoDao.getNodeByNum(nodeError.num)!!.toModel().mismatchKey)

        // Now upsert with a valid Key C
        val keyC = ByteArray(32) { 3 }.toByteString()
        val nodeC = nodeError.copy(publicKey = keyC, user = nodeError.user.copy(public_key = keyC))
        nodeInfoDao.upsert(nodeC)

        val stored = nodeInfoDao.getNodeByNum(nodeError.num)!!.node
        assertEquals(keyC, stored.publicKey)
        assertFalse(stored.toModel().mismatchKey)
    }

    @Test
    fun testLicensedUserClearsKey() = runBlocking {
        // Start with a key
        val keyA = ByteArray(32) { 1 }.toByteString()
        val nodeA = testNodes[10].copy(publicKey = keyA, user = testNodes[10].user.copy(public_key = keyA))
        nodeInfoDao.upsert(nodeA)

        // Upsert as licensed user
        val nodeLicensed =
            nodeA.copy(
                user = nodeA.user.copy(is_licensed = true, public_key = ByteString.EMPTY),
                publicKey = ByteString.EMPTY,
            )
        nodeInfoDao.upsert(nodeLicensed)

        val stored = nodeInfoDao.getNodeByNum(nodeA.num)!!.node
        assertTrue(stored.publicKey == null || (stored.publicKey?.size ?: 0) == 0)
        assertFalse(stored.toModel().mismatchKey)
    }
}
