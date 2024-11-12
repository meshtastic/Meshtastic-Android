package com.geeksville.mesh.database

import com.geeksville.mesh.Portnums
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.entity.MeshLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MeshLogRepository @Inject constructor(private val meshLogDaoLazy: dagger.Lazy<MeshLogDao>) {
    private val meshLogDao by lazy {
        meshLogDaoLazy.get()
    }

    suspend fun getAllLogs(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> = withContext(Dispatchers.IO) {
        meshLogDao.getAllLogs(maxItems)
    }

    suspend fun getAllLogsInReceiveOrder(maxItems: Int = MAX_ITEMS): Flow<List<MeshLog>> = withContext(Dispatchers.IO) {
        meshLogDao.getAllLogsInReceiveOrder(maxItems)
    }

    private fun parseTelemetryLog(log: MeshLog): Telemetry? = runCatching {
        Telemetry.parseFrom(log.fromRadio.packet.decoded.payload)
            .toBuilder().setTime((log.received_date / MILLIS_TO_SECONDS).toInt()).build()
    }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> =
        meshLogDao.getLogsFrom(nodeNum, Portnums.PortNum.TELEMETRY_APP_VALUE, MAX_MESH_PACKETS)
            .distinctUntilChanged()
            .mapLatest { list -> list.mapNotNull(::parseTelemetryLog) }
            .flowOn(Dispatchers.IO)

    fun getLogsFrom(
        nodeNum: Int,
        portNum: Int = Portnums.PortNum.UNKNOWN_APP_VALUE,
        maxItem: Int = MAX_MESH_PACKETS,
    ): Flow<List<MeshLog>> = meshLogDao.getLogsFrom(nodeNum, portNum, maxItem)
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

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
        .flowOn(Dispatchers.IO)

    suspend fun insert(log: MeshLog) = withContext(Dispatchers.IO) {
        meshLogDao.insert(log)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        meshLogDao.deleteAll()
    }

    suspend fun deleteLog(uuid: String) = withContext(Dispatchers.IO) {
        meshLogDao.deleteLog(uuid)
    }

    suspend fun deleteLogs(nodeNum: Int, portNum: Int) = withContext(Dispatchers.IO) {
        meshLogDao.deleteLogs(nodeNum, portNum)
    }

    companion object {
        private const val MAX_ITEMS = 500
        private const val MAX_MESH_PACKETS = 10000
        private const val MILLIS_TO_SECONDS = 1000
    }
}
