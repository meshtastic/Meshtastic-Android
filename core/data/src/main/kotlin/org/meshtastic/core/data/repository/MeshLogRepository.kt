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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import javax.inject.Inject

@Suppress("TooManyFunctions")
class MeshLogRepository
@Inject
constructor(
    private val dbManager: DatabaseManager,
    private val dispatchers: CoroutineDispatchers,
    private val meshLogPrefs: MeshLogPrefs,
) {
    fun getAllLogs(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> =
        dbManager.currentDb.flatMapLatest { it.meshLogDao().getAllLogs(maxItems) }.flowOn(dispatchers.io).conflate()

    fun getAllLogsUnbounded(): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getAllLogs(Int.MAX_VALUE) }
        .flowOn(dispatchers.io)
        .conflate()

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

    fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getLogsFrom(nodeNum, PortNum.TELEMETRY_APP.value, MAX_MESH_PACKETS) }
        .distinctUntilChanged()
        .mapLatest { list -> list.mapNotNull(::parseTelemetryLog) }
        .flowOn(dispatchers.io)

    fun getLogsFrom(
        nodeNum: Int,
        portNum: Int = PortNum.UNKNOWN_APP.value,
        maxItem: Int = MAX_MESH_PACKETS,
    ): Flow<List<MeshLog>> = dbManager.currentDb
        .flatMapLatest { it.meshLogDao().getLogsFrom(nodeNum, portNum, maxItem) }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    /*
     * Retrieves MeshPackets matching 'nodeNum' and 'portNum'.
     * If 'portNum' is not specified, returns all MeshPackets. Otherwise, filters by 'portNum'.
     */
    fun getMeshPacketsFrom(nodeNum: Int, portNum: Int = PortNum.UNKNOWN_APP.value): Flow<List<MeshPacket>> =
        getLogsFrom(nodeNum, portNum)
            .mapLatest { list -> list.mapNotNull { it.fromRadio.packet } }
            .flowOn(dispatchers.io)

    fun getMyNodeInfo(): Flow<MyNodeInfo?> = getLogsFrom(0, 0)
        .mapLatest { list -> list.firstOrNull { it.myNodeInfo != null }?.myNodeInfo }
        .flowOn(dispatchers.io)

    suspend fun insert(log: MeshLog) = withContext(dispatchers.io) {
        if (!meshLogPrefs.loggingEnabled) return@withContext
        dbManager.currentDb.value.meshLogDao().insert(log)
    }

    suspend fun deleteAll() = withContext(dispatchers.io) { dbManager.currentDb.value.meshLogDao().deleteAll() }

    suspend fun deleteLog(uuid: String) =
        withContext(dispatchers.io) { dbManager.currentDb.value.meshLogDao().deleteLog(uuid) }

    suspend fun deleteLogs(nodeNum: Int, portNum: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.meshLogDao().deleteLogs(nodeNum, portNum) }

    @Suppress("MagicNumber")
    suspend fun deleteLogsOlderThan(retentionDays: Int) = withContext(dispatchers.io) {
        if (retentionDays == MeshLogPrefs.NEVER_CLEAR_RETENTION_DAYS) return@withContext

        val cutoffTimestamp =
            if (retentionDays == MeshLogPrefs.ONE_HOUR_RETENTION_DAYS) {
                System.currentTimeMillis() - (60 * 60 * 1000L)
            } else {
                System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
            }
        dbManager.currentDb.value.meshLogDao().deleteOlderThan(cutoffTimestamp)
    }

    companion object {
        private const val MAX_ITEMS = 500
        private const val MAX_MESH_PACKETS = 10000
        private const val MILLIS_TO_SECONDS = 1000
    }
}
