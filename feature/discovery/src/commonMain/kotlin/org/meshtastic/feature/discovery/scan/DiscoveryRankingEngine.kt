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
package org.meshtastic.feature.discovery.scan

import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity

/** Input bundle for ranking: a preset result together with its discovered nodes. */
data class PresetRankingInput(
    val presetResult: DiscoveryPresetResultEntity,
    val discoveredNodes: List<DiscoveredNodeEntity>,
)

/** Per-criterion score breakdown for a ranked preset. */
data class RankingScoreBreakdown(
    /** Criterion 1: unique discovered node count. */
    val uniqueNodeCount: Int,
    /** Criterion 2: neighbor-report diversity (direct + mesh neighbor count). */
    val neighborDiversity: Int,
    /** Criterion 3: non-duplicate packet count (numPacketsRx - numRxDupe). */
    val nonDupePacketCount: Int,
    /** Criterion 4a: median SNR across discovered nodes. */
    val medianSnr: Float,
    /** Criterion 4b: median RSSI across discovered nodes (tiebreak within criterion 4). */
    val medianRssi: Int,
    /** Criterion 5: best known distance to a valid-position node (metres). */
    val bestKnownDistance: Double,
    /** Criterion 6: failure/reconnect penalty (packet failure rate). */
    val failurePenalty: Double,
)

/** Output ranking for a single preset. */
data class PresetRanking(
    /** 1-based rank (1 = best). Tied presets share the same rank. */
    val rank: Int,
    val presetResult: DiscoveryPresetResultEntity,
    val scoreBreakdown: RankingScoreBreakdown,
    /** True when this preset tied with at least one other after all 6 criteria. */
    val isTied: Boolean,
)

/**
 * Deterministic 6-level heuristic ranking engine for discovery preset results.
 *
 * The ranking order (best-first) is:
 * 1. Highest unique discovered node count
 * 2. Highest neighbor-report diversity (direct + mesh neighbor mentions)
 * 3. Highest non-duplicate packet count
 * 4. Best median link quality (median SNR first, then median RSSI)
 * 5. Greatest best-known distance to a valid-position node
 * 6. Lowest failure / reconnect penalty
 *
 * If two presets still tie after all heuristics they are labelled as tied.
 */
@Single
class DiscoveryRankingEngine {

    /**
     * Rank the given preset inputs best-to-worst using the 6-level heuristic.
     *
     * @return sorted list of [PresetRanking] (index 0 = best). Empty input yields empty output.
     */
    fun rank(inputs: List<PresetRankingInput>): List<PresetRanking> {
        if (inputs.isEmpty()) return emptyList()

        val scored = inputs.map { it.toScored() }
        val sorted = scored.sortedWith(RANKING_COMPARATOR)

        return assignRanks(sorted)
    }

    // ---- internal helpers ----

    private data class ScoredPreset(val presetResult: DiscoveryPresetResultEntity, val breakdown: RankingScoreBreakdown)

    private fun PresetRankingInput.toScored(): ScoredPreset {
        val pr = presetResult
        val nodes = discoveredNodes

        val snrValues = nodes.map { it.snr }.sorted()
        val rssiValues = nodes.map { it.rssi }.sorted()

        return ScoredPreset(
            presetResult = pr,
            breakdown =
            RankingScoreBreakdown(
                uniqueNodeCount = pr.uniqueNodes,
                neighborDiversity = pr.directNeighborCount + pr.meshNeighborCount,
                nonDupePacketCount = (pr.numPacketsRx - pr.numRxDupe).coerceAtLeast(0),
                medianSnr = median(snrValues) { it },
                medianRssi = medianInt(rssiValues),
                bestKnownDistance = nodes.mapNotNull { it.distanceFromUser }.maxOrNull() ?: 0.0,
                failurePenalty = pr.packetFailureRate,
            ),
        )
    }

    private fun assignRanks(sorted: List<ScoredPreset>): List<PresetRanking> {
        if (sorted.isEmpty()) return emptyList()

        // Detect tie groups: consecutive entries that compare as 0.
        val tieFlags = BooleanArray(sorted.size)
        for (i in 0 until sorted.size - 1) {
            if (RANKING_COMPARATOR.compare(sorted[i], sorted[i + 1]) == 0) {
                tieFlags[i] = true
                tieFlags[i + 1] = true
            }
        }

        val result = mutableListOf<PresetRanking>()
        var currentRank = 1
        for (i in sorted.indices) {
            if (i > 0 && RANKING_COMPARATOR.compare(sorted[i - 1], sorted[i]) != 0) {
                currentRank = i + 1
            }
            result +=
                PresetRanking(
                    rank = currentRank,
                    presetResult = sorted[i].presetResult,
                    scoreBreakdown = sorted[i].breakdown,
                    isTied = tieFlags[i],
                )
        }
        return result
    }

    companion object {
        /**
         * Comparator implementing the 6-level heuristic (best-first ordering). "Higher is better" criteria use
         * descending compare (b vs a). "Lower is better" criteria (penalty) use ascending compare (a vs b).
         */
        private val RANKING_COMPARATOR =
            Comparator<ScoredPreset> { a, b ->
                // 1. Highest unique node count
                var cmp = b.breakdown.uniqueNodeCount.compareTo(a.breakdown.uniqueNodeCount)
                if (cmp != 0) return@Comparator cmp

                // 2. Highest neighbor-report diversity
                cmp = b.breakdown.neighborDiversity.compareTo(a.breakdown.neighborDiversity)
                if (cmp != 0) return@Comparator cmp

                // 3. Highest non-duplicate packet count
                cmp = b.breakdown.nonDupePacketCount.compareTo(a.breakdown.nonDupePacketCount)
                if (cmp != 0) return@Comparator cmp

                // 4. Best median link quality: SNR first, then RSSI
                cmp = b.breakdown.medianSnr.compareTo(a.breakdown.medianSnr)
                if (cmp != 0) return@Comparator cmp
                cmp = b.breakdown.medianRssi.compareTo(a.breakdown.medianRssi)
                if (cmp != 0) return@Comparator cmp

                // 5. Greatest best-known distance
                cmp = b.breakdown.bestKnownDistance.compareTo(a.breakdown.bestKnownDistance)
                if (cmp != 0) return@Comparator cmp

                // 6. Lowest failure/reconnect penalty
                a.breakdown.failurePenalty.compareTo(b.breakdown.failurePenalty)
            }

        /** Compute the median of a sorted float-convertible list. Returns 0 for empty. */
        internal fun <T> median(sorted: List<T>, toFloat: (T) -> Float): Float {
            if (sorted.isEmpty()) return 0f
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) {
                (toFloat(sorted[mid - 1]) + toFloat(sorted[mid])) / 2f
            } else {
                toFloat(sorted[mid])
            }
        }

        /** Compute the median of a sorted Int list. Returns 0 for empty. */
        private fun medianInt(sorted: List<Int>): Int {
            if (sorted.isEmpty()) return 0
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) {
                (sorted[mid - 1] + sorted[mid]) / 2
            } else {
                sorted[mid]
            }
        }
    }
}
