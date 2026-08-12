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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for session history: sorting, session load by ID, and delete behavior (D042). */
class DiscoveryHistoryBehaviorTest {

    private val dao = SharedInMemoryDiscoveryDao()

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
        completionStatus = DiscoverySessionStatus.COMPLETE,
    )

    // endregion
}
