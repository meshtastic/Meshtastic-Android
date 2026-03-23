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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry

class FakeMeshLogRepository : MeshLogRepository {
    private val _logs = MutableStateFlow<List<MeshLog>>(emptyList())
    val currentLogs: List<MeshLog> get() = _logs.value
    
    var deleteLogsOlderThanCalledDays: Int? = null

    override fun getAllLogs(maxItem: Int): Flow<List<MeshLog>> = _logs.map { it.take(maxItem) }

    override fun getAllLogsInReceiveOrder(maxItem: Int): Flow<List<MeshLog>> = _logs.map { it.take(maxItem) }

    override fun getAllLogsUnbounded(): Flow<List<MeshLog>> = _logs

    override fun getLogsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshLog>> = _logs.map {
        it.filter { log -> log.fromNum == nodeNum && log.portNum == portNum }
    }

    override fun getMeshPacketsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshPacket>> = MutableStateFlow(emptyList())

    override fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> = MutableStateFlow(emptyList())

    override fun getRequestLogs(targetNodeNum: Int, portNum: PortNum): Flow<List<MeshLog>> = MutableStateFlow(emptyList())

    override fun getMyNodeInfo(): Flow<MyNodeInfo?> = MutableStateFlow(null)

    override suspend fun insert(log: MeshLog) {
        _logs.value = _logs.value + log
    }

    override suspend fun deleteAll() {
        _logs.value = emptyList()
    }

    override suspend fun deleteLog(uuid: String) {
        _logs.value = _logs.value.filter { it.uuid != uuid }
    }

    override suspend fun deleteLogs(nodeNum: Int, portNum: Int) {
        _logs.value = _logs.value.filterNot { it.fromNum == nodeNum && it.portNum == portNum }
    }

    override suspend fun deleteLogsOlderThan(retentionDays: Int) {
        deleteLogsOlderThanCalledDays = retentionDays
    }

    fun setLogs(logs: List<MeshLog>) {
        _logs.value = logs
    }
}
