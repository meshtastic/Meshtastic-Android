package com.geeksville.mesh

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geeksville.mesh.database.MeshtasticDatabase
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
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
class NodeInfoDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var nodeInfoDao: NodeInfoDao

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

    private val testNodes = listOf(ourNode) + testPositions.mapIndexed { index, pos ->
        NodeEntity(
            num = 9 + index,
            user = user {
                id = "+165087653%02d".format(9 + index)
                longName = "Kevin Mester$index"
                shortName = "KM$index"
                hwModel = MeshProtos.HardwareModel.ANDROID_SIM
                isLicensed = false
            },
            longName = "Kevin Mester$index", shortName = if (index == 2) null else "KM$index",
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
    ).first().filter { it != ourNode }

    @Test // node list size
    fun testNodeListSize() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(11, nodes.size)
    }

    @Test // nodeDBbyNum() re-orders our node at the top of the list
    fun testOurNodeInfoIsFirst() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(ourNode, nodes.values.first())
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
        val sortedNodes = nodes.sortedWith( // nodes with invalid (null) positions at the end
            compareBy<NodeEntity> { it.validPosition == null }.thenBy { it.distance(ourNode) }
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
        val containsUnsetNode = nodes.any { it.shortName == null }
        assertTrue(containsUnsetNode)
    }
}
