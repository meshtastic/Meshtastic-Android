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
package org.meshtastic.feature.map.component

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName

/**
 * One node in a cluster the map cannot zoom apart.
 *
 * [node] is nullable because the two maps learn about cluster members differently: the Google map holds the node it
 * clustered, while the MapLibre map reads names off the cluster's own leaf features and may be listing a node the
 * database no longer has. When it is present the row gets the same chip the rest of the app draws.
 */
data class ClusterMemberEntry(val nodeNum: Int, val title: String, val subtitle: String, val node: Node?)

/**
 * The order both maps list a spot's nodes in: favorites first, then by name with runs of digits compared as numbers, so
 * S6 comes before S10.
 */
internal fun List<ClusterMemberEntry>.inDialogOrder(): List<ClusterMemberEntry> = sortedWith(
    compareByDescending<ClusterMemberEntry> { it.node?.isFavorite == true }
        .then { a, b -> naturalCompare(a.sortName(), b.sortName()) }
        .thenBy { it.nodeNum },
)

// The Google map's title carries a heard-ago suffix, so the long name, where known, is what both maps sort on.
private fun ClusterMemberEntry.sortName(): String = node?.effectiveLongName?.ifBlank { null } ?: title

// ASCII digits only, in the split and in the test alike: Char.isDigit also accepts other scripts' digits, which the
// split would leave inside a text run, and the two disagreeing breaks the comparator's ordering.
private val nameRuns = Regex("""[0-9]+|[^0-9]+""")

internal fun naturalCompare(a: String, b: String): Int {
    val x = nameRuns.findAll(a.lowercase()).map { it.value }.toList()
    val y = nameRuns.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val (p, q) = x[i] to y[i]
        val order =
            if (p[0] in '0'..'9' && q[0] in '0'..'9') {
                compareValuesBy(p.trimStart('0'), q.trimStart('0'), { it.length }, { it })
            } else {
                p.compareTo(q)
            }
        if (order != 0) return order
    }
    return x.size.compareTo(y.size)
}
