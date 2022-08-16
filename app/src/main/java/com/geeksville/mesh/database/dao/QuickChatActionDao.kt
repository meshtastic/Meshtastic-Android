package com.geeksville.mesh.database.dao

import androidx.room.*
import com.geeksville.mesh.database.entity.QuickChatAction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickChatActionDao {

    @Query("Select * from quick_chat order by position asc")
    fun getAll(): Flow<List<QuickChatAction>>

    @Insert
    fun insert(action: QuickChatAction)

    @Query("Delete from quick_chat")
    fun deleteAll()

    @Query("Delete from quick_chat where uuid=:uuid")
    fun _delete(uuid: Long)

    @Transaction
    fun delete(action: QuickChatAction) {
        _delete(action.uuid)
        decrementPositionsAfter(action.position)
    }

    @Update
    fun update(action: QuickChatAction)

    @Query("Update quick_chat set position=:position WHERE uuid=:uuid")
    fun updateActionPosition(uuid: Long, position: Int)

    @Query("Update quick_chat set position=position-1 where position>=:position")
    fun decrementPositionsAfter(position: Int)

}