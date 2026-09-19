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
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.testing.setupTestContext
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the node list's "Only show direct nodes" filter resolves to.
 *
 * Direct is hops_away = 0 AND not via_mqtt. An MQTT-bridged node carries the hop count its uplink gateway heard, so
 * zero hops there says nothing about our own radio, and the row renderers withhold the direct-node signal treatment
 * from it. The query has to agree, or the filter hands back a node it presents as not direct.
 */
abstract class CommonNodeInfoDaoDirectFilterTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var dao: NodeInfoDao

    private val myNodeNum = 42424242

    private suspend fun createDb() {
        setupTestContext()
        database = getInMemoryDatabaseBuilder().build()
        dao = database.nodeInfoDao()
        dao.setMyNodeInfo(
            MyNodeEntity(
                myNodeNum = myNodeNum,
                model = "TBEAM",
                firmwareVersion = "2.8.0",
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = 1L,
                messageTimeoutMsec = 300000,
                minAppVersion = 1,
                maxChannels = 8,
                hasWifi = false,
            ),
        )
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    private fun node(num: Int, hopsAway: Int, viaMqtt: Boolean = false) = NodeEntity(
        num = num,
        user = User.Builder().also { wb -> wb.id = "!$num" }.build(),
        shortName = "N$num",
        hopsAway = hopsAway,
        viaMqtt = viaMqtt,
    )

    private suspend fun visible(onlyDirect: Boolean) =
        dao.getNodes(sort = "last_heard", includeUnknown = true, onlyDirect = onlyDirect, lastHeardMin = -1)
            .first()
            .map { it.node.num }
            .sorted()

    @Test
    fun `the direct filter keeps a node our radio heard at zero hops`() = runTest {
        createDb()
        dao.putAll(listOf(node(1, hopsAway = 0)))

        assertEquals(listOf(1), visible(onlyDirect = true))
    }

    @Test
    fun `the direct filter drops an mqtt node reporting zero hops`() = runTest {
        createDb()
        dao.putAll(listOf(node(1, hopsAway = 0), node(2, hopsAway = 0, viaMqtt = true)))

        assertEquals(listOf(1), visible(onlyDirect = true))
    }

    @Test
    fun `the direct filter drops relayed nodes and nodes never measured`() = runTest {
        createDb()
        dao.putAll(listOf(node(1, hopsAway = 0), node(2, hopsAway = 1), node(3, hopsAway = -1)))

        assertEquals(listOf(1), visible(onlyDirect = true))
    }

    @Test
    fun `the connected radio survives the direct filter`() = runTest {
        createDb()
        dao.putAll(listOf(node(myNodeNum, hopsAway = -1, viaMqtt = true)))

        assertEquals(listOf(myNodeNum), visible(onlyDirect = true))
    }

    @Test
    fun `the filter off keeps every node`() = runTest {
        createDb()
        dao.putAll(listOf(node(1, hopsAway = 0), node(2, hopsAway = 0, viaMqtt = true), node(3, hopsAway = 2)))

        assertEquals(listOf(1, 2, 3), visible(onlyDirect = false))
    }
}
