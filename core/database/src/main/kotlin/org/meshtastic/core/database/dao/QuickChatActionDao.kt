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

package org.meshtastic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.QuickChatAction

@Dao
interface QuickChatActionDao {

    @Query("Select * from quick_chat order by position asc")
    fun getAll(): Flow<List<QuickChatAction>>

    @Upsert suspend fun upsert(action: QuickChatAction)

    @Query("Delete from quick_chat")
    suspend fun deleteAll()

    @Query("Delete from quick_chat where uuid=:uuid")
    suspend fun delete(uuid: Long)

    @Transaction
    suspend fun delete(action: QuickChatAction) {
        delete(action.uuid)
        decrementPositionsAfter(action.position)
    }

    @Query("Update quick_chat set position=:position WHERE uuid=:uuid")
    suspend fun updateActionPosition(uuid: Long, position: Int)

    @Query("Update quick_chat set position=position-1 where position>=:position")
    suspend fun decrementPositionsAfter(position: Int)
}
