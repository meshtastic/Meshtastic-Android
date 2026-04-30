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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.model.MeshLog
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry

/**
 * Repository interface for managing and retrieving logs from the database.
 *
 * This component provides access to the application's message log, telemetry history, and debug records. It supports
 * reactive queries for packets, telemetry data, and node-specific logs.
 *
 * This interface is shared across platforms via Kotlin Multiplatform (KMP).
 */
@Suppress("TooManyFunctions")
interface MeshLogRepository {
    /** Retrieves all [MeshLog]s in the database, up to [maxItem]. */
    fun getAllLogs(maxItem: Int = DEFAULT_MAX_LOGS): Flow<List<MeshLog>>

    /** Retrieves all [MeshLog]s in the database in the order they were received. */
    fun getAllLogsInReceiveOrder(maxItem: Int = DEFAULT_MAX_LOGS): Flow<List<MeshLog>>

    /** Retrieves all [MeshLog]s in the database without any limit. */
    fun getAllLogsUnbounded(): Flow<List<MeshLog>>

    /** Retrieves all [MeshLog]s associated with a specific [nodeNum] and [portNum]. */
    fun getLogsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshLog>>

    /** Retrieves all [MeshLog]s containing [MeshPacket]s for a specific [nodeNum]. */
    fun getMeshPacketsFrom(nodeNum: Int, portNum: Int = -1): Flow<List<MeshPacket>>

    /** Retrieves telemetry history for a specific node, automatically handling local node redirection. */
    fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>>

    /**
     * Retrieves all outgoing request logs for a specific [targetNodeNum] and [portNum].
     *
     * A request log is defined as an outgoing packet where `want_response` is true.
     */
    fun getRequestLogs(targetNodeNum: Int, portNum: PortNum): Flow<List<MeshLog>>

    /** Returns the cached [MyNodeInfo] from the system logs. */
    fun getMyNodeInfo(): Flow<MyNodeInfo?>

    /** Persists a new log entry to the database. */
    suspend fun insert(log: MeshLog)

    /** Clears all logs from the database. */
    suspend fun deleteAll()

    /** Deletes a specific log entry by its [uuid]. */
    suspend fun deleteLog(uuid: String)

    /** Deletes all logs associated with a specific [nodeNum] and [portNum]. */
    suspend fun deleteLogs(nodeNum: Int, portNum: Int)

    /** Prunes the log database based on the configured [retentionDays]. */
    suspend fun deleteLogsOlderThan(retentionDays: Int)

    companion object {
        const val DEFAULT_MAX_LOGS = 5000
    }
}
