package com.geeksville.mesh.database

import com.geeksville.mesh.Portnums
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMeshPacketsFrom(nodeNum: Int) = meshLogDao.getAllLogs(MAX_MESH_PACKETS)
        .mapLatest { list -> list.mapNotNull { it.meshPacket }.filter { it.from == nodeNum } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTelemetryFrom(nodeNum: Int) = getMeshPacketsFrom(nodeNum).mapLatest { list ->
        list.filter { it.hasDecoded() && it.decoded.portnum == Portnums.PortNum.TELEMETRY_APP }
            .mapNotNull { runCatching { Telemetry.parseFrom(it.decoded.payload) }.getOrNull() }
    }.flowOn(Dispatchers.IO)

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
