package com.geeksville.mesh

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geeksville.mesh.database.MeshtasticDatabase
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.model.NodeSortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeDBTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var nodeInfoDao: NodeInfoDao

    private val ourNodeInfo = NodeInfo(
        num = 8,
        user = MeshUser(
            "+16508765308".format(8),
            "Kevin Mester",
            "KLO",
            MeshProtos.HardwareModel.ANDROID_SIM,
            false
        ),
        position = Position(30.267153, -97.743057, 35, 123), // Austin
    )

    private val myNodeInfo: MyNodeInfo = MyNodeInfo(
        myNodeNum = ourNodeInfo.num,
        hasGPS = false,
        model = null,
        firmwareVersion = null,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 1L,
        messageTimeoutMsec = 5 * 60 * 1000,
        minAppVersion = 1,
        maxChannels = 8,
        hasWifi = false,
        channelUtilization = 0f,
        airUtilTx = 0f,
    )

    private val testPositions = arrayOf(
        Position(32.776665, -96.796989, 35, 123),  // Dallas
        Position(32.960758, -96.733521, 35, 456),  // Richardson
        Position(32.912901, -96.781776, 35, 789),  // North Dallas
        Position(29.760427, -95.369804, 35, 123),  // Houston
        Position(33.748997, -84.387985, 35, 456),  // Atlanta
        Position(34.052235, -118.243683, 35, 789), // Los Angeles
        Position(40.712776, -74.005974, 35, 123),  // New York City
        Position(41.878113, -87.629799, 35, 456),  // Chicago
        Position(39.952583, -75.165222, 35, 789),  // Philadelphia
    )

    private val testNodes = listOf(ourNodeInfo) + testPositions.mapIndexed { index, it ->
        NodeInfo(
            num = 9 + index,
            user = MeshUser(
                "+165087653%02d".format(9 + index),
                "Kevin Mester$index",
                "KM$index",
                if (index == 2) MeshProtos.HardwareModel.UNSET else MeshProtos.HardwareModel.ANDROID_SIM,
                false
            ),
            position = it,
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
     * The list excludes [ourNodeInfo] (our NodeInfo) to ensure consistency in the results.
     */
    private suspend fun getNodes(
        sort: NodeSortOption = NodeSortOption.LAST_HEARD,
        filter: String = "",
        includeUnknown: Boolean = true,
    ) = nodeInfoDao.getNodes(
        sort = sort.sqlValue,
        filter = filter,
        includeUnknown = includeUnknown,
        unknownHwModel = MeshProtos.HardwareModel.UNSET
    ).first().filter { it != ourNodeInfo }

    @Test // node list size
    fun testNodeListSize() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(10, nodes.size)
    }

    @Test // nodeDBbyNum() re-orders our node at the top of the list
    fun testOurNodeInfoIsFirst() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(ourNodeInfo, nodes.values.first())
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
        val sortedNodes = nodes.sortedBy { it.user?.longName?.uppercase() }
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testSortByDistance() = runBlocking {
        val nodes = getNodes(sort = NodeSortOption.DISTANCE)
        val sortedNodes = nodes.sortedBy { it.distance(ourNodeInfo) }
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
        val sortedNodes = nodes.sortedBy { it.user?.longName?.contains("(MQTT)") == true }
        assertEquals(sortedNodes, nodes)
    }

    @Test
    fun testIncludeUnknownIsFalse() = runBlocking {
        val nodes = getNodes(includeUnknown = false)
        val containsUnsetNode = nodes.any { node ->
            node.user?.hwModel == MeshProtos.HardwareModel.UNSET
        }
        assertFalse(containsUnsetNode)
    }

    @Test
    fun testIncludeUnknownIsTrue() = runBlocking {
        val nodes = getNodes(includeUnknown = true)
        val containsUnsetNode = nodes.any { node ->
            node.user?.hwModel == MeshProtos.HardwareModel.UNSET
        }
        assertTrue(containsUnsetNode)
    }
}
