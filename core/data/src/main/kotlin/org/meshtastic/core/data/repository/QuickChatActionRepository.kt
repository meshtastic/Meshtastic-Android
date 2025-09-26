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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.dao.QuickChatActionDao
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.di.annotation.IoDispatcher
import javax.inject.Inject

class QuickChatActionRepository
@Inject
constructor(
    private val quickChatDaoLazy: Lazy<QuickChatActionDao>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val quickChatActionDao by lazy { quickChatDaoLazy.get() }

    fun getAllActions() = quickChatActionDao.getAll().flowOn(ioDispatcher)

    suspend fun upsert(action: QuickChatAction) = withContext(ioDispatcher) { quickChatActionDao.upsert(action) }

    suspend fun deleteAll() = withContext(ioDispatcher) { quickChatActionDao.deleteAll() }

    suspend fun delete(action: QuickChatAction) = withContext(ioDispatcher) { quickChatActionDao.delete(action) }

    suspend fun setItemPosition(uuid: Long, newPos: Int) =
        withContext(ioDispatcher) { quickChatActionDao.updateActionPosition(uuid, newPos) }
}
