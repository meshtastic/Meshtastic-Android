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
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshLogRepository.Companion.DEFAULT_MAX_LOGS
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry

/**
 * Repository implementation for managing and retrieving logs from the local database.
 *
 * This repository provides methods for inserting, deleting, and querying logs, including specialized methods for
 * telemetry and traceroute data.
 */
@Suppress("TooManyFunctions")
@Single
open class MeshLogRepositoryImpl(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
    private val meshLogPrefs: MeshLogPrefs,
    private val nodeInfoReadDataSource: NodeInfoReadDataSource,
) : MeshLogRepository {

    /** Retrieves all [MeshLog]s in the database, up to [maxItem]. */
    override fun getAllLogs(maxItem: Int): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getAllLogs(maxItem) }
        .map { list -> list.map { it.asExternalModel() } }
        .flowOn(dispatchers.io)

    /** Retrieves all [MeshLog]s in the database in the order they were received. */
    override fun getAllLogsInReceiveOrder(maxItem: Int): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getAllLogsInReceiveOrder(maxItem) }
        .map { list -> list.map { it.asExternalModel() } }
        .flowOn(dispatchers.io)

    /** Retrieves all [MeshLog]s in the database without any limit. */
    override fun getAllLogsUnbounded(): Flow<List<MeshLog>> = getAllLogs(Int.MAX_VALUE)

    /** Retrieves all [MeshLog]s associated with a specific [nodeNum] and [portNum]. */
    override fun getLogsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getLogsFrom(nodeNum, portNum, DEFAULT_MAX_LOGS) }
        .map { list -> list.map { it.asExternalModel() } }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    /** Retrieves all [MeshLog]s containing [MeshPacket]s for a specific [nodeNum]. */
    override fun getMeshPacketsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshPacket>> =
        getLogsFrom(nodeNum, portNum).map { list -> list.mapNotNull { it.fromRadio.packet } }.flowOn(dispatchers.io)

    /** Retrieves telemetry history for a specific node, automatically handling local node redirection. */
    override fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> = effectiveLogId(nodeNum)
        .flatMapLatest { logId ->
            dbManager.currentDb
                .flatMapLatest { it.meshLogDao().getLogsFrom(logId, PortNum.TELEMETRY_APP.value, DEFAULT_MAX_LOGS) }
                .distinctUntilChanged()
                .mapLatest { list -> list.map { it.asExternalModel() }.mapNotNull(::parseTelemetryLog) }
        }
        .flowOn(dispatchers.io)

    /**
     * Retrieves all outgoing request logs for a specific [targetNodeNum] and [portNum].
     *
     * A request log is defined as an outgoing packet (`fromNum = 0`) where `want_response` is true.
     */
    override fun getRequestLogs(targetNodeNum: Int, portNum: PortNum): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getLogsFrom(MeshLog.NODE_NUM_LOCAL, portNum.value, DEFAULT_MAX_LOGS) }
        .map { list ->
            list
                .map { it.asExternalModel() }
                .filter { log ->
                    val packet = log.fromRadio.packet ?: return@filter false
                    log.fromNum == MeshLog.NODE_NUM_LOCAL &&
                        packet.to == targetNodeNum &&
                        packet.decoded?.want_response == true
                }
        }
        .distinctUntilChanged()
        .conflate()

    @Suppress("CyclomaticComplexMethod")
    private fun parseTelemetryLog(log: MeshLog): Telemetry? = runCatching {
        val decoded = log.fromRadio.packet?.decoded ?: return@runCatching null
        // Requests for telemetry (want_response = true) should not be logged as data points.
        if (decoded.want_response == true) return@runCatching null

        val telemetry = Telemetry.ADAPTER.decode(decoded.payload)
        telemetry.copy(
            time = (log.received_date / MILLIS_PER_SEC).toInt(),
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

    /** Returns the cached [MyNodeInfo] from the system logs. */
    override fun getMyNodeInfo(): Flow<MyNodeInfo?> = dbManager.currentDb
        .flatMapLatest { db -> db.meshLogDao().getLogsFrom(MeshLog.NODE_NUM_LOCAL, 0, DEFAULT_MAX_LOGS) }
        .mapLatest { list -> list.map { it.asExternalModel() }.firstOrNull { it.myNodeInfo != null }?.myNodeInfo }
        .flowOn(dispatchers.io)

    /** Persists a new log entry to the database if logging is enabled in preferences. */
    override suspend fun insert(log: MeshLog) = withContext(dispatchers.io) {
        if (!meshLogPrefs.loggingEnabled.value) return@withContext
        dbManager.currentDb.value.meshLogDao().insert(log.asEntity())
    }

    /** Clears all logs from the database. */
    override suspend fun deleteAll() =
        withContext(dispatchers.io) { dbManager.currentDb.value.meshLogDao().deleteAll() }

    /** Deletes a specific log entry by its [uuid]. */
    override suspend fun deleteLog(uuid: String) =
        withContext(dispatchers.io) { dbManager.currentDb.value.meshLogDao().deleteLog(uuid) }

    /** Deletes all logs associated with a specific [nodeNum] and [portNum]. */
    override suspend fun deleteLogs(nodeNum: Int, portNum: Int) = withContext(dispatchers.io) {
        val myNodeNum = nodeInfoReadDataSource.myNodeInfoFlow().firstOrNull()?.myNodeNum
        val logId = if (nodeNum == myNodeNum) MeshLog.NODE_NUM_LOCAL else nodeNum
        dbManager.currentDb.value.meshLogDao().deleteLogs(logId, portNum)
    }

    /** Prunes the log database based on the configured [retentionDays]. */
    @Suppress("MagicNumber")
    override suspend fun deleteLogsOlderThan(retentionDays: Int) = withContext(dispatchers.io) {
        val cutoffTime = nowMillis - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        dbManager.currentDb.value.meshLogDao().deleteOlderThan(cutoffTime)
    }

    companion object {
        private const val MILLIS_PER_SEC = 1000L
    }
}
