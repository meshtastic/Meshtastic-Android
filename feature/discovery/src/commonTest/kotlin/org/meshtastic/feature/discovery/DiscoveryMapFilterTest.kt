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

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the map ViewModel's preset filtering, mapped/unmapped counts, and topology toggle behavior (D028).
 *
 * These are logic-level tests that validate the ViewModel's state flows without rendering UI.
 */
class DiscoveryMapFilterTest {

    // region Preset filter selection

    @Test
    fun defaultFilter_isNull_showsAllPresets() {
        val vm = createViewModel()
        assertNull(vm.selectedPresetFilter.value, "Default filter should be null (show all)")
    }

    @Test
    fun selectPresetFilter_updatesState() {
        val vm = createViewModel()
        vm.selectPresetFilter(42L)
        assertEquals(42L, vm.selectedPresetFilter.value)
    }

    @Test
    fun selectPresetFilter_null_resetsToAll() {
        val vm = createViewModel()
        vm.selectPresetFilter(42L)
        vm.selectPresetFilter(null)
        assertNull(vm.selectedPresetFilter.value)
    }

    // endregion

    // region Topology toggle

    @Test
    fun topologyOverlay_defaultOff() {
        val vm = createViewModel()
        assertFalse(vm.showTopologyOverlay.value)
    }

    @Test
    fun toggleTopologyOverlay_turnsOn() {
        val vm = createViewModel()
        vm.toggleTopologyOverlay()
        assertTrue(vm.showTopologyOverlay.value)
    }

    @Test
    fun toggleTopologyOverlay_turnsOff() {
        val vm = createViewModel()
        vm.toggleTopologyOverlay()
        vm.toggleTopologyOverlay()
        assertFalse(vm.showTopologyOverlay.value)
    }

    // endregion

    // region Map stats (mapped/unmapped counts)

    @Test
    fun mapStats_initiallyZero() {
        val vm = createViewModel()
        val stats = vm.mapStats.value
        assertEquals(0, stats.totalNodes)
        assertEquals(0, stats.mappedNodes)
        assertEquals(0, stats.unmappedNodes)
    }

    @Test
    fun discoveryMapStats_dataClass_equality() {
        val stats1 = DiscoveryMapStats(totalNodes = 5, mappedNodes = 3, unmappedNodes = 2)
        val stats2 = DiscoveryMapStats(totalNodes = 5, mappedNodes = 3, unmappedNodes = 2)
        assertEquals(stats1, stats2)
    }

    // endregion

    // region Preset results loaded

    @Test
    fun sharedDaoFlowsObserveWritesAfterSubscription() = runTest {
        val dao = SharedInMemoryDiscoveryDao()
        val sessionId = dao.insertSession(testSession())
        val observedResults =
            async(start = CoroutineStart.UNDISPATCHED) { dao.getPresetResultsFlow(sessionId).first { it.size == 2 } }
        val presetResultId =
            dao.insertPresetResult(DiscoveryPresetResultEntity(sessionId = sessionId, presetName = "LONG_FAST"))
        dao.insertPresetResult(DiscoveryPresetResultEntity(sessionId = sessionId, presetName = "SHORT_FAST"))
        val observedNodes =
            async(start = CoroutineStart.UNDISPATCHED) {
                dao.getDiscoveredNodesFlow(presetResultId).first { it.isNotEmpty() }
            }
        dao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = presetResultId, nodeNum = 123L))

        assertEquals(setOf("LONG_FAST", "SHORT_FAST"), observedResults.await().map { it.presetName }.toSet())
        assertEquals(listOf(123L), observedNodes.await().map { it.nodeNum })
    }

    // endregion

    // region Helpers

    private fun createViewModel(): DiscoveryMapViewModel {
        val dao = SharedInMemoryDiscoveryDao()
        return DiscoveryMapViewModel(sessionId = 1L, discoveryDao = dao)
    }

    private fun testSession() = DiscoverySessionEntity(
        timestamp = 1_000_000L,
        presetsScanned = "LONG_FAST",
        homePreset = "LONG_FAST",
        completionStatus = DiscoverySessionStatus.COMPLETE,
    )

    // endregion
}
