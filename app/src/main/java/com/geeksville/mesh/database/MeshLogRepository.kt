package com.geeksville.mesh.database

import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.entity.MeshLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    suspend fun insert(log: MeshLog) = withContext(Dispatchers.IO) {
        meshLogDao.insert(log)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        meshLogDao.deleteAll()
    }

    companion object {
        private const val MAX_ITEMS = 500
    }
}