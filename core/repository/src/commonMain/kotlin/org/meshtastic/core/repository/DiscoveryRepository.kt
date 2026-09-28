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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity

/** Stored Local Mesh Discovery sessions, their per-preset results, and the nodes heard under each preset. */
interface DiscoveryRepository {
    /** Every session, newest first. */
    fun getAllSessions(): Flow<List<DiscoverySessionEntity>>

    fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?>

    suspend fun getSession(sessionId: Long): DiscoverySessionEntity?

    fun getPresetResultsFlow(sessionId: Long): Flow<List<DiscoveryPresetResultEntity>>

    suspend fun getPresetResults(sessionId: Long): List<DiscoveryPresetResultEntity>

    /** The nodes discovered under each of [presetResultIds], keyed by preset result id in the order given. */
    suspend fun getNodesByPresetResult(presetResultIds: List<Long>): Map<Long, List<DiscoveredNodeEntity>>

    suspend fun updateSession(session: DiscoverySessionEntity)

    suspend fun updatePresetResult(result: DiscoveryPresetResultEntity)

    /** Deletes the session together with its preset results and their nodes. */
    suspend fun deleteSession(sessionId: Long)

    /** Marks every session still in progress as interrupted, for scans a process death cut short. */
    suspend fun markInterruptedSessions()
}
