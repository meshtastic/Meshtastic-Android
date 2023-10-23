package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.geeksville.mesh.database.entity.MeshLog
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshLogDao {

    @Query("Select * from log order by received_date desc limit 0,:maxItem")
    fun getAllLogs(maxItem: Int): Flow<List<MeshLog>>

    @Query("Select * from log order by received_date asc limit 0,:maxItem")
    fun getAllLogsInReceiveOrder(maxItem: Int): Flow<List<MeshLog>>

    @Insert
    fun insert(log: MeshLog)

    @Query("DELETE from log")
    fun deleteAll()

}