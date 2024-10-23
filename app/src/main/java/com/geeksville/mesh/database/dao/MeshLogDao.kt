package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.geeksville.mesh.database.entity.MeshLog
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshLogDao {

    @Query("SELECT * FROM log ORDER BY received_date DESC LIMIT 0,:maxItem")
    fun getAllLogs(maxItem: Int): Flow<List<MeshLog>>

    @Query("SELECT * FROM log ORDER BY received_date ASC LIMIT 0,:maxItem")
    fun getAllLogsInReceiveOrder(maxItem: Int): Flow<List<MeshLog>>

    /*
     * Retrieves MeshPackets matching 'from_num' (nodeNum) and 'port_num' (PortNum).
     * If 'portNum' is 0, returns all MeshPackets. Otherwise, filters by 'port_num'.
     */
    @Query(
        """
        SELECT * FROM log 
        WHERE from_num = :fromNum AND (:portNum = 0 AND port_num != 0 OR port_num = :portNum)
        ORDER BY received_date DESC LIMIT 0,:maxItem
        """
    )
    fun getLogsFrom(fromNum: Int, portNum: Int, maxItem: Int): Flow<List<MeshLog>>

    @Insert
    fun insert(log: MeshLog)

    @Query("DELETE FROM log")
    fun deleteAll()
}
