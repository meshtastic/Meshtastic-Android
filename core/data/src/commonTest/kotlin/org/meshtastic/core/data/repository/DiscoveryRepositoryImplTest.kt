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

package org.meshtastic.core.data.repository

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveryRepositoryImplTest {

    private val storedNodes =
        listOf(
            DiscoveredNodeEntity(id = 1, presetResultId = 3, nodeNum = 30),
            DiscoveredNodeEntity(id = 2, presetResultId = 1, nodeNum = 10),
            DiscoveredNodeEntity(id = 3, presetResultId = 1, nodeNum = 11),
        )
    private val queriedBatches = mutableListOf<List<Long>>()
    private val dao = mock<DiscoveryDao>(MockMode.autofill)
    private val repository = DiscoveryRepositoryImpl(dao)

    init {
        everySuspend { dao.getDiscoveredNodesForPresetResults(any()) } calls
            { call ->
                val ids: List<Long> = call.arg(0)
                queriedBatches += ids
                storedNodes.filter { it.presetResultId in ids }
            }
    }

    @Test
    fun nodesByPresetResultComeFromOneBatchedQueryKeyedInTheOrderGiven() = runTest {
        val nodesByPreset = repository.getNodesByPresetResult(listOf(3L, 1L, 2L))

        assertEquals(listOf(listOf(3L, 1L, 2L)), queriedBatches)
        verifySuspend(VerifyMode.exactly(0)) { dao.getDiscoveredNodes(any()) }
        assertEquals(listOf(3L, 1L, 2L), nodesByPreset.keys.toList())
        assertEquals(listOf(30L), nodesByPreset.getValue(3L).map { it.nodeNum })
        assertEquals(listOf(10L, 11L), nodesByPreset.getValue(1L).map { it.nodeNum })
        assertTrue(nodesByPreset.getValue(2L).isEmpty(), "a preset result with no nodes still gets an entry")
    }

    @Test
    fun presetResultIdsPastTheBindParameterLimitAreQueriedInChunks() = runTest {
        val ids = (1L..1000L).toList()

        val nodesByPreset = repository.getNodesByPresetResult(ids)

        assertEquals(listOf(999, 1), queriedBatches.map { it.size })
        assertEquals(ids, queriedBatches.flatten())
        assertEquals(listOf(10L, 11L), nodesByPreset.getValue(1L).map { it.nodeNum })
    }

    @Test
    fun noPresetResultIdsIssueNoQuery() = runTest {
        assertTrue(repository.getNodesByPresetResult(emptyList()).isEmpty())
        assertTrue(queriedBatches.isEmpty())
    }
}
