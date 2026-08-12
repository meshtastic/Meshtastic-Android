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
package org.meshtastic.feature.discovery

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus

/** Shared in-memory [DiscoveryDao] for discovery feature tests, including live session flows. */
internal class SharedInMemoryDiscoveryDao : DiscoveryDao {
    private val stateLock = SynchronizedObject()
    private var nextSessionId = 1L
    private var nextPresetResultId = 1L
    private var nextNodeId = 1L
    private val mutableSessions = mutableMapOf<Long, DiscoverySessionEntity>()
    private val mutablePresetResults = mutableMapOf<Long, DiscoveryPresetResultEntity>()
    private val mutableDiscoveredNodes = mutableMapOf<Long, DiscoveredNodeEntity>()
    private val sessionsFlow = MutableStateFlow<List<DiscoverySessionEntity>>(emptyList())
    private val presetResultsFlow = MutableStateFlow<List<DiscoveryPresetResultEntity>>(emptyList())
    private val discoveredNodesFlow = MutableStateFlow<List<DiscoveredNodeEntity>>(emptyList())

    /** Read-only views used by feature-test assertions. */
    val sessions: Map<Long, DiscoverySessionEntity>
        get() = synchronized(stateLock) { mutableSessions.toMap() }

    val presetResults: Map<Long, DiscoveryPresetResultEntity>
        get() = synchronized(stateLock) { mutablePresetResults.toMap() }

    val discoveredNodes: Map<Long, DiscoveredNodeEntity>
        get() = synchronized(stateLock) { mutableDiscoveredNodes.toMap() }

    /** Seeds a persisted session with a stable ID for recovery tests. */
    fun seedSession(session: DiscoverySessionEntity) {
        require(session.id > 0) { "seeded discovery sessions require a persisted id" }
        synchronized(stateLock) {
            mutableSessions[session.id] = session
            nextSessionId = maxOf(nextSessionId, session.id + 1)
        }
        refreshSessionsFlow()
    }

    private fun refreshSessionsFlow() {
        sessionsFlow.value = synchronized(stateLock) { mutableSessions.values.sortedByDescending { it.timestamp } }
    }

    private fun refreshPresetResultsFlow() {
        presetResultsFlow.value = synchronized(stateLock) { mutablePresetResults.values.toList() }
    }

    private fun refreshDiscoveredNodesFlow() {
        discoveredNodesFlow.value = synchronized(stateLock) { mutableDiscoveredNodes.values.toList() }
    }

    override suspend fun insertSession(session: DiscoverySessionEntity): Long {
        val id =
            synchronized(stateLock) {
                val id = nextSessionId++
                mutableSessions[id] = session.copy(id = id)
                id
            }
        refreshSessionsFlow()
        return id
    }

    override suspend fun updateSession(session: DiscoverySessionEntity) {
        synchronized(stateLock) {
            if (mutableSessions.containsKey(session.id)) mutableSessions[session.id] = session
        }
        refreshSessionsFlow()
    }

    override fun getAllSessions(): Flow<List<DiscoverySessionEntity>> = sessionsFlow

    // The Room snapshot query intentionally has no ORDER BY; preserve that contract instead of mirroring the live flow.
    override suspend fun getAllSessionsSnapshot(): List<DiscoverySessionEntity> =
        synchronized(stateLock) { mutableSessions.values.toList() }

    override suspend fun getSession(sessionId: Long): DiscoverySessionEntity? =
        synchronized(stateLock) { mutableSessions[sessionId] }

    override suspend fun updateSessionCompletionStatus(sessionId: Long, status: String): Int {
        val updated =
            synchronized(stateLock) {
                val session = mutableSessions[sessionId] ?: return@synchronized false
                mutableSessions[sessionId] = session.copy(completionStatus = status)
                true
            }
        if (updated) refreshSessionsFlow()
        return if (updated) 1 else 0
    }

    override suspend fun updateSessionAggregates(
        sessionId: Long,
        totalUniqueNodes: Int,
        totalDwellSeconds: Long,
        totalMessages: Int,
        totalSensorPackets: Int,
        furthestNodeDistance: Double,
        avgChannelUtilization: Double,
    ) {
        val updated =
            synchronized(stateLock) {
                val session = mutableSessions[sessionId] ?: return@synchronized false
                mutableSessions[sessionId] =
                    session.copy(
                        totalUniqueNodes = totalUniqueNodes,
                        totalDwellSeconds = totalDwellSeconds,
                        totalMessages = totalMessages,
                        totalSensorPackets = totalSensorPackets,
                        furthestNodeDistance = furthestNodeDistance,
                        avgChannelUtilization = avgChannelUtilization,
                    )
                true
            }
        if (updated) refreshSessionsFlow()
    }

    override suspend fun updateRecoverableSessionCompletionStatus(sessionId: Long, status: String): Int {
        val updated =
            synchronized(stateLock) {
                val session =
                    mutableSessions[sessionId]?.takeIf { it.completionStatus in DiscoverySessionStatus.RECOVERABLE }
                        ?: return@synchronized false
                mutableSessions[sessionId] = session.copy(completionStatus = status)
                true
            }
        if (updated) refreshSessionsFlow()
        return if (updated) 1 else 0
    }

    override fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?> =
        sessionsFlow.map { sessions -> sessions.firstOrNull { it.id == sessionId } }

    override suspend fun deleteSession(sessionId: Long) {
        synchronized(stateLock) {
            mutableSessions.remove(sessionId)
            val resultIds = mutablePresetResults.values.filter { it.sessionId == sessionId }.map { it.id }
            resultIds.forEach { resultId ->
                mutableDiscoveredNodes.entries.removeAll { it.value.presetResultId == resultId }
                mutablePresetResults.remove(resultId)
            }
        }
        refreshSessionsFlow()
        refreshPresetResultsFlow()
        refreshDiscoveredNodesFlow()
    }

    override suspend fun insertPresetResult(result: DiscoveryPresetResultEntity): Long {
        val id =
            synchronized(stateLock) {
                val id = nextPresetResultId++
                mutablePresetResults[id] = result.copy(id = id)
                id
            }
        refreshPresetResultsFlow()
        return id
    }

    override suspend fun updatePresetResult(result: DiscoveryPresetResultEntity) {
        synchronized(stateLock) {
            if (mutablePresetResults.containsKey(result.id)) mutablePresetResults[result.id] = result
        }
        refreshPresetResultsFlow()
    }

    override suspend fun getPresetResults(sessionId: Long): List<DiscoveryPresetResultEntity> =
        synchronized(stateLock) { mutablePresetResults.values.filter { it.sessionId == sessionId } }

    override fun getPresetResultsFlow(sessionId: Long): Flow<List<DiscoveryPresetResultEntity>> =
        presetResultsFlow.map { results -> results.filter { it.sessionId == sessionId } }

    override suspend fun insertDiscoveredNode(node: DiscoveredNodeEntity): Long {
        val id =
            synchronized(stateLock) {
                val id = nextNodeId++
                mutableDiscoveredNodes[id] = node.copy(id = id)
                id
            }
        refreshDiscoveredNodesFlow()
        return id
    }

    override suspend fun insertDiscoveredNodes(nodes: List<DiscoveredNodeEntity>) {
        synchronized(stateLock) {
            nodes.forEach { node ->
                val id = nextNodeId++
                mutableDiscoveredNodes[id] = node.copy(id = id)
            }
        }
        refreshDiscoveredNodesFlow()
    }

    override suspend fun updateDiscoveredNode(node: DiscoveredNodeEntity) {
        synchronized(stateLock) {
            if (mutableDiscoveredNodes.containsKey(node.id)) mutableDiscoveredNodes[node.id] = node
        }
        refreshDiscoveredNodesFlow()
    }

    override suspend fun getDiscoveredNodes(presetResultId: Long): List<DiscoveredNodeEntity> =
        synchronized(stateLock) { mutableDiscoveredNodes.values.filter { it.presetResultId == presetResultId } }

    override fun getDiscoveredNodesFlow(presetResultId: Long): Flow<List<DiscoveredNodeEntity>> =
        discoveredNodesFlow.map { nodes -> nodes.filter { it.presetResultId == presetResultId } }

    override suspend fun getUniqueNodeNums(sessionId: Long): List<Int> =
        synchronized(stateLock) {
            mutablePresetResults.values
                .filter { it.sessionId == sessionId }
                .flatMap { result -> mutableDiscoveredNodes.values.filter { it.presetResultId == result.id } }
                .map { it.nodeNum }
                .distinct()
        }

    override suspend fun getUniqueNodeCount(sessionId: Long): Int = getUniqueNodeNums(sessionId).size

    override suspend fun getMaxDistance(sessionId: Long): Double? =
        synchronized(stateLock) {
            mutablePresetResults.values
                .filter { it.sessionId == sessionId }
                .flatMap { result -> mutableDiscoveredNodes.values.filter { it.presetResultId == result.id } }
                .mapNotNull { it.distanceFromUser }
                .maxOrNull()
        }

    // The DAO method name is historical; the Room query currently returns only the session entity.
    override suspend fun getSessionWithResults(sessionId: Long): DiscoverySessionEntity? =
        synchronized(stateLock) { mutableSessions[sessionId] }

    override suspend fun markInterruptedSessions() {
        synchronized(stateLock) {
            mutableSessions.keys.toList().forEach { key ->
                val session = checkNotNull(mutableSessions[key])
                if (session.completionStatus == DiscoverySessionStatus.IN_PROGRESS) {
                    mutableSessions[key] = session.copy(completionStatus = DiscoverySessionStatus.INTERRUPTED)
                }
            }
        }
        refreshSessionsFlow()
    }

    override suspend fun getInterruptedSession(deviceAddress: String): DiscoverySessionEntity? =
        synchronized(stateLock) {
            mutableSessions.values
                .filter { it.deviceAddress == deviceAddress && it.completionStatus in DiscoverySessionStatus.RECOVERABLE }
                .maxByOrNull { it.timestamp }
        }
}
