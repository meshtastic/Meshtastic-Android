/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.feature.discovery.ai.LoRaPresetReference

@Single
@Suppress("TooManyFunctions")
class DiscoverySummaryGenerator {

    fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String {
        if (presetResults.isEmpty()) return "No presets were scanned during this session."

        val ranked =
            presetResults.sortedWith(
                compareByDescending<DiscoveryPresetResultEntity> { it.uniqueNodes }.thenBy { it.avgChannelUtilization },
            )
        val best = ranked.first()

        val lines = buildList {
            add(buildPresetComparisonLine(best, presetResults))
            for (result in presetResults) {
                if (result.id != best.id) {
                    add(buildAlternativeLine(result))
                }
            }
            add(buildCongestionNote(presetResults))
            add(buildTrafficMixNote(presetResults))
            add(buildRecommendation(best, session))
        }

        return lines.filterNotNull().joinToString(" ")
    }

    fun generatePresetSummary(result: DiscoveryPresetResultEntity): String = buildString {
        val info = LoRaPresetReference.getInfo(result.presetName)
        append("${result.presetName}")
        if (info != null) append(" (${info.dataRate}, ${info.linkBudget} link budget)")
        append(": ${result.uniqueNodes} nodes")
        append(" (${result.directNeighborCount} direct, ${result.meshNeighborCount} mesh)")
        if (result.avgChannelUtilization > 0.0) {
            append(", ${formatPercent(result.avgChannelUtilization)} channel utilization")
            if (result.avgChannelUtilization > HIGH_CONGESTION_THRESHOLD) {
                append(" (congested)")
            }
        }
        if (result.messageCount > 0 || result.sensorPacketCount > 0) {
            val dominant = if (result.messageCount >= result.sensorPacketCount) "chat" else "sensor"
            append(", $dominant-dominated traffic")
        }
        append(".")
    }

    /** Build AI-style prompt for session-level analysis. Used by AI providers. */
    fun buildSessionPrompt(session: DiscoverySessionEntity, presetResults: List<DiscoveryPresetResultEntity>): String =
        buildString {
            appendLine(
                "Analyze this Meshtastic mesh radio discovery scan and recommend the best modem preset. " +
                    "Be concise (3-4 sentences).",
            )
            appendLine()
            appendLine("Session: ${session.totalUniqueNodes} unique nodes, status: ${session.completionStatus}")
            appendLine()
            append(LoRaPresetReference.buildReferenceBlock(presetResults.map { it.presetName }))
            appendLine("Channel util >25% indicates congestion; >50% causes significant packet loss.")
            appendLine()
            appendLine("Scan Results:")
            for (result in presetResults) {
                appendLine(formatPresetDataBlock(result))
            }
            appendLine()
            append(
                "Based on the scan data and preset reference, recommend which preset is best for this location. " +
                    "Consider node density, infrastructure count, channel utilization, airtime, and traffic mix. " +
                    "If congestion is high, recommend a faster preset.",
            )
        }

    /** Build AI-style prompt for per-preset analysis. Used by AI providers. */
    fun buildPresetPrompt(result: DiscoveryPresetResultEntity): String = buildString {
        appendLine(
            "Briefly summarize (1-2 sentences) the performance of the ${result.presetName} " +
                "Meshtastic modem preset based on this scan data.",
        )
        appendLine()
        val ref = LoRaPresetReference.formatReference(result.presetName)
        if (ref != null) appendLine("Preset info: $ref")
        appendLine("Channel util >25% indicates congestion; >50% causes significant packet loss.")
        appendLine()
        appendLine(formatPresetDataBlock(result))
        appendLine()
        append("Note if this preset is well-suited for the observed traffic pattern and node density.")
    }

    private fun formatPresetDataBlock(result: DiscoveryPresetResultEntity): String = buildString {
        append("  ${result.presetName}: ")
        append("Nodes: ${result.uniqueNodes} ")
        append("(Direct: ${result.directNeighborCount}, Mesh: ${result.meshNeighborCount})")
        append(", Messages: ${result.messageCount}, Sensor Packets: ${result.sensorPacketCount}")
        if (result.avgChannelUtilization > 0.0) {
            append(", Channel Util: ${formatPercent(result.avgChannelUtilization)}")
        }
        if (result.avgAirtimeRate > 0.0) {
            append(", Airtime: ${formatPercent(result.avgAirtimeRate)}")
        }
        if (result.packetSuccessRate > 0.0) {
            append(", Packet Success: ${formatPercent(result.packetSuccessRate * PERCENT_MULTIPLIER)}")
        }
    }

    private fun buildPresetComparisonLine(
        best: DiscoveryPresetResultEntity,
        allResults: List<DiscoveryPresetResultEntity>,
    ): String {
        val info = LoRaPresetReference.getInfo(best.presetName)
        val rateStr = if (info != null) " (${info.dataRate})" else ""
        if (allResults.size == 1) {
            return "${best.presetName}$rateStr discovered ${best.uniqueNodes} node(s) " +
                "with ${formatPercent(best.avgChannelUtilization)} channel utilization."
        }
        return "${best.presetName}$rateStr discovered the most nodes (${best.uniqueNodes}) " +
            "with ${describeUtilization(best.avgChannelUtilization)} channel utilization " +
            "(${formatPercent(best.avgChannelUtilization)})."
    }

    private fun buildAlternativeLine(result: DiscoveryPresetResultEntity): String {
        val utilDesc = describeUtilization(result.avgChannelUtilization)
        val utilPct = formatPercent(result.avgChannelUtilization)
        return "${result.presetName} found ${result.uniqueNodes} node(s) " +
            "with $utilDesc channel utilization ($utilPct)."
    }

    private fun buildCongestionNote(results: List<DiscoveryPresetResultEntity>): String? {
        val congested = results.filter { it.avgChannelUtilization > HIGH_CONGESTION_THRESHOLD }
        if (congested.isEmpty()) return null
        return "High congestion detected on ${congested.joinToString { it.presetName }}; " +
            "consider a faster preset to reduce airtime."
    }

    private fun buildTrafficMixNote(results: List<DiscoveryPresetResultEntity>): String? {
        val chatDominant = results.filter { it.messageCount > it.sensorPacketCount }
        val sensorDominant = results.filter { it.sensorPacketCount > it.messageCount }
        val parts = buildList {
            if (chatDominant.isNotEmpty()) {
                add("chat-dominated on ${chatDominant.joinToString { it.presetName }}")
            }
            if (sensorDominant.isNotEmpty()) {
                add("sensor-dominated on ${sensorDominant.joinToString { it.presetName }}")
            }
        }
        if (parts.isEmpty()) return null
        return "Traffic mix: ${parts.joinToString("; ")}."
    }

    private fun buildRecommendation(best: DiscoveryPresetResultEntity, session: DiscoverySessionEntity): String {
        val status = if (session.completionStatus == "complete") "completed" else "partially completed"
        return "Recommendation: Use ${best.presetName} for this location (scan $status)."
    }

    private fun describeUtilization(percent: Double): String = when {
        percent < LOW_UTIL_THRESHOLD -> "low"
        percent < MODERATE_UTIL_THRESHOLD -> "moderate"
        percent < HIGH_UTIL_THRESHOLD -> "high"
        else -> "very high"
    }

    private fun formatPercent(value: Double): String = "${NumberFormatter.format(value, 1)}%"

    companion object {
        private const val LOW_UTIL_THRESHOLD = 25.0
        private const val MODERATE_UTIL_THRESHOLD = 50.0
        private const val HIGH_UTIL_THRESHOLD = 75.0
        private const val HIGH_CONGESTION_THRESHOLD = 25.0
        private const val PERCENT_MULTIPLIER = 100.0
    }
}
