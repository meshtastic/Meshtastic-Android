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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for session history: sorting, session load by ID, and delete behavior (D042). */
class DiscoveryHistoryBehaviorTest {

    private val dao = HistoryTestDao()

    // region History sorting

    @Test
    fun getAllSessions_returnsNewestFirst() = runTest {
        dao.insertSession(session(timestamp = 1_000L))
        dao.insertSession(session(timestamp = 3_000L))
        dao.insertSession(session(timestamp = 2_000L))

        val sessions = dao.getAllSessions().first()
        assertEquals(3, sessions.size)
        assertEquals(3_000L, sessions[0].timestamp, "Newest session should be first")
        assertEquals(2_000L, sessions[1].timestamp)
        assertEquals(1_000L, sessions[2].timestamp, "Oldest session should be last")
    }

    @Test
    fun getAllSessions_emptyListWhenNoSessions() = runTest {
        val sessions = dao.getAllSessions().first()
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun getAllSessions_singleSession() = runTest {
        dao.insertSession(session(timestamp = 5_000L))
        val sessions = dao.getAllSessions().first()
        assertEquals(1, sessions.size)
        assertEquals(5_000L, sessions.first().timestamp)
    }

    // endregion

    // region Session load by ID

    @Test
    fun sessionLoadById_returnsStoredSession() = runTest {
        val id = dao.insertSession(session(timestamp = 10_000L, homePreset = "MEDIUM_FAST"))
        val loaded = dao.getSession(id)
        assertNotNull(loaded)
        assertEquals("MEDIUM_FAST", loaded.homePreset)
        assertEquals(10_000L, loaded.timestamp)
    }

    @Test
    fun sessionLoadById_returnsNullForMissing() = runTest {
        assertNull(dao.getSession(999L), "Should return null for non-existent session")
    }

    // endregion

    // region Delete behavior

    @Test
    fun deleteSession_removesFromHistory() = runTest {
        val id1 = dao.insertSession(session(timestamp = 1L))
        val id2 = dao.insertSession(session(timestamp = 2L))

        dao.deleteSession(id1)

        val remaining = dao.getAllSessions().first()
        assertEquals(1, remaining.size)
        assertEquals(id2, remaining[0].id)
    }

    @Test
    fun deleteSession_cascadesPresetResultsAndNodes() = runTest {
        val sessionId = dao.insertSession(session())
        val presetId =
            dao.insertPresetResult(DiscoveryPresetResultEntity(sessionId = sessionId, presetName = "LONG_FAST"))
        dao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = presetId, nodeNum = 100))

        dao.deleteSession(sessionId)

        assertNull(dao.getSession(sessionId))
        assertTrue(dao.getPresetResults(sessionId).isEmpty(), "Preset results should cascade-delete")
        assertTrue(dao.getDiscoveredNodes(presetId).isEmpty(), "Discovered nodes should cascade-delete")
    }

    @Test
    fun deleteSession_doesNotAffectOtherSessions() = runTest {
        val id1 = dao.insertSession(session(timestamp = 1L))
        val id2 = dao.insertSession(session(timestamp = 2L))
        val preset2 = dao.insertPresetResult(DiscoveryPresetResultEntity(sessionId = id2, presetName = "SHORT_FAST"))
        dao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = preset2, nodeNum = 42))

        dao.deleteSession(id1)

        assertNotNull(dao.getSession(id2), "Other sessions should be unaffected")
        assertEquals(1, dao.getPresetResults(id2).size)
        assertEquals(1, dao.getDiscoveredNodes(preset2).size)
    }

    @Test
    fun deleteAllSessions_leavesEmptyHistory() = runTest {
        val id1 = dao.insertSession(session(timestamp = 1L))
        val id2 = dao.insertSession(session(timestamp = 2L))

        dao.deleteSession(id1)
        dao.deleteSession(id2)

        assertTrue(dao.getAllSessions().first().isEmpty())
    }

    // endregion

    // region Helpers

    private fun session(timestamp: Long = 1_000_000L, homePreset: String = "LONG_FAST") = DiscoverySessionEntity(
        timestamp = timestamp,
        presetsScanned = "LONG_FAST",
        homePreset = homePreset,
        completionStatus = "complete",
    )

    // endregion
}

// region In-memory DAO for history tests

private class HistoryTestDao : DiscoveryDao {
    private var nextSessionId = 1L
    private var nextPresetResultId = 1L
    private var nextNodeId = 1L

    private val sessions = mutableMapOf<Long, DiscoverySessionEntity>()
    private val presetResults = mutableMapOf<Long, DiscoveryPresetResultEntity>()
    private val discoveredNodes = mutableMapOf<Long, DiscoveredNodeEntity>()
    private val sessionsFlow = MutableStateFlow<List<DiscoverySessionEntity>>(emptyList())

    private fun refreshSessionsFlow() {
        sessionsFlow.update { sessions.values.sortedByDescending { it.timestamp } }
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

    override fun getAllSessions(): Flow<List<DiscoverySessionEntity>> = sessionsFlow

    override suspend fun getAllSessionsSnapshot(): List<DiscoverySessionEntity> = sessions.values.toList()

    override suspend fun getSession(sessionId: Long) = sessions[sessionId]

    override fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?> = MutableStateFlow(sessions[sessionId])

    override suspend fun deleteSession(sessionId: Long) {
        sessions.remove(sessionId)
        val resultIds = presetResults.values.filter { it.sessionId == sessionId }.map { it.id }
        resultIds.forEach { rid ->
            discoveredNodes.entries.removeAll { it.value.presetResultId == rid }
            presetResults.remove(rid)
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
        .flatMap { pr -> discoveredNodes.values.filter { it.presetResultId == pr.id } }
        .map { it.nodeNum }
        .distinct()

    override suspend fun getUniqueNodeCount(sessionId: Long) = getUniqueNodeNums(sessionId).size

    override suspend fun getMaxDistance(sessionId: Long) = presetResults.values
        .filter { it.sessionId == sessionId }
        .flatMap { pr -> discoveredNodes.values.filter { it.presetResultId == pr.id } }
        .mapNotNull { it.distanceFromUser }
        .maxOrNull()

    override suspend fun getSessionWithResults(sessionId: Long) = sessions[sessionId]

    override suspend fun markInterruptedSessions() {
        sessions.keys.toList().forEach { key ->
            val session = sessions[key]!!
            if (session.completionStatus == "in_progress") {
                sessions[key] = session.copy(completionStatus = "interrupted")
            }
        }
    }

    override suspend fun getInterruptedSession(deviceAddress: String): DiscoverySessionEntity? = sessions.values
        .filter { it.deviceAddress == deviceAddress && it.completionStatus in setOf("in_progress", "interrupted") }
        .maxByOrNull { it.timestamp }
}

// endregion
