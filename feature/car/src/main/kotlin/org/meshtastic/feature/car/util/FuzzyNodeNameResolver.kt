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

package org.meshtastic.feature.car.util

import org.koin.core.annotation.Factory

/**
 * Resolves voice-spoken node names to actual node numbers using fuzzy matching.
 * TODO: Consolidate with FuzzyNameResolver from core/data when AppFunctions branch merges.
 */
@Factory
class FuzzyNodeNameResolver {

    data class ResolvedNode(val nodeNum: Int, val name: String, val confidence: Float)

    fun resolve(spokenName: String, nodes: List<Pair<Int, String>>): ResolvedNode? {
        if (spokenName.isBlank() || nodes.isEmpty()) return null

        val normalizedInput = spokenName.lowercase().trim()

        return nodes
            .map { (nodeNum, name) ->
                val normalizedName = name.lowercase().trim()
                val score = lcsScore(normalizedInput, normalizedName)
                ResolvedNode(nodeNum, name, score)
            }
            .filter { it.confidence >= MIN_CONFIDENCE }
            .maxByOrNull { it.confidence }
    }

    private fun lcsScore(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val maxLen = maxOf(a.length, b.length)
        val lcsLen = lcsLength(a, b)
        return lcsLen.toFloat() / maxLen.toFloat()
    }

    private fun lcsLength(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp[m][n]
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.6f
    }
}
