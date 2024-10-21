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

    private fun parseTelemetryLog(log: MeshLog): Telemetry? =
        runCatching { Telemetry.parseFrom(log.fromRadio.packet.decoded.payload) }.getOrNull()

    private fun parseMeshPacket(log: MeshLog): MeshPacket? =
        runCatching { log.meshPacket }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> =
        meshLogDao.getLogsFrom(nodeNum, Portnums.PortNum.TELEMETRY_APP_VALUE, MAX_MESH_PACKETS)
            .distinctUntilChanged()
            .mapLatest { list -> list.mapNotNull(::parseTelemetryLog) }
            .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMeshPacketsFrom(nodeNum: Int): Flow<List<MeshPacket>> =
        meshLogDao.getLogsFrom(nodeNum, Portnums.PortNum.TELEMETRY_APP_VALUE, MAX_MESH_PACKETS)
            .distinctUntilChanged()
            .mapLatest { list -> list.mapNotNull(::parseMeshPacket) }
            .flowOn(Dispatchers.IO)

    suspend fun insert(log: MeshLog) = withContext(Dispatchers.IO) {
        meshLogDao.insert(log)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        meshLogDao.deleteAll()
    }

    companion object {
        private const val MAX_ITEMS = 500
        private const val MAX_MESH_PACKETS = 10000
    }
}
