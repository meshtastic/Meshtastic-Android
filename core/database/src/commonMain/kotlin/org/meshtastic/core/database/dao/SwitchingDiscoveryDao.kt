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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity

/**
 * A switch-aware [DiscoveryDao] that resolves the active database on every call instead of pinning the one that was
 * current at injection time. This is what Koin hands to `feature:discovery` consumers (ViewModels and the scan engine),
 * which hold their DAO for their whole lifetime:
 * - Flow methods re-latch via `currentDb.flatMapLatest`, so an open discovery screen follows a device/DB switch instead
 *   of watching the old database forever.
 * - Suspend methods go through [DatabaseProvider.withDb], so writes register with the cross-transport merge drain
 *   barrier (a mid-scan merge can't snapshot-then-retire the DB underneath an in-flight session write) and every call
 *   picks up withDb's closed-pool retry — a pinned DAO used to throw unrecoverably once its DB was retired.
 *
 * `withDb` only returns null when no database is open, which [DatabaseProvider] guarantees can't happen (the default DB
 * is the floor), so the non-null coercions below are structural, not behavioral.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class SwitchingDiscoveryDao(private val dbManager: DatabaseProvider) : DiscoveryDao {

    // region Session operations

    override suspend fun insertSession(session: DiscoverySessionEntity): Long =
        checkNotNull(dbManager.withDb { it.discoveryDao().insertSession(session) })

    override suspend fun updateSession(session: DiscoverySessionEntity) {
        dbManager.withDb { it.discoveryDao().updateSession(session) }
    }

    override fun getAllSessions(): Flow<List<DiscoverySessionEntity>> =
        dbManager.currentDb.flatMapLatest { it.discoveryDao().getAllSessions() }

    override suspend fun getAllSessionsSnapshot(): List<DiscoverySessionEntity> =
        dbManager.withDb { it.discoveryDao().getAllSessionsSnapshot() }.orEmpty()

    override suspend fun getSession(sessionId: Long): DiscoverySessionEntity? =
        dbManager.withDb { it.discoveryDao().getSession(sessionId) }

    override fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?> =
        dbManager.currentDb.flatMapLatest { it.discoveryDao().getSessionFlow(sessionId) }

    override suspend fun deleteSession(sessionId: Long) {
        dbManager.withDb { it.discoveryDao().deleteSession(sessionId) }
    }

    override suspend fun markInterruptedSessions() {
        dbManager.withDb { it.discoveryDao().markInterruptedSessions() }
    }

    override suspend fun getInterruptedSession(deviceAddress: String): DiscoverySessionEntity? =
        dbManager.withDb { it.discoveryDao().getInterruptedSession(deviceAddress) }

    // endregion

    // region Preset result operations

    override suspend fun insertPresetResult(result: DiscoveryPresetResultEntity): Long =
        checkNotNull(dbManager.withDb { it.discoveryDao().insertPresetResult(result) })

    override suspend fun updatePresetResult(result: DiscoveryPresetResultEntity) {
        dbManager.withDb { it.discoveryDao().updatePresetResult(result) }
    }

    override suspend fun getPresetResults(sessionId: Long): List<DiscoveryPresetResultEntity> =
        dbManager.withDb { it.discoveryDao().getPresetResults(sessionId) }.orEmpty()

    override fun getPresetResultsFlow(sessionId: Long): Flow<List<DiscoveryPresetResultEntity>> =
        dbManager.currentDb.flatMapLatest { it.discoveryDao().getPresetResultsFlow(sessionId) }

    // endregion

    // region Discovered node operations

    override suspend fun insertDiscoveredNode(node: DiscoveredNodeEntity): Long =
        checkNotNull(dbManager.withDb { it.discoveryDao().insertDiscoveredNode(node) })

    override suspend fun insertDiscoveredNodes(nodes: List<DiscoveredNodeEntity>) {
        dbManager.withDb { it.discoveryDao().insertDiscoveredNodes(nodes) }
    }

    override suspend fun updateDiscoveredNode(node: DiscoveredNodeEntity) {
        dbManager.withDb { it.discoveryDao().updateDiscoveredNode(node) }
    }

    override suspend fun getDiscoveredNodes(presetResultId: Long): List<DiscoveredNodeEntity> =
        dbManager.withDb { it.discoveryDao().getDiscoveredNodes(presetResultId) }.orEmpty()

    override fun getDiscoveredNodesFlow(presetResultId: Long): Flow<List<DiscoveredNodeEntity>> =
        dbManager.currentDb.flatMapLatest { it.discoveryDao().getDiscoveredNodesFlow(presetResultId) }

    override suspend fun getUniqueNodeNums(sessionId: Long): List<Long> =
        dbManager.withDb { it.discoveryDao().getUniqueNodeNums(sessionId) }.orEmpty()

    // endregion

    // region Aggregate queries

    override suspend fun getUniqueNodeCount(sessionId: Long): Int =
        dbManager.withDb { it.discoveryDao().getUniqueNodeCount(sessionId) } ?: 0

    override suspend fun getMaxDistance(sessionId: Long): Double? =
        dbManager.withDb { it.discoveryDao().getMaxDistance(sessionId) }

    override suspend fun getSessionWithResults(sessionId: Long): DiscoverySessionEntity? =
        dbManager.withDb { it.discoveryDao().getSessionWithResults(sessionId) }

    // endregion
}
