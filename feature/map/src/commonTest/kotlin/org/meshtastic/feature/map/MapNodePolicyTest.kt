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
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rules both map engines answer: who appears, and who draws on top. */
class MapNodePolicyTest {

    private fun node(num: Int, latitude: Double, longitude: Double, lastHeard: Int = 0, isFavorite: Boolean = false) =
        Node(
            num = num,
            position = Position(latitude_i = (latitude * 1e7).toInt(), longitude_i = (longitude * 1e7).toInt()),
            lastHeard = lastHeard,
            isFavorite = isFavorite,
        )

    private fun filters(onlyFavorites: Boolean = false, lastHeard: LastHeardFilter = LastHeardFilter.Any) =
        BaseMapViewModel.MapFilterState(
            onlyFavorites = onlyFavorites,
            showWaypoints = true,
            showPrecisionCircle = true,
            lastHeardFilter = lastHeard,
            lastHeardTrackFilter = LastHeardFilter.Any,
        )

    private fun visible(nodes: List<Node>, state: BaseMapViewModel.MapFilterState, now: Long = 0, mine: Int? = null) =
        MapNodePolicy.visibleNodes(nodes, state, now, mine).map { it.num }

    @Test
    fun `nodes without a fix never reach the map`() {
        assertEquals(listOf(2), visible(listOf(node(1, 0.0, 0.0), node(2, 45.0, -122.0)), filters()))
    }

    @Test
    fun `favourites filter keeps only favourites`() {
        val nodes = listOf(node(1, 45.0, -122.0), node(2, 45.1, -122.1, isFavorite = true))
        assertEquals(listOf(2), visible(nodes, filters(onlyFavorites = true)))
    }

    @Test
    fun `last heard filter drops nodes outside the window`() {
        val nodes = listOf(node(1, 45.0, -122.0, lastHeard = 0), node(2, 45.1, -122.1, lastHeard = 9_000))
        assertEquals(listOf(2), visible(nodes, filters(lastHeard = LastHeardFilter.OneHour), now = 10_000))
    }

    @Test
    fun `my own node survives the favourites filter`() {
        // You are always on your own map. The MapLibre map's own copy of these rules left this out, so filtering to
        // favourites hid you from yourself while the Google map kept you.
        val nodes = listOf(node(1, 45.0, -122.0), node(2, 45.1, -122.1, isFavorite = true))
        assertEquals(listOf(1, 2), visible(nodes, filters(onlyFavorites = true), mine = 1))
    }

    @Test
    fun `my own node survives the last heard filter`() {
        val nodes = listOf(node(1, 45.0, -122.0, lastHeard = 0), node(2, 45.1, -122.1, lastHeard = 9_000))
        assertEquals(listOf(1, 2), visible(nodes, filters(lastHeard = LastHeardFilter.OneHour), now = 10_000, mine = 1))
    }

    @Test
    fun `my own node still needs a fix to be drawn`() {
        // Exempt from the filters, not from physics: there is nowhere to draw it.
        assertEquals(emptyList(), visible(listOf(node(1, 0.0, 0.0)), filters(), mine = 1))
    }

    @Test
    fun `this node and favourites draw above everything else`() {
        val mine = node(1, 45.0, -122.0)
        val favourite = node(2, 45.0, -122.0, isFavorite = true)
        val ordinary = node(3, 45.0, -122.0)
        assertEquals(MapNodePolicy.PRIORITY_PROMINENT, MapNodePolicy.priorityOf(mine, myNodeNum = 1))
        assertEquals(MapNodePolicy.PRIORITY_PROMINENT, MapNodePolicy.priorityOf(favourite, myNodeNum = 1))
        assertEquals(MapNodePolicy.PRIORITY_ORDINARY, MapNodePolicy.priorityOf(ordinary, myNodeNum = 1))
        assertTrue(MapNodePolicy.PRIORITY_PROMINENT > MapNodePolicy.PRIORITY_ORDINARY)
    }
}
