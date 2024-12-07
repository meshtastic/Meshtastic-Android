/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeInfoDao {

    @Query("SELECT * FROM my_node")
    fun getMyNodeInfo(): Flow<MyNodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setMyNodeInfo(myInfo: MyNodeEntity)

    @Query("DELETE FROM my_node")
    fun clearMyNodeInfo()

    @Query(
        """
        SELECT * FROM nodes
        ORDER BY CASE
            WHEN num = (SELECT myNodeNum FROM my_node LIMIT 1) THEN 0
            ELSE 1
        END,
        last_heard DESC
        """
    )
    fun nodeDBbyNum(): Flow<Map<@MapColumn(columnName = "num") Int, NodeEntity>>

    @Query(
        """
    WITH OurNode AS (
        SELECT latitude, longitude
        FROM nodes
        WHERE num = (SELECT myNodeNum FROM my_node LIMIT 1)
    )
    SELECT * FROM nodes
    WHERE (:includeUnknown = 1 OR short_name IS NOT NULL)
        AND (:filter = ''
            OR (long_name LIKE '%' || :filter || '%'
            OR short_name LIKE '%' || :filter || '%'))
    ORDER BY CASE
        WHEN num = (SELECT myNodeNum FROM my_node LIMIT 1) THEN 0
        ELSE 1
    END,
    CASE
        WHEN :sort = 'last_heard' THEN last_heard * -1
        WHEN :sort = 'alpha' THEN UPPER(long_name) 
        WHEN :sort = 'distance' THEN
            CASE
                WHEN latitude IS NULL OR longitude IS NULL OR
                    (latitude = 0.0 AND longitude = 0.0) THEN 999999999
                ELSE
                    (latitude - (SELECT latitude FROM OurNode)) *
                    (latitude - (SELECT latitude FROM OurNode)) +
                    (longitude - (SELECT longitude FROM OurNode)) *
                    (longitude - (SELECT longitude FROM OurNode))
            END
        WHEN :sort = 'hops_away' THEN
            CASE
                WHEN hops_away = -1 THEN 999999999
                ELSE hops_away
            END
        WHEN :sort = 'channel' THEN channel
        WHEN :sort = 'via_mqtt' THEN via_mqtt
        ELSE 0
    END ASC,
    last_heard DESC
    """
    )
    fun getNodes(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
    ): Flow<List<NodeEntity>>

    @Upsert
    fun upsert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(nodes: List<NodeEntity>)

    @Query("DELETE FROM nodes")
    fun clearNodeInfo()

    @Query("DELETE FROM nodes WHERE num=:num")
    fun deleteNode(num: Int)
}
