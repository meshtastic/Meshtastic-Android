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
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity

@Dao
@Suppress("TooManyFunctions")
interface DiscoveryDao {

    // region Session operations

    @Insert suspend fun insertSession(session: DiscoverySessionEntity): Long

    @Update suspend fun updateSession(session: DiscoverySessionEntity)

    @Query("SELECT * FROM discovery_session ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<DiscoverySessionEntity>>

    /** Snapshot used by DatabaseMerger to append a transport's discovery sessions to the unified DB. */
    @Query("SELECT * FROM discovery_session")
    suspend fun getAllSessionsSnapshot(): List<DiscoverySessionEntity>

    @Query("SELECT * FROM discovery_session WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): DiscoverySessionEntity?

    @Query("SELECT * FROM discovery_session WHERE id = :sessionId")
    fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?>

    @Query("DELETE FROM discovery_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("UPDATE discovery_session SET completion_status = 'interrupted' WHERE completion_status = 'in_progress'")
    suspend fun markInterruptedSessions()

    // endregion

    // region Preset result operations

    @Insert suspend fun insertPresetResult(result: DiscoveryPresetResultEntity): Long

    @Update suspend fun updatePresetResult(result: DiscoveryPresetResultEntity)

    @Query("SELECT * FROM discovery_preset_result WHERE session_id = :sessionId")
    suspend fun getPresetResults(sessionId: Long): List<DiscoveryPresetResultEntity>

    @Query("SELECT * FROM discovery_preset_result WHERE session_id = :sessionId")
    fun getPresetResultsFlow(sessionId: Long): Flow<List<DiscoveryPresetResultEntity>>

    // endregion

    // region Discovered node operations

    @Insert suspend fun insertDiscoveredNode(node: DiscoveredNodeEntity): Long

    @Insert suspend fun insertDiscoveredNodes(nodes: List<DiscoveredNodeEntity>)

    @Update suspend fun updateDiscoveredNode(node: DiscoveredNodeEntity)

    @Query("SELECT * FROM discovered_node WHERE preset_result_id = :presetResultId")
    suspend fun getDiscoveredNodes(presetResultId: Long): List<DiscoveredNodeEntity>

    @Query("SELECT * FROM discovered_node WHERE preset_result_id = :presetResultId")
    fun getDiscoveredNodesFlow(presetResultId: Long): Flow<List<DiscoveredNodeEntity>>

    @Query(
        """
        SELECT DISTINCT node_num FROM discovered_node dn
        INNER JOIN discovery_preset_result dpr ON dn.preset_result_id = dpr.id
        WHERE dpr.session_id = :sessionId
        """,
    )
    suspend fun getUniqueNodeNums(sessionId: Long): List<Long>

    // endregion

    // region Aggregate queries

    @Query(
        """
        SELECT COUNT(DISTINCT node_num) FROM discovered_node dn
        INNER JOIN discovery_preset_result dpr ON dn.preset_result_id = dpr.id
        WHERE dpr.session_id = :sessionId
        """,
    )
    suspend fun getUniqueNodeCount(sessionId: Long): Int

    @Query(
        """
        SELECT MAX(distance_from_user) FROM discovered_node dn
        INNER JOIN discovery_preset_result dpr ON dn.preset_result_id = dpr.id
        WHERE dpr.session_id = :sessionId
        """,
    )
    suspend fun getMaxDistance(sessionId: Long): Double?

    @Transaction
    @Query("SELECT * FROM discovery_session WHERE id = :sessionId")
    suspend fun getSessionWithResults(sessionId: Long): DiscoverySessionEntity?

    // endregion
}
