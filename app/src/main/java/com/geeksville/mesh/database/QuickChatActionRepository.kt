package com.geeksville.mesh.database

import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.database.dao.QuickChatActionDao
import com.geeksville.mesh.database.entity.QuickChatAction
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class QuickChatActionRepository @Inject constructor(
    private val quickChatDaoLazy: dagger.Lazy<QuickChatActionDao>,
    private val dispatchers: CoroutineDispatchers,
) {
    private val quickChatActionDao by lazy {
        quickChatDaoLazy.get()
    }

    fun getAllActions() = quickChatActionDao.getAll().flowOn(dispatchers.io)

    suspend fun upsert(action: QuickChatAction) = withContext(dispatchers.io) {
        quickChatActionDao.upsert(action)
    }

    suspend fun deleteAll() = withContext(dispatchers.io) {
        quickChatActionDao.deleteAll()
    }

    suspend fun delete(action: QuickChatAction) = withContext(dispatchers.io) {
        quickChatActionDao.delete(action)
    }

    suspend fun setItemPosition(uuid: Long, newPos: Int) = withContext(dispatchers.io) {
        quickChatActionDao.updateActionPosition(uuid, newPos)
    }
}