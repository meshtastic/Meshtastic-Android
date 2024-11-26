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

package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geeksville.mesh.database.entity.QuickChatAction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickChatActionDao {

    @Query("Select * from quick_chat order by position asc")
    fun getAll(): Flow<List<QuickChatAction>>

    @Upsert
    fun upsert(action: QuickChatAction)

    @Query("Delete from quick_chat")
    fun deleteAll()

    @Query("Delete from quick_chat where uuid=:uuid")
    fun _delete(uuid: Long)

    @Transaction
    fun delete(action: QuickChatAction) {
        _delete(action.uuid)
        decrementPositionsAfter(action.position)
    }

    @Query("Update quick_chat set position=:position WHERE uuid=:uuid")
    fun updateActionPosition(uuid: Long, position: Int)

    @Query("Update quick_chat set position=position-1 where position>=:position")
    fun decrementPositionsAfter(position: Int)
}
