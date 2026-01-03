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

import kotlinx.coroutines.flow.flatMapLatest
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.QuickChatAction
import javax.inject.Inject

class QuickChatActionRepository
@Inject
constructor(
    private val dbManager: DatabaseManager,
) {
    fun getAllActions() = dbManager.currentDb.flatMapLatest { it.quickChatActionDao().getAll() }

    suspend fun upsert(action: QuickChatAction) =
        dbManager.currentDb.value.quickChatActionDao().upsert(action)

    suspend fun deleteAll() = dbManager.currentDb.value.quickChatActionDao().deleteAll()

    suspend fun delete(action: QuickChatAction) =
        dbManager.currentDb.value.quickChatActionDao().delete(action)

    suspend fun setItemPosition(uuid: Long, newPos: Int) {
        dbManager.currentDb.value.quickChatActionDao().updateActionPosition(uuid, newPos)
    }
}
