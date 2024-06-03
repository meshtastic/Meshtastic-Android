package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.geeksville.mesh.MeshProtos
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

    @Query("SELECT * FROM NodeInfo ORDER BY CASE WHEN num = (SELECT myNodeNum FROM MyNodeInfo LIMIT 1) THEN 0 ELSE 1 END, lastHeard DESC")
    fun nodeDBbyNum(): Flow<Map<@MapColumn(columnName = "num") Int, NodeInfo>>

    @Query("SELECT * FROM NodeInfo")
    fun nodeDBbyID(): Flow<Map<@MapColumn(columnName = "user_id") String, NodeInfo>>

    @Query(
        """
    WITH OurNode AS (
        SELECT position_latitude, position_longitude
        FROM NodeInfo
        WHERE num = (SELECT myNodeNum FROM MyNodeInfo LIMIT 1)
    )
    SELECT * FROM NodeInfo
    WHERE (:includeUnknown = 1 OR user_hwModel != :unknownHwModel)
        AND (:filter = ''
            OR (user_longName LIKE '%' || :filter || '%'
            OR user_shortName LIKE '%' || :filter || '%'))
    ORDER BY CASE
        WHEN num = (SELECT myNodeNum FROM MyNodeInfo LIMIT 1) THEN 0
        ELSE 1
    END,
    CASE
        WHEN :sort = 'last_heard' THEN lastHeard * -1
        WHEN :sort = 'alpha' THEN UPPER(user_longName) 
        WHEN :sort = 'distance' THEN
            CASE
                WHEN position_latitude IS NULL OR position_longitude IS NULL OR
                    (position_latitude = 0 AND position_longitude = 0) THEN 999999999
                ELSE
                    (position_latitude - (SELECT position_latitude FROM OurNode)) *
                    (position_latitude - (SELECT position_latitude FROM OurNode)) +
                    (position_longitude - (SELECT position_longitude FROM OurNode)) *
                    (position_longitude - (SELECT position_longitude FROM OurNode))
            END
        WHEN :sort = 'hops_away' THEN hopsAway
        WHEN :sort = 'channel' THEN channel
        WHEN :sort = 'via_mqtt' THEN user_longName LIKE '%(MQTT)' -- viaMqtt
        ELSE 0
    END ASC,
    lastHeard DESC
    """
    )
    fun getNodes(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
        unknownHwModel: MeshProtos.HardwareModel
    ): Flow<List<NodeInfo>>

    @Upsert
    fun upsert(node: NodeInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(nodes: List<NodeInfo>)

    @Query("DELETE FROM NodeInfo")
    fun clearNodeInfo()

    @Query("DELETE FROM NodeInfo WHERE num=:num")
    fun deleteNode(num: Int)
}
