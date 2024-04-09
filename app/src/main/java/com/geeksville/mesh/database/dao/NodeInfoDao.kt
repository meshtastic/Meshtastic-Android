package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeInfoDao {

    @Query("SELECT * FROM MyNodeInfo")
    fun getMyNodeInfo(): Flow<MyNodeInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setMyNodeInfo(myInfo: MyNodeInfo)

    @Query("DELETE FROM MyNodeInfo")
    fun clearMyNodeInfo()

    @Query("SELECT * FROM NodeInfo")
    fun getNodes(): Flow<List<NodeInfo>>

    @Query("SELECT * FROM NodeInfo ORDER BY CASE WHEN num = (SELECT myNodeNum FROM MyNodeInfo LIMIT 1) THEN 0 ELSE 1 END, lastHeard DESC")
    fun nodeDBbyNum(): Flow<Map<@MapColumn(columnName = "num") Int, NodeInfo>>

    @Query("SELECT * FROM NodeInfo")
    fun nodeDBbyID(): Flow<Map<@MapColumn(columnName = "user_id") String, NodeInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(node: NodeInfo)

    @Upsert
    fun upsert(node: NodeInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(nodes: List<NodeInfo>)

    @Query("DELETE FROM NodeInfo")
    fun clearNodeInfo()

    @Query("DELETE FROM NodeInfo WHERE num=:num")
    fun delNode(num: Int)

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
