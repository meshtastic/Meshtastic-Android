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

import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.feature.discovery.scan.DiscoveryRankingEngine
import org.meshtastic.feature.discovery.scan.PresetRankingInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryRankingEngineTest {

    private val engine = DiscoveryRankingEngine()

    // ---- Helpers ----

    private fun preset(
        id: Long = 1,
        sessionId: Long = 100,
        name: String = "LongFast",
        uniqueNodes: Int = 0,
        directNeighborCount: Int = 0,
        meshNeighborCount: Int = 0,
        numPacketsRx: Int = 0,
        numRxDupe: Int = 0,
        packetFailureRate: Double = 0.0,
    ) = DiscoveryPresetResultEntity(
        id = id,
        sessionId = sessionId,
        presetName = name,
        uniqueNodes = uniqueNodes,
        directNeighborCount = directNeighborCount,
        meshNeighborCount = meshNeighborCount,
        numPacketsRx = numPacketsRx,
        numRxDupe = numRxDupe,
        packetFailureRate = packetFailureRate,
    )

    private fun node(
        presetResultId: Long = 1,
        nodeNum: Long = 1,
        snr: Float = 0f,
        rssi: Int = 0,
        distanceFromUser: Double? = null,
    ) = DiscoveredNodeEntity(
        presetResultId = presetResultId,
        nodeNum = nodeNum,
        snr = snr,
        rssi = rssi,
        distanceFromUser = distanceFromUser,
    )

    private fun input(preset: DiscoveryPresetResultEntity, nodes: List<DiscoveredNodeEntity> = emptyList()) =
        PresetRankingInput(preset, nodes)

    // ---- Tests ----

    @Test
    fun emptyInputReturnsEmptyOutput() {
        val result = engine.rank(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun singlePresetAlwaysRank1NotTied() {
        val p = preset(uniqueNodes = 5)
        val result = engine.rank(listOf(input(p)))
        assertEquals(1, result.size)
        assertEquals(1, result[0].rank)
        assertFalse(result[0].isTied)
        assertEquals(5, result[0].scoreBreakdown.uniqueNodeCount)
    }

    @Test
    fun criterion1UniqueNodeCountDecides() {
        val winner = preset(id = 1, name = "LongFast", uniqueNodes = 10)
        val loser = preset(id = 2, name = "ShortFast", uniqueNodes = 3)
        val result = engine.rank(listOf(input(loser), input(winner)))

        assertEquals(2, result.size)
        assertEquals("LongFast", result[0].presetResult.presetName)
        assertEquals(1, result[0].rank)
        assertEquals("ShortFast", result[1].presetResult.presetName)
        assertEquals(2, result[1].rank)
        assertFalse(result[0].isTied)
        assertFalse(result[1].isTied)
    }

    @Test
    fun criterion2NeighborDiversityBreaksTie() {
        val a = preset(id = 1, name = "A", uniqueNodes = 5, directNeighborCount = 3, meshNeighborCount = 4)
        val b = preset(id = 2, name = "B", uniqueNodes = 5, directNeighborCount = 1, meshNeighborCount = 2)
        val result = engine.rank(listOf(input(b), input(a)))

        assertEquals("A", result[0].presetResult.presetName, "Higher neighbor diversity wins")
        assertEquals(7, result[0].scoreBreakdown.neighborDiversity)
        assertEquals(3, result[1].scoreBreakdown.neighborDiversity)
    }

    @Test
    fun criterion3NonDupePacketCountBreaksTie() {
        val a =
            preset(
                id = 1,
                name = "A",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 100,
                numRxDupe = 10,
            )
        val b =
            preset(
                id = 2,
                name = "B",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 80,
                numRxDupe = 5,
            )
        val result = engine.rank(listOf(input(b), input(a)))

        assertEquals("A", result[0].presetResult.presetName, "Higher non-dupe packet count wins")
        assertEquals(90, result[0].scoreBreakdown.nonDupePacketCount)
        assertEquals(75, result[1].scoreBreakdown.nonDupePacketCount)
    }

    @Test
    fun criterion4MedianSnrBreaksTie() {
        val pA =
            preset(
                id = 1,
                name = "A",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
            )
        val pB =
            preset(
                id = 2,
                name = "B",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
            )
        val nodesA =
            listOf(
                node(presetResultId = 1, nodeNum = 1, snr = 10f),
                node(presetResultId = 1, nodeNum = 2, snr = 8f),
                node(presetResultId = 1, nodeNum = 3, snr = 12f),
            )
        val nodesB =
            listOf(
                node(presetResultId = 2, nodeNum = 4, snr = 2f),
                node(presetResultId = 2, nodeNum = 5, snr = 4f),
                node(presetResultId = 2, nodeNum = 6, snr = 3f),
            )
        val result = engine.rank(listOf(input(pB, nodesB), input(pA, nodesA)))

        assertEquals("A", result[0].presetResult.presetName, "Higher median SNR wins")
        assertEquals(10f, result[0].scoreBreakdown.medianSnr)
        assertEquals(3f, result[1].scoreBreakdown.medianSnr)
    }

    @Test
    fun criterion4MedianRssiBreaksTieOnSnr() {
        val pA =
            preset(
                id = 1,
                name = "A",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
            )
        val pB =
            preset(
                id = 2,
                name = "B",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
            )
        val nodesA =
            listOf(
                node(presetResultId = 1, nodeNum = 1, snr = 5f, rssi = -60),
                node(presetResultId = 1, nodeNum = 2, snr = 5f, rssi = -50),
                node(presetResultId = 1, nodeNum = 3, snr = 5f, rssi = -55),
            )
        val nodesB =
            listOf(
                node(presetResultId = 2, nodeNum = 4, snr = 5f, rssi = -90),
                node(presetResultId = 2, nodeNum = 5, snr = 5f, rssi = -80),
                node(presetResultId = 2, nodeNum = 6, snr = 5f, rssi = -85),
            )
        val result = engine.rank(listOf(input(pB, nodesB), input(pA, nodesA)))

        assertEquals("A", result[0].presetResult.presetName, "Higher median RSSI wins when SNR ties")
    }

    @Test
    fun criterion5BestKnownDistanceBreaksTie() {
        val pA =
            preset(
                id = 1,
                name = "A",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
            )
        val pB =
            preset(
                id = 2,
                name = "B",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
            )
        val nodesA =
            listOf(
                node(presetResultId = 1, nodeNum = 1, snr = 5f, rssi = -70, distanceFromUser = 5000.0),
                node(presetResultId = 1, nodeNum = 2, snr = 5f, rssi = -70, distanceFromUser = 3000.0),
            )
        val nodesB =
            listOf(
                node(presetResultId = 2, nodeNum = 3, snr = 5f, rssi = -70, distanceFromUser = 1000.0),
                node(presetResultId = 2, nodeNum = 4, snr = 5f, rssi = -70, distanceFromUser = 500.0),
            )
        val result = engine.rank(listOf(input(pB, nodesB), input(pA, nodesA)))

        assertEquals("A", result[0].presetResult.presetName, "Greater best-known distance wins")
        assertEquals(5000.0, result[0].scoreBreakdown.bestKnownDistance)
        assertEquals(1000.0, result[1].scoreBreakdown.bestKnownDistance)
    }

    @Test
    fun criterion6LowestFailurePenaltyBreaksTie() {
        val pA =
            preset(
                id = 1,
                name = "A",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
                packetFailureRate = 0.05,
            )
        val pB =
            preset(
                id = 2,
                name = "B",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
                packetFailureRate = 0.20,
            )
        val nodesA = listOf(node(presetResultId = 1, nodeNum = 1, snr = 5f, rssi = -70))
        val nodesB = listOf(node(presetResultId = 2, nodeNum = 2, snr = 5f, rssi = -70))
        val result = engine.rank(listOf(input(pB, nodesB), input(pA, nodesA)))

        assertEquals("A", result[0].presetResult.presetName, "Lower failure rate wins")
        assertEquals(0.05, result[0].scoreBreakdown.failurePenalty)
    }

    @Test
    fun allCriteriaTiedMarkedAsTied() {
        val pA =
            preset(
                id = 1,
                name = "A",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
                packetFailureRate = 0.1,
            )
        val pB =
            preset(
                id = 2,
                name = "B",
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
                numPacketsRx = 50,
                packetFailureRate = 0.1,
            )
        val nodesA = listOf(node(presetResultId = 1, nodeNum = 1, snr = 5f, rssi = -70, distanceFromUser = 1000.0))
        val nodesB = listOf(node(presetResultId = 2, nodeNum = 2, snr = 5f, rssi = -70, distanceFromUser = 1000.0))
        val result = engine.rank(listOf(input(pA, nodesA), input(pB, nodesB)))

        assertEquals(2, result.size)
        assertEquals(1, result[0].rank)
        assertEquals(1, result[1].rank, "Tied presets share the same rank")
        assertTrue(result[0].isTied)
        assertTrue(result[1].isTied)
    }

    @Test
    fun threePresetsWithOneFailedStillRanked() {
        val good =
            preset(
                id = 1,
                name = "LongFast",
                uniqueNodes = 10,
                directNeighborCount = 5,
                meshNeighborCount = 3,
                numPacketsRx = 100,
                packetFailureRate = 0.02,
            )
        val mediocre =
            preset(
                id = 2,
                name = "MedFast",
                uniqueNodes = 5,
                directNeighborCount = 2,
                meshNeighborCount = 1,
                numPacketsRx = 50,
                packetFailureRate = 0.10,
            )
        val failed =
            preset(
                id = 3,
                name = "ShortFast",
                uniqueNodes = 0,
                directNeighborCount = 0,
                meshNeighborCount = 0,
                numPacketsRx = 5,
                packetFailureRate = 0.9,
            )

        val result = engine.rank(listOf(input(failed), input(mediocre), input(good)))

        assertEquals(3, result.size)
        assertEquals("LongFast", result[0].presetResult.presetName)
        assertEquals(1, result[0].rank)
        assertEquals("MedFast", result[1].presetResult.presetName)
        assertEquals(2, result[1].rank)
        assertEquals("ShortFast", result[2].presetResult.presetName)
        assertEquals(3, result[2].rank)
        assertFalse(result[0].isTied)
        assertFalse(result[2].isTied)
    }

    @Test
    fun noNodesProducesZeroMediansAndDistance() {
        val p = preset(uniqueNodes = 3, numPacketsRx = 20)
        val result = engine.rank(listOf(input(p, emptyList())))

        assertEquals(0f, result[0].scoreBreakdown.medianSnr)
        assertEquals(0, result[0].scoreBreakdown.medianRssi)
        assertEquals(0.0, result[0].scoreBreakdown.bestKnownDistance)
    }

    @Test
    fun nodesWithoutDistanceYieldZeroBestDistance() {
        val p = preset(id = 1, uniqueNodes = 2)
        val nodes =
            listOf(
                node(presetResultId = 1, nodeNum = 1, snr = 5f, distanceFromUser = null),
                node(presetResultId = 1, nodeNum = 2, snr = 3f, distanceFromUser = null),
            )
        val result = engine.rank(listOf(input(p, nodes)))
        assertEquals(0.0, result[0].scoreBreakdown.bestKnownDistance)
    }

    @Test
    fun negativeDupeCountClampedToZero() {
        val p = preset(numPacketsRx = 5, numRxDupe = 10) // more dupes than rx — shouldn't go negative
        val result = engine.rank(listOf(input(p)))
        assertEquals(0, result[0].scoreBreakdown.nonDupePacketCount)
    }
}
