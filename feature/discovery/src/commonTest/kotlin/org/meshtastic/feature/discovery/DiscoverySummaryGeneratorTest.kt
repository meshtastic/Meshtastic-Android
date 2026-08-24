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

import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoverySummaryGeneratorTest {

    private val generator = DiscoverySummaryGenerator()

    // ---- Helpers ----

    private fun session(
        id: Long = 1,
        totalUniqueNodes: Int = 10,
        completionStatus: String = "complete",
        avgChannelUtilization: Double = 0.0,
    ) = DiscoverySessionEntity(
        id = id,
        timestamp = 1_000_000L,
        presetsScanned = "LongFast,ShortFast",
        homePreset = "LongFast",
        totalUniqueNodes = totalUniqueNodes,
        avgChannelUtilization = avgChannelUtilization,
        completionStatus = completionStatus,
    )

    private fun preset(
        id: Long = 1,
        sessionId: Long = 1,
        name: String = "LongFast",
        uniqueNodes: Int = 5,
        directNeighborCount: Int = 3,
        meshNeighborCount: Int = 2,
        messageCount: Int = 10,
        sensorPacketCount: Int = 5,
        avgChannelUtilization: Double = 15.0,
        avgAirtimeRate: Double = 3.0,
        packetSuccessRate: Double = 0.95,
        packetFailureRate: Double = 0.05,
    ) = DiscoveryPresetResultEntity(
        id = id,
        sessionId = sessionId,
        presetName = name,
        uniqueNodes = uniqueNodes,
        directNeighborCount = directNeighborCount,
        meshNeighborCount = meshNeighborCount,
        messageCount = messageCount,
        sensorPacketCount = sensorPacketCount,
        avgChannelUtilization = avgChannelUtilization,
        avgAirtimeRate = avgAirtimeRate,
        packetSuccessRate = packetSuccessRate,
        packetFailureRate = packetFailureRate,
    )

    // ---- generateSessionSummary ----

    @Test
    fun emptyPresetsReturnsNoPresetsMessage() {
        val result = generator.generateSessionSummary(session(), emptyList())
        assertEquals("No presets were scanned during this session.", result)
    }

    @Test
    fun singlePresetSessionMentionsPresetName() {
        val p = preset(name = "LongFast", uniqueNodes = 7)
        val result = generator.generateSessionSummary(session(), listOf(p))
        assertContains(result, "LongFast")
        assertContains(result, "7")
    }

    @Test
    fun singlePresetSessionIncludesChannelUtilization() {
        val p = preset(name = "LongFast", avgChannelUtilization = 12.5)
        val result = generator.generateSessionSummary(session(), listOf(p))
        assertContains(result, "12.5%")
    }

    @Test
    fun multiPresetSessionRanksByNodeCount() {
        val winner = preset(id = 1, name = "LongFast", uniqueNodes = 12, avgChannelUtilization = 20.0)
        val loser = preset(id = 2, name = "ShortFast", uniqueNodes = 4, avgChannelUtilization = 10.0)
        val result = generator.generateSessionSummary(session(), listOf(loser, winner))
        assertContains(result, "LongFast")
        assertContains(result, "most nodes")
    }

    @Test
    fun multiPresetSessionMentionsAlternativePresets() {
        val winner = preset(id = 1, name = "LongFast", uniqueNodes = 12, avgChannelUtilization = 20.0)
        val loser = preset(id = 2, name = "ShortFast", uniqueNodes = 4, avgChannelUtilization = 10.0)
        val result = generator.generateSessionSummary(session(), listOf(loser, winner))
        assertContains(result, "ShortFast")
        assertContains(result, "4 node")
    }

    @Test
    fun highCongestionGeneratesWarning() {
        val congested = preset(name = "LongFast", avgChannelUtilization = 35.0)
        val result = generator.generateSessionSummary(session(), listOf(congested))
        assertContains(result, "congestion")
        assertContains(result, "LongFast")
    }

    @Test
    fun lowCongestionNoWarning() {
        val clear = preset(name = "LongFast", avgChannelUtilization = 10.0)
        val result = generator.generateSessionSummary(session(), listOf(clear))
        assertFalse(result.contains("congestion"), "Should not mention congestion at 10%")
    }

    @Test
    fun chatDominatedTrafficNoted() {
        val chatHeavy = preset(name = "LongFast", messageCount = 100, sensorPacketCount = 5)
        val result = generator.generateSessionSummary(session(), listOf(chatHeavy))
        assertContains(result, "chat-dominated")
    }

    @Test
    fun sensorDominatedTrafficNoted() {
        val sensorHeavy = preset(name = "LongFast", messageCount = 2, sensorPacketCount = 50)
        val result = generator.generateSessionSummary(session(), listOf(sensorHeavy))
        assertContains(result, "sensor-dominated")
    }

    @Test
    fun lowTrafficCountsNoMixNote() {
        val lowTraffic = preset(name = "LongFast", messageCount = 3, sensorPacketCount = 1)
        val result = generator.generateSessionSummary(session(), listOf(lowTraffic))
        assertFalse(result.contains("dominated"), "Should not classify traffic mix below threshold")
    }

    @Test
    fun equalTrafficMixNoNote() {
        val balanced = preset(name = "LongFast", messageCount = 0, sensorPacketCount = 0)
        val result = generator.generateSessionSummary(session(), listOf(balanced))
        assertFalse(result.contains("dominated"), "Should not mention traffic mix when counts are zero")
    }

    @Test
    fun completedSessionRecommendationSaysCompleted() {
        val p = preset(name = "LongFast")
        val result = generator.generateSessionSummary(session(completionStatus = "complete"), listOf(p))
        assertContains(result, "completed")
        assertContains(result, "Recommendation")
    }

    @Test
    fun stoppedSessionRecommendationSaysPartial() {
        val p = preset(name = "LongFast")
        val result = generator.generateSessionSummary(session(completionStatus = "stopped"), listOf(p))
        assertContains(result, "partially completed")
    }

    @Test
    fun recommendationIncludesBestPresetName() {
        val winner = preset(id = 1, name = "MediumSlow", uniqueNodes = 15, avgChannelUtilization = 5.0)
        val loser = preset(id = 2, name = "LongFast", uniqueNodes = 3, avgChannelUtilization = 5.0)
        val result = generator.generateSessionSummary(session(), listOf(loser, winner))
        assertContains(result, "Recommendation: Use MediumSlow")
    }

    // ---- generatePresetSummary ----

    @Test
    fun presetSummaryIncludesPresetName() {
        val result = generator.generatePresetSummary(preset(name = "LongFast"))
        assertTrue(result.startsWith("LongFast"))
    }

    @Test
    fun presetSummaryIncludesNodeCounts() {
        val p = preset(uniqueNodes = 8, directNeighborCount = 5, meshNeighborCount = 3)
        val result = generator.generatePresetSummary(p)
        assertContains(result, "8 nodes")
        assertContains(result, "5 direct")
        assertContains(result, "3 mesh")
    }

    @Test
    fun presetSummaryIncludesChannelUtilization() {
        val p = preset(avgChannelUtilization = 42.7)
        val result = generator.generatePresetSummary(p)
        assertContains(result, "42.7%")
        assertContains(result, "channel utilization")
    }

    @Test
    fun presetSummaryHighCongestionMarked() {
        val p = preset(avgChannelUtilization = 30.0)
        val result = generator.generatePresetSummary(p)
        assertContains(result, "congested")
    }

    @Test
    fun presetSummaryLowCongestionNotMarked() {
        val p = preset(avgChannelUtilization = 20.0)
        val result = generator.generatePresetSummary(p)
        assertFalse(result.contains("congested"))
    }

    @Test
    fun presetSummaryChatDominated() {
        val p = preset(messageCount = 50, sensorPacketCount = 5)
        val result = generator.generatePresetSummary(p)
        assertContains(result, "chat-dominated")
    }

    @Test
    fun presetSummarySensorDominated() {
        val p = preset(messageCount = 2, sensorPacketCount = 40)
        val result = generator.generatePresetSummary(p)
        assertContains(result, "sensor-dominated")
    }

    @Test
    fun presetSummaryKnownPresetIncludesDataRate() {
        val p = preset(name = "Long Fast")
        val result = generator.generatePresetSummary(p)
        // "Long Fast" matches LoRaPresetReference key and should include data rate
        assertTrue(result.contains("kbps") || result.contains("bps"), "Should include data rate for known preset")
    }

    // ---- buildSessionPrompt ----

    @Test
    fun sessionPromptContainsInstructions() {
        val p = preset(name = "LongFast", uniqueNodes = 5)
        val result = generator.buildSessionPrompt(session(), listOf(p))
        assertContains(result, "Analyze this Meshtastic mesh radio discovery scan")
        assertContains(result, "recommend the best modem preset")
        assertContains(result, "concise")
    }

    @Test
    fun sessionPromptContainsSessionMetadata() {
        val s = session(totalUniqueNodes = 15, completionStatus = "complete")
        val p = preset(name = "LongFast")
        val result = generator.buildSessionPrompt(s, listOf(p))
        assertContains(result, "15 unique nodes")
        assertContains(result, "complete")
    }

    @Test
    fun sessionPromptContainsPresetData() {
        val p = preset(name = "ShortFast", uniqueNodes = 8, messageCount = 20, sensorPacketCount = 3)
        val result = generator.buildSessionPrompt(session(), listOf(p))
        assertContains(result, "ShortFast")
        assertContains(result, "Nodes: 8")
        assertContains(result, "Messages: 20")
    }

    @Test
    fun sessionPromptContainsChannelUtilization() {
        val p = preset(name = "LongFast", avgChannelUtilization = 33.5, avgAirtimeRate = 5.2)
        val result = generator.buildSessionPrompt(session(), listOf(p))
        assertContains(result, "33.5")
        assertContains(result, "5.2")
    }

    @Test
    fun sessionPromptContainsCongestionGuidance() {
        val p = preset(name = "LongFast")
        val result = generator.buildSessionPrompt(session(), listOf(p))
        assertContains(result, "Channel util >25% indicates congestion")
    }

    // ---- buildPresetPrompt ----

    @Test
    fun presetPromptContainsPresetName() {
        val p = preset(name = "MediumFast")
        val result = generator.buildPresetPrompt(p)
        assertContains(result, "MediumFast")
        assertContains(result, "summarize")
    }

    @Test
    fun presetPromptContainsMetrics() {
        val p =
            preset(
                name = "LongFast",
                uniqueNodes = 6,
                directNeighborCount = 4,
                meshNeighborCount = 2,
                avgChannelUtilization = 18.0,
            )
        val result = generator.buildPresetPrompt(p)
        assertContains(result, "Nodes: 6")
        assertContains(result, "Direct: 4")
        assertContains(result, "Mesh: 2")
        assertContains(result, "18.0")
    }

    @Test
    fun presetPromptContainsGuidanceContext() {
        val p = preset(name = "LongFast")
        val result = generator.buildPresetPrompt(p)
        assertContains(result, "traffic pattern")
        assertContains(result, "node density")
    }
}
