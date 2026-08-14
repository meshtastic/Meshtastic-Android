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
import org.meshtastic.core.model.util.TELEMETRY_CHANNEL_COUNT
import org.meshtastic.core.model.util.adcVoltage
import org.meshtastic.core.model.util.oneWireTemperature
import org.meshtastic.core.model.util.withAdcVoltage
import org.meshtastic.core.model.util.withLegacyOneWireTemperatures
import org.meshtastic.core.model.util.withOneWireTemperature
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshLogRepository.Companion.DEFAULT_MAX_LOGS
import org.meshtastic.core.repository.MeshLogRetention
import org.meshtastic.proto.EnvironmentMetrics
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
    override fun getAllLogs(maxItem: Int): Flow<List<MeshLog>> = dbManager
        .observeCurrentDb { it.meshLogDao().getAllLogs(maxItem) }
        .map { list -> list.map { it.asExternalModel() } }
        .flowOn(dispatchers.io)

    /** Retrieves all [MeshLog]s in the database in the order they were received. */
    override fun getAllLogsInReceiveOrder(maxItem: Int): Flow<List<MeshLog>> = dbManager
        .observeCurrentDb { it.meshLogDao().getAllLogsInReceiveOrder(maxItem) }
        .map { list -> list.map { it.asExternalModel() } }
        .flowOn(dispatchers.io)

    /** Retrieves all [MeshLog]s in the database without any limit. */
    override fun getAllLogsUnbounded(): Flow<List<MeshLog>> = getAllLogs(Int.MAX_VALUE)

    /** Retrieves all [MeshLog]s associated with a specific [nodeNum] and [portNum]. */
    override fun getLogsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshLog>> = dbManager
        .observeCurrentDb { it.meshLogDao().getLogsFrom(nodeNum, portNum, DEFAULT_MAX_LOGS) }
        .map { list -> list.map { it.asExternalModel() } }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    /** Retrieves all [MeshLog]s containing [MeshPacket]s for a specific [nodeNum]. */
    override fun getMeshPacketsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshPacket>> =
        getLogsFrom(nodeNum, portNum).map { list -> list.mapNotNull { it.fromRadio.packet } }.flowOn(dispatchers.io)

    /** Retrieves telemetry history for a specific node, automatically handling local node redirection. */
    override fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> = effectiveLogId(nodeNum)
        .flatMapLatest { logId ->
            dbManager
                .observeCurrentDb {
                    it.meshLogDao().getLogsFrom(logId, PortNum.TELEMETRY_APP.value, DEFAULT_MAX_LOGS)
                }
                .distinctUntilChanged()
                .mapLatest { list -> list.map { it.asExternalModel() }.mapNotNull(::parseTelemetryLog) }
        }
        .flowOn(dispatchers.io)

    /**
     * Retrieves all outgoing request logs for a specific [targetNodeNum] and [portNum].
     *
     * A request log is defined as an outgoing packet (`fromNum = 0`) where `want_response` is true.
     */
    override fun getRequestLogs(targetNodeNum: Int, portNum: PortNum): Flow<List<MeshLog>> = dbManager
        .observeCurrentDb { it.meshLogDao().getLogsFrom(MeshLog.NODE_NUM_LOCAL, portNum.value, DEFAULT_MAX_LOGS) }
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

    private fun parseTelemetryLog(log: MeshLog): Telemetry? = runCatching {
        val decoded = log.fromRadio.packet?.decoded ?: return@runCatching null
        // Requests for telemetry (want_response = true) should not be logged as data points.
        if (decoded.want_response == true) return@runCatching null

        val telemetry = Telemetry.ADAPTER.decode(decoded.payload)
        telemetry.copy(
            time = (log.received_date / MILLIS_PER_SEC).toInt(),
            environment_metrics = telemetry.environment_metrics?.withSentinelsForAbsentReadings(),
        )
    }
        .getOrNull()

    /** Returns a flow that maps a [nodeNum] to [MeshLog.NODE_NUM_LOCAL] if it is the locally connected node. */
    private fun effectiveLogId(nodeNum: Int): Flow<Int> = nodeInfoReadDataSource
        .myNodeInfoFlow()
        .map { info -> if (nodeNum == info?.myNodeNum) MeshLog.NODE_NUM_LOCAL else nodeNum }
        .distinctUntilChanged()

    /** Returns the cached [MyNodeInfo] from the system logs. */
    override fun getMyNodeInfo(): Flow<MyNodeInfo?> = dbManager
        .observeCurrentDb { db -> db.meshLogDao().getLogsFrom(MeshLog.NODE_NUM_LOCAL, 0, DEFAULT_MAX_LOGS) }
        .mapLatest { list -> list.map { it.asExternalModel() }.firstOrNull { it.myNodeInfo != null }?.myNodeInfo }
        .flowOn(dispatchers.io)

    // Writes go through withDb so they register with the cross-transport merge drain barrier (see DatabaseProvider).

    /** Persists a new log entry to the database if logging is enabled in preferences. */
    override suspend fun insert(log: MeshLog) = withContext(dispatchers.io) {
        if (!meshLogPrefs.loggingEnabled.value) return@withContext
        dbManager.withDb { it.meshLogDao().insert(log.asEntity()) }
        Unit
    }

    /** Clears all logs from the database. */
    override suspend fun deleteAll() {
        withContext(dispatchers.io) { dbManager.withDb { it.meshLogDao().deleteAll() } }
    }

    /** Deletes a specific log entry by its [uuid]. */
    override suspend fun deleteLog(uuid: String) {
        withContext(dispatchers.io) { dbManager.withDb { it.meshLogDao().deleteLog(uuid) } }
    }

    /** Deletes all logs associated with a specific [nodeNum] and [portNum]. */
    override suspend fun deleteLogs(nodeNum: Int, portNum: Int) = withContext(dispatchers.io) {
        val myNodeNum = nodeInfoReadDataSource.myNodeInfoFlow().firstOrNull()?.myNodeNum
        val logId = if (nodeNum == myNodeNum) MeshLog.NODE_NUM_LOCAL else nodeNum
        dbManager.withDb { it.meshLogDao().deleteLogs(logId, portNum) }
        Unit
    }

    /**
     * Deletes only local stats telemetry logs for [nodeNum], preserving other telemetry types. The bounded keyset scan
     * and atomic deletion remain under one manager-tracked database lease, while protobuf parsing runs on the compute
     * dispatcher.
     */
    override suspend fun deleteLocalStatsLogs(nodeNum: Int) = withContext(dispatchers.io) {
        val myNodeNum = nodeInfoReadDataSource.myNodeInfoFlow().firstOrNull()?.myNodeNum
        val logId = if (nodeNum == myNodeNum) MeshLog.NODE_NUM_LOCAL else nodeNum
        dbManager.withDb { selectedDb ->
            val dao = selectedDb.meshLogDao()
            val uuidsToDelete = mutableListOf<String>()
            var beforeReceivedDate: Long? = null
            var beforeUuid: String? = null
            do {
                val page =
                    dao.getLogsSnapshotPage(
                        fromNum = logId,
                        portNum = PortNum.TELEMETRY_APP.value,
                        beforeReceivedDate = beforeReceivedDate,
                        beforeUuid = beforeUuid,
                        pageSize = TELEMETRY_SNAPSHOT_PAGE_SIZE,
                    )
                uuidsToDelete +=
                    withContext(dispatchers.default) {
                        page
                            .asSequence()
                            .map { it.asExternalModel() }
                            .filter { parseTelemetryLog(it)?.local_stats != null }
                            .map { it.uuid }
                            .toList()
                    }
                page.lastOrNull()?.let { last ->
                    beforeReceivedDate = last.received_date
                    beforeUuid = last.uuid
                }
            } while (page.size == TELEMETRY_SNAPSHOT_PAGE_SIZE)

            if (uuidsToDelete.isNotEmpty()) {
                dao.deleteLogsByUuidAtomic(uuidsToDelete)
            }
        }
        Unit
    }

    /**
     * Prunes the log database based on the configured [retentionDays]. The sentinel values are resolved by
     * [MeshLogRetention], so "never delete" is a no-op and "1 hour" trims to the last hour rather than scaling the
     * sentinel by days.
     */
    override suspend fun deleteLogsOlderThan(retentionDays: Int) = withContext(dispatchers.io) {
        val window = MeshLogRetention.windowOrNull(retentionDays) ?: return@withContext
        val cutoffTime = nowMillis - window.inWholeMilliseconds
        dbManager.withDb { it.meshLogDao().deleteOlderThan(cutoffTime) }
        Unit
    }

    companion object {
        private const val MILLIS_PER_SEC = 1000L
        private const val TELEMETRY_SNAPSHOT_PAGE_SIZE = 512
    }
}

/**
 * Replaces absent optional readings with the sentinel the graphing layer filters on, so every field reaches the charts
 * through one representation.
 *
 * Only presence is normalized: a reported `0` is a real reading on every field here and is preserved. Historical logs
 * predating firmware 2.8 carry 1-Wire temperatures in the deprecated repeated field, so those are lifted onto the
 * per-channel fields first — see [withLegacyOneWireTemperatures].
 */
private fun EnvironmentMetrics.withSentinelsForAbsentReadings(): EnvironmentMetrics =
    withLegacyOneWireTemperatures().withScalarSentinels().withChannelSentinels()

private fun EnvironmentMetrics.withScalarSentinels(): EnvironmentMetrics = copy(
    temperature = temperature ?: Float.NaN,
    relative_humidity = relative_humidity ?: Float.NaN,
    soil_temperature = soil_temperature ?: Float.NaN,
    barometric_pressure = barometric_pressure ?: Float.NaN,
    gas_resistance = gas_resistance ?: Float.NaN,
    voltage = voltage ?: Float.NaN,
    current = current ?: Float.NaN,
    lux = lux ?: Float.NaN,
    uv_lux = uv_lux ?: Float.NaN,
    iaq = iaq ?: Int.MIN_VALUE,
    soil_moisture = soil_moisture ?: Int.MIN_VALUE,
)

private fun EnvironmentMetrics.withChannelSentinels(): EnvironmentMetrics =
    (0 until TELEMETRY_CHANNEL_COUNT).fold(this) { metrics, channel ->
        metrics
            .withOneWireTemperature(channel, metrics.oneWireTemperature(channel) ?: Float.NaN)
            .withAdcVoltage(channel, metrics.adcVoltage(channel) ?: Float.NaN)
    }
