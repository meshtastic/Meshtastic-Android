/*
 * Copyright (c) 2025 Meshtastic LLC
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

import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.dao.MeshLogDao
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.TelemetryProtos.Telemetry
import javax.inject.Inject

@Suppress("TooManyFunctions")
class MeshLogRepository
@Inject
constructor(
    private val meshLogDaoLazy: Lazy<MeshLogDao>,
    private val dispatchers: CoroutineDispatchers,
) {
    private val meshLogDao by lazy { meshLogDaoLazy.get() }

    fun getAllLogs(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> =
        meshLogDao.getAllLogs(maxItems).flowOn(dispatchers.io).conflate()

    fun getAllLogsInReceiveOrder(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> =
        meshLogDao.getAllLogsInReceiveOrder(maxItems).flowOn(dispatchers.io).conflate()

    private fun parseTelemetryLog(log: MeshLog): Telemetry? = runCatching {
        Telemetry.parseFrom(log.fromRadio.packet.decoded.payload)
            .toBuilder()
            .apply {
                if (hasEnvironmentMetrics()) {
                    // Handle float metrics that default to 0.0f when not explicitly set or when 0.0f means no
                    // data
                    if (!environmentMetrics.hasTemperature()) {
                        environmentMetrics = environmentMetrics.toBuilder().setTemperature(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasRelativeHumidity()) {
                        environmentMetrics =
                            environmentMetrics.toBuilder().setRelativeHumidity(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasSoilTemperature()) {
                        environmentMetrics =
                            environmentMetrics.toBuilder().setSoilTemperature(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasBarometricPressure()) {
                        environmentMetrics =
                            environmentMetrics.toBuilder().setBarometricPressure(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasGasResistance()) {
                        environmentMetrics = environmentMetrics.toBuilder().setGasResistance(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasVoltage()) {
                        environmentMetrics = environmentMetrics.toBuilder().setVoltage(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasCurrent()) {
                        environmentMetrics = environmentMetrics.toBuilder().setCurrent(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasLux()) {
                        environmentMetrics = environmentMetrics.toBuilder().setLux(Float.NaN).build()
                    }
                    if (!environmentMetrics.hasUvLux()) {
                        environmentMetrics = environmentMetrics.toBuilder().setUvLux(Float.NaN).build()
                    }

                    // Handle uint32 metrics that default to 0 when not explicitly set or when 0 means no data
                    if (!environmentMetrics.hasIaq()) {
                        environmentMetrics = environmentMetrics.toBuilder().setIaq(Int.MIN_VALUE).build()
                    }
                    if (!environmentMetrics.hasSoilMoisture()) {
                        environmentMetrics =
                            environmentMetrics.toBuilder().setSoilMoisture(Int.MIN_VALUE).build()
                    }
                }
                // Leaving in case we have need of nulling any in device metrics.
                //                if (hasDeviceMetrics()) {
                //                    deviceMetrics =
                // deviceMetrics.toBuilder().setBatteryLevel(Int.MIN_VALUE).build()
                //                }
            }
            .setTime((log.received_date / MILLIS_TO_SECONDS).toInt())
            .build()
    }
        .getOrNull()

    fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> = meshLogDao
        .getLogsFrom(nodeNum, Portnums.PortNum.TELEMETRY_APP_VALUE, MAX_MESH_PACKETS)
        .distinctUntilChanged()
        .mapLatest { list -> list.mapNotNull(::parseTelemetryLog) }
        .flowOn(dispatchers.io)

    fun getLogsFrom(
        nodeNum: Int,
        portNum: Int = Portnums.PortNum.UNKNOWN_APP_VALUE,
        maxItem: Int = MAX_MESH_PACKETS,
    ): Flow<List<MeshLog>> =
        meshLogDao.getLogsFrom(nodeNum, portNum, maxItem).distinctUntilChanged().flowOn(dispatchers.io)

    /*
     * Retrieves MeshPackets matching 'nodeNum' and 'portNum'.
     * If 'portNum' is not specified, returns all MeshPackets. Otherwise, filters by 'portNum'.
     */
    fun getMeshPacketsFrom(nodeNum: Int, portNum: Int = Portnums.PortNum.UNKNOWN_APP_VALUE): Flow<List<MeshPacket>> =
        getLogsFrom(nodeNum, portNum).mapLatest { list -> list.map { it.fromRadio.packet } }.flowOn(dispatchers.io)

    fun getMyNodeInfo(): Flow<MeshProtos.MyNodeInfo?> = getLogsFrom(0, 0)
        .mapLatest { list -> list.firstOrNull { it.myNodeInfo != null }?.myNodeInfo }
        .flowOn(dispatchers.io)

    suspend fun insert(log: MeshLog) = withContext(dispatchers.io) { meshLogDao.insert(log) }

    suspend fun deleteAll() = withContext(dispatchers.io) { meshLogDao.deleteAll() }

    suspend fun deleteLog(uuid: String) = withContext(dispatchers.io) { meshLogDao.deleteLog(uuid) }

    suspend fun deleteLogs(nodeNum: Int, portNum: Int) =
        withContext(dispatchers.io) { meshLogDao.deleteLogs(nodeNum, portNum) }

    companion object {
        private const val MAX_ITEMS = 500
        private const val MAX_MESH_PACKETS = 10000
        private const val MILLIS_TO_SECONDS = 1000
    }
}
