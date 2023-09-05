package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.geeksville.mesh.NodeInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeInfoDao {

    @Query("SELECT * FROM NodeInfo")
    fun getNodes(): Flow<List<NodeInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(node: NodeInfo)

    @Upsert
    fun upsert(node: NodeInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(nodes: List<NodeInfo>)

    @Query("DELETE FROM NodeInfo")
    fun clearNodeInfo()

    @Query("SELECT * FROM NodeInfo WHERE num=:num")
    fun getNodeInfo(num: Int): NodeInfo?

//    @Transaction
//    suspend fun updateUser(num: Int, updatedUser: MeshUser) {
//        getNodeInfo(num)?.let {
//            val updatedNodeInfo = it.copy(user = updatedUser)
//            upsert(updatedNodeInfo)
//        }
//    }

//    @Query("Update node_info set position=:position WHERE num=:num")
//    fun updatePosition(num: Int, position: MeshProtos.Position)
}
