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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus

/** Shared in-memory [DiscoveryDao] for history/map tests; optionally exposes a live session-list flow. */
internal class SharedInMemoryDiscoveryDao(private val flowBackedSessions: Boolean = false) : DiscoveryDao {
    private var nextSessionId = 1L
    private var nextPresetResultId = 1L
    private var nextNodeId = 1L
    private val sessions = mutableMapOf<Long, DiscoverySessionEntity>()
    private val presetResults = mutableMapOf<Long, DiscoveryPresetResultEntity>()
    private val discoveredNodes = mutableMapOf<Long, DiscoveredNodeEntity>()
    private val sessionsFlow = MutableStateFlow<List<DiscoverySessionEntity>>(emptyList())

    private fun refreshSessionsFlow() {
        if (flowBackedSessions) sessionsFlow.update { sessions.values.sortedByDescending { it.timestamp } }
    }

    override suspend fun insertSession(session: DiscoverySessionEntity): Long {
        val id = nextSessionId++
        sessions[id] = session.copy(id = id)
        refreshSessionsFlow()
        return id
    }

    override suspend fun updateSession(session: DiscoverySessionEntity) {
        sessions[session.id] = session
        refreshSessionsFlow()
    }

    override fun getAllSessions(): Flow<List<DiscoverySessionEntity>> =
        if (flowBackedSessions) sessionsFlow else flowOf(sessions.values.sortedByDescending { it.timestamp })

    override suspend fun getAllSessionsSnapshot(): List<DiscoverySessionEntity> = sessions.values.toList()

    override suspend fun getSession(sessionId: Long): DiscoverySessionEntity? = sessions[sessionId]

    override suspend fun updateSessionCompletionStatus(sessionId: Long, status: String) {
        sessions[sessionId]?.let { sessions[sessionId] = it.copy(completionStatus = status) }
        refreshSessionsFlow()
    }

    override suspend fun updateRecoverableSessionCompletionStatus(sessionId: Long, status: String) {
        sessions[sessionId]
            ?.takeIf { it.completionStatus in DiscoverySessionStatus.RECOVERABLE }
            ?.let { sessions[sessionId] = it.copy(completionStatus = status) }
        refreshSessionsFlow()
    }

    override fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?> = MutableStateFlow(sessions[sessionId])

    override suspend fun deleteSession(sessionId: Long) {
        sessions.remove(sessionId)
        val resultIds = presetResults.values.filter { it.sessionId == sessionId }.map { it.id }
        resultIds.forEach { resultId ->
            discoveredNodes.entries.removeAll { it.value.presetResultId == resultId }
            presetResults.remove(resultId)
        }
        refreshSessionsFlow()
    }

    override suspend fun insertPresetResult(result: DiscoveryPresetResultEntity): Long {
        val id = nextPresetResultId++
        presetResults[id] = result.copy(id = id)
        return id
    }

    override suspend fun updatePresetResult(result: DiscoveryPresetResultEntity) {
        presetResults[result.id] = result
    }

    override suspend fun getPresetResults(sessionId: Long) = presetResults.values.filter { it.sessionId == sessionId }

    override fun getPresetResultsFlow(sessionId: Long) =
        flowOf(presetResults.values.filter { it.sessionId == sessionId })

    override suspend fun insertDiscoveredNode(node: DiscoveredNodeEntity): Long {
        val id = nextNodeId++
        discoveredNodes[id] = node.copy(id = id)
        return id
    }

    override suspend fun insertDiscoveredNodes(nodes: List<DiscoveredNodeEntity>) {
        nodes.forEach { insertDiscoveredNode(it) }
    }

    override suspend fun updateDiscoveredNode(node: DiscoveredNodeEntity) {
        discoveredNodes[node.id] = node
    }

    override suspend fun getDiscoveredNodes(presetResultId: Long) =
        discoveredNodes.values.filter { it.presetResultId == presetResultId }

    override fun getDiscoveredNodesFlow(presetResultId: Long) =
        flowOf(discoveredNodes.values.filter { it.presetResultId == presetResultId })

    override suspend fun getUniqueNodeNums(sessionId: Long) = presetResults.values
        .filter { it.sessionId == sessionId }
        .flatMap { result -> discoveredNodes.values.filter { it.presetResultId == result.id } }
        .map { it.nodeNum }
        .distinct()

    override suspend fun getUniqueNodeCount(sessionId: Long) = getUniqueNodeNums(sessionId).size

    override suspend fun getMaxDistance(sessionId: Long) = presetResults.values
        .filter { it.sessionId == sessionId }
        .flatMap { result -> discoveredNodes.values.filter { it.presetResultId == result.id } }
        .mapNotNull { it.distanceFromUser }
        .maxOrNull()

    override suspend fun getSessionWithResults(sessionId: Long) = sessions[sessionId]

    override suspend fun markInterruptedSessions() {
        sessions.keys.toList().forEach { key ->
            val session = checkNotNull(sessions[key])
            if (session.completionStatus == DiscoverySessionStatus.IN_PROGRESS) {
                sessions[key] = session.copy(completionStatus = DiscoverySessionStatus.INTERRUPTED)
            }
        }
        refreshSessionsFlow()
    }

    override suspend fun getInterruptedSession(deviceAddress: String): DiscoverySessionEntity? = sessions.values
        .filter { it.deviceAddress == deviceAddress && it.completionStatus in DiscoverySessionStatus.RECOVERABLE }
        .maxByOrNull { it.timestamp }
}
