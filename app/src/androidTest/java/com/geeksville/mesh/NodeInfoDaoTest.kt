package com.geeksville.mesh

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geeksville.mesh.database.MeshtasticDatabase
import com.geeksville.mesh.database.dao.NodeInfoDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeDBTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var nodeInfoDao: NodeInfoDao

    private val testNodeNoPosition = NodeInfo(
        8,
        MeshUser(
            "+16508765308".format(8),
            "Kevin MesterNoLoc",
            "KLO",
            MeshProtos.HardwareModel.ANDROID_SIM,
            false
        ),
        null
    )

    private val myNodeInfo: MyNodeInfo = MyNodeInfo(
        myNodeNum = testNodeNoPosition.num,
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
        Position(32.776665, -96.796989, 35, 123), // dallas
        Position(32.960758, -96.733521, 35, 456), // richardson
        Position(32.912901, -96.781776, 35, 789), // north dallas
    )

    private val testNodes = listOf(testNodeNoPosition) + testPositions.mapIndexed { index, it ->
        NodeInfo(
            9 + index,
            MeshUser(
                "+165087653%02d".format(9 + index),
                "Kevin Mester$index",
                "KM$index",
                MeshProtos.HardwareModel.ANDROID_SIM,
                false
            ),
            it
        )
    }

    @Before
    fun createDb(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MeshtasticDatabase::class.java).build()
        nodeInfoDao = database.nodeInfoDao()

        nodeInfoDao.apply{
            putAll(testNodes)
            setMyNodeInfo(myNodeInfo)
        }
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test // node list size
    fun testNodeListSize() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(nodes.size, 4)
    }

    @Test // nodeDBbyNum() re-orders our node at the top of the list
    fun testOurNodeIntoIsFirst() = runBlocking {
        val nodes = nodeInfoDao.nodeDBbyNum().first()
        assertEquals(nodes.values.first(), testNodeNoPosition)
    }

    @Test // getNodeInfo()
    fun testGetNodeInfo() = runBlocking {
        for (node in nodeInfoDao.getNodes().first()) {
            assertEquals(nodeInfoDao.getNodeInfo(node.num), node)
        }
    }
}
