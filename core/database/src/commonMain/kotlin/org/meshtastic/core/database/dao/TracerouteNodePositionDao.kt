/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.TracerouteNodePositionEntity

@Dao
interface TracerouteNodePositionDao {

    @Query("SELECT * FROM traceroute_node_position WHERE log_uuid = :logUuid")
    fun getByLogUuid(logUuid: String): Flow<List<TracerouteNodePositionEntity>>

    @Query("DELETE FROM traceroute_node_position WHERE log_uuid = :logUuid")
    suspend fun deleteByLogUuid(logUuid: String)

    @Upsert suspend fun insertAll(entities: List<TracerouteNodePositionEntity>)
}
