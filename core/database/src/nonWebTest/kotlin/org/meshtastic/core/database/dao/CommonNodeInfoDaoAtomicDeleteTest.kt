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

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.testing.setupTestContext
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class CommonNodeInfoDaoAtomicDeleteTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var dao: NodeInfoDao

    @BeforeTest
    fun setUp() {
        setupTestContext()
        database = getInMemoryDatabaseBuilder().build()
        dao = database.nodeInfoDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteNodeAndMetadataRemovesBothRows() = runTest {
        seedNodeAndMetadata(NODE_NUM, "test")

        dao.deleteNodeAndMetadata(NODE_NUM)

        assertNull(dao.getNodeByNum(NODE_NUM))
        assertTrue(dao.getAllMetadataSnapshot().none { it.num == NODE_NUM })
    }

    @Test
    fun deleteNodesAndMetadataChunksPastBindLimit() = runTest {
        val secondNum = NODE_NUM + 1
        seedNodeAndMetadata(NODE_NUM, "first")
        seedNodeAndMetadata(secondNum, "second")
        val nodeNums = buildList {
            add(NODE_NUM)
            repeat(NodeInfoDao.MAX_BIND_PARAMS - 1) { add(-(it + 1)) }
            add(secondNum)
        }

        dao.deleteNodesAndMetadata(nodeNums)

        assertNull(dao.getNodeByNum(NODE_NUM))
        assertNull(dao.getNodeByNum(secondNum))
        assertTrue(dao.getAllMetadataSnapshot().none { it.num == NODE_NUM || it.num == secondNum })
    }

    @Test
    fun deleteNodeAndMetadataRollsBackWhenMetadataDeleteFails() = runTest {
        seedNodeAndMetadata(NODE_NUM, "test")
        database.useWriterConnection {
            it.executeSQL(
                """
                CREATE TRIGGER fail_metadata_delete
                BEFORE DELETE ON metadata
                WHEN OLD.num = $NODE_NUM
                BEGIN
                    SELECT RAISE(ABORT, 'forced metadata delete failure');
                END
                """
                    .trimIndent(),
            )
        }

        assertFails { dao.deleteNodeAndMetadata(NODE_NUM) }

        assertNotNull(dao.getNodeByNum(NODE_NUM), "node deletion must roll back with metadata deletion")
        assertTrue(dao.getAllMetadataSnapshot().any { it.num == NODE_NUM }, "metadata must remain after rollback")
    }

    private suspend fun seedNodeAndMetadata(nodeNum: Int, label: String) {
        dao.upsert(NodeEntity(num = nodeNum, user = User(id = "!node-$label")))
        dao.upsert(MetadataEntity(num = nodeNum, proto = DeviceMetadata(firmware_version = label)))
    }

    private companion object {
        const val NODE_NUM = 0x1234
    }
}
