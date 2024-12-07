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