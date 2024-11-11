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

    /**
     * Retrieves [MeshLog]s matching 'from_num' (nodeNum) and 'port_num' (PortNum).
     *
     * @param portNum If 0, returns all MeshPackets. Otherwise, filters by 'port_num'.
     * @param timeFrame oldest limit in milliseconds of [MeshLog]s we want to retrieve.
     */
    @Query(
        """
        SELECT * FROM log 
        WHERE from_num = :fromNum AND (:portNum = 0 AND port_num != 0 OR port_num = :portNum) AND received_date > :timeFrame
        ORDER BY received_date DESC LIMIT 0,:maxItem
        """
    )
    fun getLogsFrom(fromNum: Int, portNum: Int, maxItem: Int, timeFrame: Long = 0L): Flow<List<MeshLog>>

    @Insert
    fun insert(log: MeshLog)

    @Query("DELETE FROM log")
    fun deleteAll()

    @Query("DELETE FROM log WHERE uuid = :uuid")
    fun deleteLog(uuid: String)

    @Query("DELETE FROM log WHERE from_num = :fromNum AND port_num = :portNum")
    fun deleteLogs(fromNum: Int, portNum: Int)
}
