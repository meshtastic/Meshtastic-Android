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
package org.meshtastic.core.data.ai

import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository

/**
 * Resolves fuzzy node and channel name queries to concrete identifiers.
 *
 * Uses longest-common-substring matching with a minimum threshold of 50% of the query length. Returns an error with
 * candidate list if ambiguous.
 */
@Single
class FuzzyNameResolver(
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
) {

    /** Resolve a node name query to a node number and user ID. */
    fun resolveNodeName(query: String): NodeNameResult {
        val nodes = nodeRepository.nodeDBbyNum.value
        val candidates =
            nodes.values
                .filter { it.user.long_name.isNotBlank() }
                .map { NameCandidate(it.user.long_name, it.num, it.user.id) }

        return matchName(query, candidates)
    }

    /**
     * Resolve a channel name query to a channel index.
     *
     * Admin channels are excluded from resolution (NFR-001).
     */
    @Suppress("ReturnCount")
    suspend fun resolveChannelName(query: String): ChannelNameResult {
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val candidates =
            channelSet.settings
                .mapIndexed { index, settings -> IndexedChannel(settings.name, index) }
                .filter { it.name.isNotBlank() }
                // Exclude admin channels (convention: channel named "admin" is sensitive)
                .filter { !it.name.equals("admin", ignoreCase = true) }

        if (candidates.isEmpty()) return ChannelNameResult.NotFound

        // Exact match first
        candidates
            .firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?.let {
                return ChannelNameResult.Found(it.index, it.name)
            }

        // Fuzzy match
        val scored =
            candidates
                .map { it to longestCommonSubstringLength(query.lowercase(), it.name.lowercase()) }
                .filter { (_, score) -> score >= (query.length * MATCH_THRESHOLD).toInt().coerceAtLeast(1) }
                .sortedByDescending { it.second }

        return when {
            scored.isEmpty() -> ChannelNameResult.NotFound
            scored.size == 1 -> ChannelNameResult.Found(scored[0].first.index, scored[0].first.name)
            scored[0].second > scored[1].second -> ChannelNameResult.Found(scored[0].first.index, scored[0].first.name)
            else -> ChannelNameResult.Ambiguous(scored.map { it.first.name })
        }
    }

    @Suppress("ReturnCount")
    private fun matchName(query: String, candidates: List<NameCandidate>): NodeNameResult {
        if (candidates.isEmpty()) return NodeNameResult.NotFound

        // Exact match first (case-insensitive)
        candidates
            .firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?.let {
                return NodeNameResult.Found(it.nodeNum, it.userId)
            }

        // Fuzzy match using longest common substring
        val minScore = (query.length * MATCH_THRESHOLD).toInt().coerceAtLeast(1)
        val scored =
            candidates
                .map { it to longestCommonSubstringLength(query.lowercase(), it.name.lowercase()) }
                .filter { (_, score) -> score >= minScore }
                .sortedByDescending { it.second }

        return when {
            scored.isEmpty() -> NodeNameResult.NotFound

            scored.size == 1 -> NodeNameResult.Found(scored[0].first.nodeNum, scored[0].first.userId)

            scored[0].second > scored[1].second -> {
                // Clear winner — top score is strictly greater
                NodeNameResult.Found(scored[0].first.nodeNum, scored[0].first.userId)
            }

            else -> NodeNameResult.Ambiguous(scored.map { it.first.name })
        }
    }

    private data class NameCandidate(val name: String, val nodeNum: Int, val userId: String)

    private data class IndexedChannel(val name: String, val index: Int)

    companion object {
        /** Minimum match ratio — longest common substring must be ≥50% of query length. */
        const val MATCH_THRESHOLD = 0.5
    }
}

/** Compute the length of the longest common substring between two strings. */
internal fun longestCommonSubstringLength(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    var maxLen = 0
    // Space-optimized: only need previous row
    val prev = IntArray(b.length + 1)
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            curr[j] =
                if (a[i - 1] == b[j - 1]) {
                    (prev[j - 1] + 1).also { if (it > maxLen) maxLen = it }
                } else {
                    0
                }
        }
        prev.indices.forEach {
            prev[it] = curr[it]
            curr[it] = 0
        }
    }
    return maxLen
}

sealed class NodeNameResult {
    data class Found(val nodeNum: Int, val userId: String) : NodeNameResult()

    data class Ambiguous(val candidates: List<String>) : NodeNameResult()

    data object NotFound : NodeNameResult()
}

sealed class ChannelNameResult {
    data class Found(val channelIndex: Int, val name: String) : ChannelNameResult()

    data class Ambiguous(val candidates: List<String>) : ChannelNameResult()

    data object NotFound : ChannelNameResult()
}
