package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.geeksville.mesh.database.entity.QuickChatAction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickChatActionDao {

    @Query("Select * from quick_chat")
    fun getAll(): Flow<List<QuickChatAction>>

    @Insert
    fun insert(action: QuickChatAction)

    @Query("Delete from quick_chat")
    fun deleteAll()

    @Query("Delete from quick_chat where uuid=:uuid")
    fun delete(uuid: Long)

    @Update
    fun update(action: QuickChatAction)

}