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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseConstants.SQLITE_MAX_BIND_PARAMETERS
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.repository.DiscoveryRepository

/**
 * Backed by the Koin-provided [DiscoveryDao], which resolves the active database on every call, so readers follow a
 * device switch and writes register with the cross-transport merge barrier.
 */
@Single
class DiscoveryRepositoryImpl(private val discoveryDao: DiscoveryDao) : DiscoveryRepository {
    override fun getAllSessions(): Flow<List<DiscoverySessionEntity>> = discoveryDao.getAllSessions()

    override fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?> = discoveryDao.getSessionFlow(sessionId)

    override suspend fun getSession(sessionId: Long): DiscoverySessionEntity? = discoveryDao.getSession(sessionId)

    override fun getPresetResultsFlow(sessionId: Long): Flow<List<DiscoveryPresetResultEntity>> =
        discoveryDao.getPresetResultsFlow(sessionId)

    override suspend fun getPresetResults(sessionId: Long): List<DiscoveryPresetResultEntity> =
        discoveryDao.getPresetResults(sessionId)

    override suspend fun getNodesByPresetResult(presetResultIds: List<Long>): Map<Long, List<DiscoveredNodeEntity>> {
        val nodesByPresetResult =
            presetResultIds
                .chunked(SQLITE_MAX_BIND_PARAMETERS)
                .flatMap { discoveryDao.getDiscoveredNodesForPresetResults(it) }
                .groupBy { it.presetResultId }
        return presetResultIds.associateWith { nodesByPresetResult[it].orEmpty() }
    }

    override suspend fun updateSession(session: DiscoverySessionEntity) {
        discoveryDao.updateSession(session)
    }

    override suspend fun updatePresetResult(result: DiscoveryPresetResultEntity) {
        discoveryDao.updatePresetResult(result)
    }

    override suspend fun deleteSession(sessionId: Long) {
        discoveryDao.deleteSession(sessionId)
    }

    override suspend fun markInterruptedSessions() {
        discoveryDao.markInterruptedSessions()
    }
}
