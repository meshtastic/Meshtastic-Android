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
import org.meshtastic.core.database.DatabaseConstants.SQLITE_MAX_BIND_PARAMETERS
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.proto.PortNum
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MeshLogDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var meshLogDao: MeshLogDao

    private val testFromNum = 42

    private fun logEntry(uuid: String, portNum: Int = PortNum.TELEMETRY_APP.value, time: Long) = MeshLog(
        uuid = uuid,
        message_type = "Packet",
        received_date = time,
        raw_message = "",
        fromNum = testFromNum,
        portNum = portNum,
    )

    /** Places [first] in a full bind-param chunk and [second] in the next chunk without inserting filler rows. */
    private fun crossChunkUuids(first: String, second: String): List<String> = buildList {
        add(first)
        repeat(SQLITE_MAX_BIND_PARAMETERS - 1) { add("missing-$it") }
        add(second)
    }

    @BeforeTest
    fun setUp() {
        database = getInMemoryDatabaseBuilder().build()
        meshLogDao = database.meshLogDao()
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    @Test
    fun testDeleteLogsByUuidAtomicRemovesAll() = runTest {
        meshLogDao.insert(logEntry("log-1", time = 100))
        meshLogDao.insert(logEntry("log-2", time = 200))
        meshLogDao.insert(logEntry("log-3", time = 300))

        val uuids =
            meshLogDao.getLogsFrom(testFromNum, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE).first().map { it.uuid }
        assertEquals(3, uuids.size)

        meshLogDao.deleteLogsByUuidAtomic(uuids)

        val remaining = meshLogDao.getLogsFrom(testFromNum, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE).first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun testDeleteLogsByUuidAtomicEmptyListIsNoOp() = runTest {
        meshLogDao.insert(logEntry("log-1", time = 100))
        meshLogDao.insert(logEntry("log-2", time = 200))

        val before = meshLogDao.getLogsFrom(testFromNum, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE).first()
        assertEquals(2, before.size)

        meshLogDao.deleteLogsByUuidAtomic(emptyList())

        val after = meshLogDao.getLogsFrom(testFromNum, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE).first()
        assertEquals(2, after.size)
    }

    @Test
    fun testDeleteLogsByUuidAtomicCrossesChunkBoundary() = runTest {
        val firstUuid = "first-chunk"
        val secondUuid = "second-chunk"
        meshLogDao.insert(logEntry(firstUuid, time = 100))
        meshLogDao.insert(logEntry(secondUuid, time = 200))
        // Insert one log from a different fromNum that should survive the delete.
        val survivor =
            MeshLog(
                uuid = "survivor",
                message_type = "Packet",
                received_date = 0L,
                raw_message = "",
                fromNum = 999,
                portNum = PortNum.TELEMETRY_APP.value,
            )
        meshLogDao.insert(survivor)

        meshLogDao.deleteLogsByUuidAtomic(crossChunkUuids(firstUuid, secondUuid))

        val remaining = meshLogDao.getLogsFrom(testFromNum, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE).first()
        assertTrue(remaining.isEmpty(), "all selected logs should be deleted across the chunk boundary")

        val survivorStillPresent = meshLogDao.getLogsFrom(999, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE).first()
        assertEquals(1, survivorStillPresent.size, "unselected log from a different fromNum should remain")
    }

    @Test
    fun testDeleteLogsByUuidAtomicRollsBackEarlierChunkWhenLaterChunkFails() = runTest {
        val firstUuid = "first-chunk"
        val secondUuid = "failing-second-chunk"
        meshLogDao.insert(logEntry(firstUuid, time = 100))
        meshLogDao.insert(logEntry(secondUuid, time = 200))
        database.useWriterConnection {
            it.executeSQL(
                """
                CREATE TRIGGER fail_second_log_delete_chunk
                BEFORE DELETE ON log
                WHEN OLD.uuid = '$secondUuid'
                BEGIN
                    SELECT RAISE(ABORT, 'forced later-chunk delete failure');
                END
                """
                    .trimIndent(),
            )
        }
        assertFails { meshLogDao.deleteLogsByUuidAtomic(crossChunkUuids(firstUuid, secondUuid)) }

        val remaining = meshLogDao.getLogsFrom(testFromNum, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE).first()
        assertEquals(setOf(firstUuid, secondUuid), remaining.map { it.uuid }.toSet())
    }

    @Test
    fun testGetLogsSnapshotPageTraversesEqualTimestampsWithoutDuplicates() = runTest {
        meshLogDao.insert(logEntry("log-a", time = 300))
        meshLogDao.insert(logEntry("log-c", time = 200))
        meshLogDao.insert(logEntry("log-b", time = 200))
        meshLogDao.insert(logEntry("log-a2", time = 100))

        val first =
            meshLogDao.getLogsSnapshotPage(
                testFromNum,
                PortNum.TELEMETRY_APP.value,
                beforeReceivedDate = null,
                beforeUuid = null,
                pageSize = 2,
            )
        val cursor = first.last()
        val second =
            meshLogDao.getLogsSnapshotPage(
                testFromNum,
                PortNum.TELEMETRY_APP.value,
                beforeReceivedDate = cursor.received_date,
                beforeUuid = cursor.uuid,
                pageSize = 2,
            )

        assertEquals(listOf("log-a", "log-c", "log-b", "log-a2"), (first + second).map { it.uuid })
    }

    @Test
    fun testGetLogsSnapshotReturnsMatchingLogs() = runTest {
        meshLogDao.insert(logEntry("log-1", portNum = PortNum.TELEMETRY_APP.value, time = 300))
        meshLogDao.insert(logEntry("log-2", portNum = PortNum.TELEMETRY_APP.value, time = 100))
        meshLogDao.insert(logEntry("log-3", portNum = PortNum.POSITION_APP.value, time = 200))

        val snapshot = meshLogDao.getLogsSnapshot(testFromNum, PortNum.TELEMETRY_APP.value, Int.MAX_VALUE)
        assertEquals(2, snapshot.size, "only telemetry logs should match")
        assertEquals(listOf("log-1", "log-2"), snapshot.map { it.uuid }, "telemetry logs in DESC order")
    }
}
