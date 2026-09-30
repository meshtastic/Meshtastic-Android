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
package org.meshtastic.feature.node.metrics

import kotlin.time.Duration.Companion.minutes

private const val GAP_MEDIAN_MULTIPLIER = 3
private val MIN_GAP_SECONDS = 5.minutes.inWholeSeconds

/**
 * Splits [items] into runs that a chart should draw as separate lines, cutting wherever consecutive readings are more
 * than the gap threshold apart. [items] must be sorted ascending by [timeSeconds].
 *
 * The threshold is [GAP_MEDIAN_MULTIPLIER] times the median spacing, never below [MIN_GAP_SECONDS], so it follows the
 * node's own reporting interval.
 */
internal fun <T> splitAtGaps(items: List<T>, timeSeconds: (T) -> Int): List<List<T>> {
    if (items.size < 2) return listOf(items).filter { it.isNotEmpty() }
    val deltas = items.zipWithNext { a, b -> (timeSeconds(b) - timeSeconds(a)).toLong() }
    val median = deltas.sorted()[deltas.size / 2]
    val threshold = maxOf(median * GAP_MEDIAN_MULTIPLIER, MIN_GAP_SECONDS)
    val runs = mutableListOf(mutableListOf(items.first()))
    items.zipWithNext().forEachIndexed { index, (_, next) ->
        if (deltas[index] > threshold) runs.add(mutableListOf(next)) else runs.last().add(next)
    }
    return runs
}
