package com.geeksville.mesh.database

import com.geeksville.mesh.database.dao.QuickChatActionDao
import com.geeksville.mesh.database.entity.QuickChatAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class QuickChatActionRepository @Inject constructor(private val quickChatDaoLazy: dagger.Lazy<QuickChatActionDao>) {
    private val quickChatActionDao by lazy {
        quickChatDaoLazy.get()
    }

    suspend fun getAllActions(): Flow<List<QuickChatAction>> = withContext(Dispatchers.IO) {
        quickChatActionDao.getAll()
    }

    suspend fun insert(action: QuickChatAction) = withContext(Dispatchers.IO) {
        quickChatActionDao.insert(action)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        quickChatActionDao.deleteAll()
    }

    suspend fun delete(uuid: Long) = withContext(Dispatchers.IO) {
        quickChatActionDao.delete(uuid)
    }

    suspend fun update(action:QuickChatAction) = withContext(Dispatchers.IO) {
        quickChatActionDao.update(action)
    }
}