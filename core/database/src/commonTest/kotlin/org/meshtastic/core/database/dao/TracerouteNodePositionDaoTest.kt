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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.TracerouteNodePositionEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.proto.Position
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TracerouteNodePositionDaoTest {

    private lateinit var database: MeshtasticDatabase
    private lateinit var tracerouteDao: TracerouteNodePositionDao
    private lateinit var meshLogDao: MeshLogDao

    private val logUuid = "traceroute-log-1"

    @BeforeTest
    fun setUp() {
        database = getInMemoryDatabaseBuilder().build()
        tracerouteDao = database.tracerouteNodePositionDao()
        meshLogDao = database.meshLogDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private suspend fun seedParentLog() {
        meshLogDao.insert(
            MeshLog(
                uuid = logUuid,
                message_type = "Packet",
                received_date = 1000L,
                raw_message = "",
                fromNum = 0,
                portNum = 0,
            ),
        )
    }

    private fun position(nodeNum: Int) =
        TracerouteNodePositionEntity(logUuid = logUuid, requestId = 1, nodeNum = nodeNum, position = Position())

    @Test
    fun testReplaceByLogUuidIsAtomic() = runTest {
        seedParentLog()
        tracerouteDao.insertAll(listOf(position(10), position(20)))

        val before = tracerouteDao.getByLogUuid(logUuid).first()
        assertEquals(2, before.size)

        tracerouteDao.replaceByLogUuid(logUuid, listOf(position(30), position(40), position(50)))

        val after = tracerouteDao.getByLogUuid(logUuid).first()
        assertEquals(3, after.size)
        assertEquals(setOf(30, 40, 50), after.map { it.nodeNum }.toSet())
    }

    @Test
    fun testReplaceByLogUuidRollsBackDeletionWhenInsertFails() = runTest {
        seedParentLog()
        tracerouteDao.insertAll(listOf(position(10), position(20)))
        database.useWriterConnection {
            it.executeSQL(
                """
                CREATE TRIGGER fail_traceroute_replacement_insert
                BEFORE INSERT ON traceroute_node_position
                WHEN NEW.node_num = 30
                BEGIN
                    SELECT RAISE(ABORT, 'forced traceroute replacement failure');
                END
                """
                    .trimIndent(),
            )
        }

        assertFailsWith<Exception> { tracerouteDao.replaceByLogUuid(logUuid, listOf(position(30))) }

        val after = tracerouteDao.getByLogUuid(logUuid).first()
        assertEquals(setOf(10, 20), after.map { it.nodeNum }.toSet())
    }

    @Test
    fun testReplaceByLogUuidEmptyEntitiesDeletesAll() = runTest {
        seedParentLog()
        tracerouteDao.insertAll(listOf(position(10), position(20)))

        val before = tracerouteDao.getByLogUuid(logUuid).first()
        assertEquals(2, before.size)

        tracerouteDao.replaceByLogUuid(logUuid, emptyList())

        val after = tracerouteDao.getByLogUuid(logUuid).first()
        assertTrue(after.isEmpty())
    }

    @Test
    fun testReplaceByLogUuidRejectsMismatchedSnapshotBeforeMutation() = runTest {
        seedParentLog()
        tracerouteDao.insertAll(listOf(position(10), position(20)))

        val before = tracerouteDao.getByLogUuid(logUuid).first()
        assertEquals(2, before.size)

        val badEntity =
            TracerouteNodePositionEntity(
                logUuid = "nonexistent-log",
                requestId = 1,
                nodeNum = 99,
                position = Position(),
            )
        assertFailsWith<IllegalArgumentException> {
            tracerouteDao.replaceByLogUuid(logUuid, listOf(position(30), badEntity))
        }

        val after = tracerouteDao.getByLogUuid(logUuid).first()
        assertEquals(2, after.size, "invalid replacement must not mutate the existing snapshot")
        assertEquals(setOf(10, 20), after.map { it.nodeNum }.toSet())
        assertTrue(tracerouteDao.getByLogUuid("nonexistent-log").first().isEmpty())
    }
}
