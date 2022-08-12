package com.geeksville.mesh.database.dao

import androidx.room.*
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

    @Query("Update quick_chat set position=position+1 where position>=:startInclusive and position<:endExclusive")
    fun incrementPositionsBetween(startInclusive: Int, endExclusive: Int)

    @Query("Update quick_chat SET position=position-1 where position>:startExclusive and position<=:endInclusive")
    fun decrementPositionsBetween(startExclusive: Int, endInclusive: Int)

    @Query("Update quick_chat set position=position-1 where position>=:position")
    fun decrementPositionsAfter(position: Int)

    @Transaction
    fun moveAction(action: QuickChatAction, newPos: Int) {
        // FIXME: Check newPos is valid
        if (newPos < action.position) {
            incrementPositionsBetween(newPos, action.position)
            updateActionPosition(action.uuid, newPos)
        } else if (newPos > action.position) {
            decrementPositionsBetween(action.position, newPos)
            updateActionPosition(action.uuid, newPos)
        } else {
            // Do nothing: moving to same position
        }
    }

}