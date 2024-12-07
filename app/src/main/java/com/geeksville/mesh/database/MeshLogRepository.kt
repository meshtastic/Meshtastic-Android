/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.database

import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.entity.MeshLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MeshLogRepository @Inject constructor(
    private val meshLogDaoLazy: dagger.Lazy<MeshLogDao>,
    private val dispatchers: CoroutineDispatchers,
) {
    private val meshLogDao by lazy {
        meshLogDaoLazy.get()
    }

    fun getAllLogs(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> = meshLogDao.getAllLogs(maxItems)
        .flowOn(dispatchers.io)
        .conflate()

    fun getAllLogsInReceiveOrder(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> =
        meshLogDao.getAllLogsInReceiveOrder(maxItems)
            .flowOn(dispatchers.io)
            .conflate()

    private fun parseTelemetryLog(log: MeshLog): Telemetry? = runCatching {
        Telemetry.parseFrom(log.fromRadio.packet.decoded.payload)
            .toBuilder().setTime((log.received_date / MILLIS_TO_SECONDS).toInt()).build()
    }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> =
        meshLogDao.getLogsFrom(nodeNum, Portnums.PortNum.TELEMETRY_APP_VALUE, MAX_MESH_PACKETS)
            .distinctUntilChanged()
            .mapLatest { list -> list.mapNotNull(::parseTelemetryLog) }
            .flowOn(dispatchers.io)

    fun getLogsFrom(
        nodeNum: Int,
        portNum: Int = Portnums.PortNum.UNKNOWN_APP_VALUE,
        maxItem: Int = MAX_MESH_PACKETS,
    ): Flow<List<MeshLog>> = meshLogDao.getLogsFrom(nodeNum, portNum, maxItem)
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    /*
     * Retrieves MeshPackets matching 'nodeNum' and 'portNum'.
     * If 'portNum' is not specified, returns all MeshPackets. Otherwise, filters by 'portNum'.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMeshPacketsFrom(
        nodeNum: Int,
        portNum: Int = Portnums.PortNum.UNKNOWN_APP_VALUE,
    ): Flow<List<MeshPacket>> = getLogsFrom(nodeNum, portNum)
        .mapLatest { list -> list.map { it.fromRadio.packet } }
        .flowOn(dispatchers.io)

    suspend fun insert(log: MeshLog) = withContext(dispatchers.io) {
        meshLogDao.insert(log)
    }

    suspend fun deleteAll() = withContext(dispatchers.io) {
        meshLogDao.deleteAll()
    }

    suspend fun deleteLog(uuid: String) = withContext(dispatchers.io) {
        meshLogDao.deleteLog(uuid)
    }

    suspend fun deleteLogs(nodeNum: Int, portNum: Int) = withContext(dispatchers.io) {
        meshLogDao.deleteLogs(nodeNum, portNum)
    }

    companion object {
        private const val MAX_ITEMS = 500
        private const val MAX_MESH_PACKETS = 10000
        private const val MILLIS_TO_SECONDS = 1000
    }
}
