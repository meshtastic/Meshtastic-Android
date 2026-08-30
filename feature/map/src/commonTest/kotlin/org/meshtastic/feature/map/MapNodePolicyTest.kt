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
import org.meshtastic.proto.Config
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rules both map engines answer: who appears, and who draws on top. */
class MapNodePolicyTest {

    @Suppress("LongParameterList")
    private fun node(
        num: Int,
        latitude: Double,
        longitude: Double,
        lastHeard: Int = 0,
        isFavorite: Boolean = false,
        role: Config.DeviceConfig.Role = Config.DeviceConfig.Role.CLIENT,
        hopsAway: Int = 0,
        viaMqtt: Boolean = false,
        isIgnored: Boolean = false,
        shortName: String = "ABCD",
    ) = Node(
        num = num,
        position = Position(latitude_i = (latitude * 1e7).toInt(), longitude_i = (longitude * 1e7).toInt()),
        lastHeard = lastHeard,
        isFavorite = isFavorite,
        user = User(short_name = shortName, role = role),
        hopsAway = hopsAway,
        viaMqtt = viaMqtt,
        isIgnored = isIgnored,
    )

    @Suppress("LongParameterList")
    private fun filters(
        onlyFavorites: Boolean = false,
        lastHeard: LastHeardFilter = LastHeardFilter.Any,
        excludedRoles: Set<Config.DeviceConfig.Role> = emptySet(),
        onlyOnline: Boolean = false,
        onlyDirect: Boolean = false,
        excludeMqtt: Boolean = false,
        showIgnored: Boolean = false,
        includeUnknown: Boolean = true,
    ) = BaseMapViewModel.MapFilterState(
        onlyFavorites = onlyFavorites,
        showWaypoints = true,
        showPrecisionCircle = true,
        lastHeardFilter = lastHeard,
        lastHeardTrackFilter = LastHeardFilter.Any,
        excludedRoles = excludedRoles,
        onlyOnline = onlyOnline,
        onlyDirect = onlyDirect,
        excludeMqtt = excludeMqtt,
        showIgnored = showIgnored,
        includeUnknown = includeUnknown,
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

    @Test
    fun `a node whose role is excluded is hidden`() {
        val nodes =
            listOf(
                node(1, 45.0, -122.0, role = Config.DeviceConfig.Role.ROUTER),
                node(2, 45.1, -122.1, role = Config.DeviceConfig.Role.CLIENT),
            )
        assertEquals(listOf(2), visible(nodes, filters(excludedRoles = setOf(Config.DeviceConfig.Role.ROUTER))))
    }

    @Test
    fun `excluding CLIENT also hides every node that never reported a role`() {
        // CLIENT is 0, the proto default, so "never said" and "said CLIENT" are the same value on the wire. Worth
        // pinning: it is the one surprising consequence of a per-role filter.
        val nodes = listOf(node(1, 45.0, -122.0), node(2, 45.1, -122.1, role = Config.DeviceConfig.Role.ROUTER))
        assertEquals(listOf(2), visible(nodes, filters(excludedRoles = setOf(Config.DeviceConfig.Role.CLIENT))))
    }

    @Test
    fun `the online filter drops nodes unheard for over two hours`() {
        // 97_000 is 3_000s back, inside the two-hour window; 90_000 is 10_000s back, outside it.
        val nodes = listOf(node(1, 45.0, -122.0, lastHeard = 90_000), node(2, 45.1, -122.1, lastHeard = 97_000))
        assertEquals(listOf(2), visible(nodes, filters(onlyOnline = true), now = 100_000))
    }

    @Test
    fun `the direct filter drops both relayed nodes and nodes of unknown distance`() {
        // The node list's query is `hops_away <= 0 AND hops_away >= 0`, so -1 — never measured — is not direct.
        val nodes =
            listOf(node(1, 45.0, -122.0, hopsAway = 2), node(2, 45.1, -122.1), node(3, 45.2, -122.2, hopsAway = -1))
        assertEquals(listOf(2), visible(nodes, filters(onlyDirect = true)))
    }

    @Test
    fun `the mqtt filter drops nodes heard over mqtt`() {
        val nodes = listOf(node(1, 45.0, -122.0, viaMqtt = true), node(2, 45.1, -122.1))
        assertEquals(listOf(2), visible(nodes, filters(excludeMqtt = true)))
    }

    @Test
    fun `ignored nodes are hidden by default`() {
        val nodes = listOf(node(1, 45.0, -122.0, isIgnored = true), node(2, 45.1, -122.1))
        assertEquals(listOf(2), visible(nodes, filters()))
    }

    @Test
    fun `showing ignored nodes adds them rather than showing only them`() {
        // The node list segregates — its filter is `isIgnored == showIgnored` — but a map of nothing but ignored
        // nodes is not a view anyone wants. Here the toggle reads literally: include them too.
        val nodes = listOf(node(1, 45.0, -122.0, isIgnored = true), node(2, 45.1, -122.1))
        assertEquals(listOf(1, 2), visible(nodes, filters(showIgnored = true)))
    }

    @Test
    fun `excluding unknown nodes drops the ones with no name yet`() {
        // "Unknown" is a node that has not sent a short name, matching the node list's `short_name IS NOT NULL`.
        val nodes = listOf(node(1, 45.0, -122.0, shortName = ""), node(2, 45.1, -122.1))
        assertEquals(listOf(2), visible(nodes, filters(includeUnknown = false)))
    }

    @Test
    fun `my own node survives every new filter`() {
        val mine =
            node(
                1,
                45.0,
                -122.0,
                lastHeard = 0,
                role = Config.DeviceConfig.Role.ROUTER,
                hopsAway = 3,
                viaMqtt = true,
                isIgnored = true,
                shortName = "",
            )
        val state =
            filters(
                excludedRoles = setOf(Config.DeviceConfig.Role.ROUTER),
                onlyOnline = true,
                onlyDirect = true,
                excludeMqtt = true,
                includeUnknown = false,
            )
        assertEquals(listOf(1), visible(listOf(mine), state, now = 100_000, mine = 1))
    }
}
