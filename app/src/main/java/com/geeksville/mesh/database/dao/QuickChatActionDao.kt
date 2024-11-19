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
