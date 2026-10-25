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
package org.meshtastic.feature.map

import org.meshtastic.core.model.Node
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Nodes closer than this on the ground share one marker, since no zoom level separates them. Matches the Apple client's
 * `MapColocation.spreadMeters`.
 */
const val STACK_SPREAD_METERS = 5.0

/**
 * Groups nodes that sit within [spreadMeters] of each other, so each spot draws as one marker.
 *
 * Reduced position precision truncates every node in a cell to the same point, and radios at one site report fixes a
 * few meters apart; both would otherwise draw as chips piled on one another. Each group is anchored on its first
 * member, and groups keep the order their first member had in [nodes].
 */
fun stackCoincident(nodes: List<Node>, myNodeNum: Int?, spreadMeters: Double = STACK_SPREAD_METERS): List<NodeStack> {
    val onTop = compareBy<Node> { MapNodePolicy.priorityOf(it, myNodeNum) }.thenByDescending { it.num }
    val groups = mutableListOf<MutableList<Node>>()
    // Anchors bucketed into latitude bands spreadMeters tall, so a node is only measured against its own band and the
    // two beside it rather than against every group.
    val bands = mutableMapOf<Int, MutableList<Int>>()
    nodes.forEach { node ->
        val band = node.latitudeBand(spreadMeters)
        val group =
            (band - 1..band + 1)
                .flatMap { bands[it].orEmpty() }
                .firstOrNull { groups[it].first().metersFrom(node) < spreadMeters }
        if (group != null) {
            groups[group] += node
        } else {
            groups += mutableListOf(node)
            bands.getOrPut(band) { mutableListOf() } += groups.lastIndex
        }
    }
    return groups.map { members -> NodeStack(top = members.maxWith(onTop), members = members) }
}

/**
 * Nodes at one spot. [top] is the one drawn: this node or a favorite first, then the lowest node number, which stays
 * put as packets arrive so a busy spot's chip does not keep changing.
 */
data class NodeStack(val top: Node, val members: List<Node>) {
    val count: Int
        get() = members.size
}

/** What a stack's badge reads. Material 3 caps a badge's count at four characters. */
fun stackBadgeLabel(count: Int): String = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString()

private const val MAX_BADGE_COUNT = 999

private fun Node.latitudeBand(spreadMeters: Double): Int = floor(latitude * METERS_PER_DEGREE / spreadMeters).toInt()

/** Equirectangular ground distance, which is exact enough at the few meters this compares. */
private fun Node.metersFrom(other: Node): Double {
    val north = (other.latitude - latitude) * METERS_PER_DEGREE
    val east =
        (other.longitude - longitude) * METERS_PER_DEGREE * cos((latitude + other.latitude) / 2 * RADIANS_PER_DEGREE)
    return hypot(north, east)
}

private const val METERS_PER_DEGREE = 111_320.0
private const val RADIANS_PER_DEGREE = PI / 180
