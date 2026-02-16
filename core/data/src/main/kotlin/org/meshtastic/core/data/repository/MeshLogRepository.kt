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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import javax.inject.Inject

/**
 * Repository responsible for managing mesh radio logs, telemetry history, and packet audit trails.
 *
 * This repository centralizes the mapping of local node numbers to a stable identifier ([MeshLog.NODE_NUM_LOCAL]) to
 * ensure data continuity across hardware swaps.
 */
@Suppress("TooManyFunctions")
class MeshLogRepository
@Inject
constructor(
    private val dbManager: DatabaseManager,
    private val dispatchers: CoroutineDispatchers,
    private val meshLogPrefs: MeshLogPrefs,
    private val nodeInfoReadDataSource: NodeInfoReadDataSource,
) {
    /** Returns all logs up to [maxItems], ordered by newest first. */
    fun getAllLogs(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> =
        dbManager.currentDb.flatMapLatest { it.meshLogDao().getAllLogs(maxItems) }.flowOn(dispatchers.io).conflate()

    /** Returns all logs in the database without any limit. */
    fun getAllLogsUnbounded(): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getAllLogs(Int.MAX_VALUE) }
        .flowOn(dispatchers.io)
        .conflate()

    /** Returns logs in chronological order (oldest first). */
    fun getAllLogsInReceiveOrder(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getAllLogsInReceiveOrder(maxItems) }
        .flowOn(dispatchers.io)
        .conflate()

    private fun parseTelemetryLog(log: MeshLog): Telemetry? = runCatching {
        val payload = log.fromRadio.packet?.decoded?.payload ?: return@runCatching null
        val telemetry = Telemetry.ADAPTER.decode(payload)
        telemetry.copy(
            time = (log.received_date / MILLIS_TO_SECONDS).toInt(),
            environment_metrics =
            telemetry.environment_metrics?.let { metrics ->
                metrics.copy(
                    temperature = metrics.temperature ?: Float.NaN,
                    relative_humidity = metrics.relative_humidity ?: Float.NaN,
                    soil_temperature = metrics.soil_temperature ?: Float.NaN,
                    barometric_pressure = metrics.barometric_pressure ?: Float.NaN,
                    gas_resistance = metrics.gas_resistance ?: Float.NaN,
                    voltage = metrics.voltage ?: Float.NaN,
                    current = metrics.current ?: Float.NaN,
                    lux = metrics.lux ?: Float.NaN,
                    uv_lux = metrics.uv_lux ?: Float.NaN,
                    iaq = metrics.iaq ?: Int.MIN_VALUE,
                    soil_moisture = metrics.soil_moisture ?: Int.MIN_VALUE,
                )
            },
        )
    }
        .getOrNull()

    /** Returns a flow that maps a [nodeNum] to [MeshLog.NODE_NUM_LOCAL] if it is the locally connected node. */
    private fun effectiveLogId(nodeNum: Int): Flow<Int> = nodeInfoReadDataSource
        .myNodeInfoFlow()
        .map { info -> if (nodeNum == info?.myNodeNum) MeshLog.NODE_NUM_LOCAL else nodeNum }
        .distinctUntilChanged()

    /** Retrieves telemetry history for a specific node, automatically handling local node redirection. */
    fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> = effectiveLogId(nodeNum)
        .flatMapLatest { logId ->
            dbManager.currentDb
                .flatMapLatest { it.meshLogDao().getLogsFrom(logId, PortNum.TELEMETRY_APP.value, MAX_MESH_PACKETS) }
                .distinctUntilChanged()
                .mapLatest { list -> list.mapNotNull(::parseTelemetryLog) }
        }
        .flowOn(dispatchers.io)

    /** Retrieves general logs for a specific node and optional [portNum]. */
    fun getLogsFrom(nodeNum: Int, portNum: Int = PORT_ANY, maxItem: Int = MAX_MESH_PACKETS): Flow<List<MeshLog>> =
        effectiveLogId(nodeNum)
            .flatMapLatest { logId ->
                dbManager.currentDb
                    .flatMapLatest { it.meshLogDao().getLogsFrom(logId, portNum, maxItem) }
                    .distinctUntilChanged()
            }
            .flowOn(dispatchers.io)

    /**
     * Retrieves decoded [MeshPacket] objects for a specific node, automatically handling local node redirection. If
     * [portNum] is not specified, returns packets from all applications.
     */
    fun getMeshPacketsFrom(nodeNum: Int, portNum: Int = PORT_ANY): Flow<List<MeshPacket>> =
        getLogsFrom(nodeNum, portNum)
            .mapLatest { list -> list.mapNotNull { it.fromRadio.packet } }
            .flowOn(dispatchers.io)

    /**
     * Retrieves logs for requests sent *from* the local node to a specific [toNodeNum]. Useful for tracking traceroute
     * or neighbor info request history.
     */
    fun getRequestLogs(toNodeNum: Int, portNum: PortNum): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { db ->
            db.meshLogDao().getLogsFrom(MeshLog.NODE_NUM_LOCAL, portNum.value, MAX_MESH_PACKETS)
        }
        .mapLatest { list ->
            list.filter { log ->
                val pkt = log.fromRadio.packet
                val decoded = pkt?.decoded
                pkt != null &&
                    decoded != null &&
                    decoded.want_response == true &&
                    log.fromNum == MeshLog.NODE_NUM_LOCAL &&
                    pkt.to == toNodeNum
            }
        }
        .flowOn(dispatchers.io)

    /** Returns the cached [MyNodeInfo] from the system logs. */
    fun getMyNodeInfo(): Flow<MyNodeInfo?> = dbManager.currentDb
        .flatMapLatest { db -> db.meshLogDao().getLogsFrom(MeshLog.NODE_NUM_LOCAL, 0, MAX_MESH_PACKETS) }
        .mapLatest { list -> list.firstOrNull { it.myNodeInfo != null }?.myNodeInfo }
        .flowOn(dispatchers.io)

    /** Persists a new log entry to the database if logging is enabled in preferences. */
    suspend fun insert(log: MeshLog) = withContext(dispatchers.io) {
        if (!meshLogPrefs.loggingEnabled) return@withContext
        dbManager.currentDb.value.meshLogDao().insert(log)
    }

    /** Clears all logs from the database. */
    suspend fun deleteAll() = withContext(dispatchers.io) { dbManager.currentDb.value.meshLogDao().deleteAll() }

    /** Deletes a specific log entry by its [uuid]. */
    suspend fun deleteLog(uuid: String) =
        withContext(dispatchers.io) { dbManager.currentDb.value.meshLogDao().deleteLog(uuid) }

    /** Deletes all logs associated with a specific [nodeNum] and [portNum]. */
    suspend fun deleteLogs(nodeNum: Int, portNum: Int) = withContext(dispatchers.io) {
        val myNodeNum = nodeInfoReadDataSource.myNodeInfoFlow().firstOrNull()?.myNodeNum
        val logId = if (nodeNum == myNodeNum) MeshLog.NODE_NUM_LOCAL else nodeNum
        dbManager.currentDb.value.meshLogDao().deleteLogs(logId, portNum)
    }

    /** Prunes the log database based on the configured [retentionDays]. */
    @Suppress("MagicNumber")
    suspend fun deleteLogsOlderThan(retentionDays: Int) = withContext(dispatchers.io) {
        if (retentionDays == MeshLogPrefs.NEVER_CLEAR_RETENTION_DAYS) return@withContext

        val cutoffTimestamp =
            if (retentionDays == MeshLogPrefs.ONE_HOUR_RETENTION_DAYS) {
                nowMillis - TimeConstants.ONE_HOUR.inWholeMilliseconds
            } else {
                nowMillis - (retentionDays * TimeConstants.ONE_DAY.inWholeMilliseconds)
            }
        dbManager.currentDb.value.meshLogDao().deleteOlderThan(cutoffTimestamp)
    }

    companion object {
        private const val MAX_ITEMS = 500
        private const val MAX_MESH_PACKETS = 10000
        private const val MILLIS_TO_SECONDS = 1000
        private const val PORT_ANY = -1
    }
}
