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

    @Query("DELETE FROM log WHERE uuid = :uuid")
    fun deleteLog(uuid: String)

    @Query("DELETE FROM log WHERE from_num = :fromNum AND port_num = :portNum")
    fun deleteLogs(fromNum: Int, portNum: Int)
}
