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
package org.meshtastic.feature.node.component

import org.meshtastic.core.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests for [hopHistogram] — the pure bucketing behind the nodes-per-hop view. */
class NodeHopHistogramTest {

    private fun node(hops: Int, lastHeard: Int = 1_000) =
        Node(num = hops * 100 + lastHeard, hopsAway = hops, lastHeard = lastHeard)

    @Test
    fun empty_input_gives_empty_list() {
        assertEquals(emptyList(), hopHistogram(emptyList(), cutoffSecs = null))
    }

    @Test
    fun buckets_are_contiguous_and_preserve_middle_gaps() {
        val nodes = listOf(node(0), node(0), node(1), node(3))
        // hop 2 has zero nodes but must still appear between 1 and 3
        assertEquals(listOf(2, 1, 0, 1), hopHistogram(nodes, cutoffSecs = null))
    }

    @Test
    fun unknown_hops_are_excluded() {
        val nodes = listOf(node(-1), node(0), node(-1))
        assertEquals(listOf(1), hopHistogram(nodes, cutoffSecs = null))
    }

    @Test
    fun all_unknown_gives_empty_list() {
        assertEquals(emptyList(), hopHistogram(listOf(node(-1), node(-1)), cutoffSecs = null))
    }

    @Test
    fun cutoff_drops_nodes_heard_before_it() {
        val nodes = listOf(node(0, lastHeard = 100), node(0, lastHeard = 500), node(1, lastHeard = 500))
        // cutoff=300 keeps only lastHeard >= 300
        assertEquals(listOf(1, 1), hopHistogram(nodes, cutoffSecs = 300))
    }

    @Test
    fun null_cutoff_keeps_everything_regardless_of_last_heard() {
        val nodes = listOf(node(0, lastHeard = 0), node(0, lastHeard = 999))
        assertEquals(listOf(2), hopHistogram(nodes, cutoffSecs = null))
    }
}
