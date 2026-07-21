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

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.DatabaseConstants.SQLITE_MAX_BIND_PARAMETERS
import org.meshtastic.core.database.entity.MeshLog

@Dao
@Suppress("TooManyFunctions")
interface MeshLogDao {

    companion object {
        /** Shared SQL for querying logs by from_num and port_num, used by both Flow and snapshot variants. */
        private const val LOGS_FROM_QUERY =
            """
            SELECT * FROM log
            WHERE from_num = :fromNum AND (:portNum = -1 OR port_num = :portNum)
            ORDER BY received_date DESC LIMIT :maxItem
            """
    }

    @Query("SELECT * FROM log ORDER BY received_date DESC LIMIT :maxItem")
    fun getAllLogs(maxItem: Int): Flow<List<MeshLog>>

    @Query("SELECT * FROM log ORDER BY received_date ASC LIMIT :maxItem")
    fun getAllLogsInReceiveOrder(maxItem: Int): Flow<List<MeshLog>>

    /**
     * Retrieves [MeshLog]s matching 'from_num' (nodeNum) and 'port_num' (PortNum).
     *
     * @param portNum If -1, returns all logs regardless of port. If 0, returns logs with port 0.
     */
    @Query(LOGS_FROM_QUERY)
    fun getLogsFrom(fromNum: Int, portNum: Int, maxItem: Int): Flow<List<MeshLog>>

    @Insert suspend fun insert(log: MeshLog)

    /**
     * Snapshot + IGNORE-insert used by DatabaseMerger to carry telemetry/position/traceroute history across transports.
     * uuid is a unique string per row, so IGNORE is just belt-and-suspenders against a rare clash.
     */
    @Query("SELECT * FROM log")
    suspend fun getAllLogsSnapshot(): List<MeshLog>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(logs: List<MeshLog>)

    @Query("DELETE FROM log")
    suspend fun deleteAll()

    @Query("DELETE FROM log WHERE uuid = :uuid")
    suspend fun deleteLog(uuid: String)

    @Query("DELETE FROM log WHERE uuid IN (:uuids)")
    suspend fun deleteLogsByUuid(uuids: List<String>)

    @Query("DELETE FROM log WHERE from_num = :fromNum AND port_num = :portNum")
    suspend fun deleteLogs(fromNum: Int, portNum: Int)

    @Query("DELETE FROM log WHERE received_date < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    /**
     * Suspend snapshot variant of [getLogsFrom] for one-shot reads (no Flow observer overhead). Used when a caller
     * needs to read-then-process-then-delete in two steps — the selection requires Kotlin parsing so it stays outside
     * the delete transaction, but the delete itself is atomic (see [deleteLogsByUuidAtomic]).
     */
    @Query(LOGS_FROM_QUERY)
    suspend fun getLogsSnapshot(fromNum: Int, portNum: Int, maxItem: Int): List<MeshLog>

    /**
     * Returns one deterministic keyset page for bounded snapshot processing. [beforeReceivedDate] and [beforeUuid]
     * identify the last row of the previous page; null values start from the newest row.
     */
    @Query(
        """
        SELECT * FROM log
        WHERE from_num = :fromNum
          AND (:portNum = -1 OR port_num = :portNum)
          AND (
            :beforeReceivedDate IS NULL
            OR received_date < :beforeReceivedDate
            OR (received_date = :beforeReceivedDate AND uuid < :beforeUuid)
          )
        ORDER BY received_date DESC, uuid DESC
        LIMIT :pageSize
        """,
    )
    suspend fun getLogsSnapshotPage(
        fromNum: Int,
        portNum: Int,
        beforeReceivedDate: Long?,
        beforeUuid: String?,
        pageSize: Int,
    ): List<MeshLog>

    /**
     * Atomically deletes all logs matching [uuids], chunking internally to stay under SQLite's bind-parameter limit.
     * The entire batch is all-or-nothing: a failure rolls back every chunk.
     */
    @Transaction
    suspend fun deleteLogsByUuidAtomic(uuids: List<String>) {
        if (uuids.isEmpty()) return
        for (chunk in uuids.chunked(SQLITE_MAX_BIND_PARAMETERS)) {
            deleteLogsByUuid(chunk)
        }
    }
}
