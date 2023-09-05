package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.geeksville.mesh.MyNodeInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface MyNodeInfoDao {

    @Query("SELECT * FROM MyNodeInfo")
    fun getMyNodeInfo(): Flow<MyNodeInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setMyNodeInfo(myInfo: MyNodeInfo?)

    @Query("DELETE FROM MyNodeInfo")
    fun clearMyNodeInfo()
}
