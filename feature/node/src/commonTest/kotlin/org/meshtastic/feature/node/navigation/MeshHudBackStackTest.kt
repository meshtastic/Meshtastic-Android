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
package org.meshtastic.feature.node.navigation

import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.ContactsRoute
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.NodeDetailRoute
import org.meshtastic.core.navigation.NodesRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class MeshHudBackStackTest {

    @Test
    fun `mesh view from a detail opened by a mesh view replaces that mesh view`() {
        val stack = mutableListOf<NavKey>(NodesRoute.Nodes, MapRoute.MeshHud(1), NodesRoute.NodeDetail(2))

        stack.navigateFromNodeDetail(MapRoute.MeshHud(focusNodeNum = 2))

        assertEquals(listOf<NavKey>(NodesRoute.Nodes, MapRoute.MeshHud(focusNodeNum = 2)), stack)
    }

    @Test
    fun `mesh view from a position log under a mesh view pops through the detail screens`() {
        val stack =
            mutableListOf<NavKey>(
                NodesRoute.Nodes,
                MapRoute.MeshHud(1),
                NodesRoute.NodeDetail(2),
                NodeDetailRoute.PositionLog(2),
            )
        val track = MapRoute.MeshHud(focusNodeNum = 2, trackNodeNum = 2)

        stack.navigateFromNodeDetail(track)

        assertEquals(listOf<NavKey>(NodesRoute.Nodes, track), stack)
    }

    @Test
    fun `mesh view from a detail opened by the node list is pushed`() {
        val stack = mutableListOf<NavKey>(NodesRoute.Nodes, NodesRoute.NodeDetail(2))

        stack.navigateFromNodeDetail(MapRoute.MeshHud(focusNodeNum = 2))

        assertEquals(
            listOf<NavKey>(NodesRoute.Nodes, NodesRoute.NodeDetail(2), MapRoute.MeshHud(focusNodeNum = 2)),
            stack,
        )
    }

    @Test
    fun `other routes from a detail above a mesh view are pushed`() {
        val stack = mutableListOf<NavKey>(NodesRoute.Nodes, MapRoute.MeshHud(1), NodesRoute.NodeDetail(2))

        stack.navigateFromNodeDetail(ContactsRoute.Messages("0!00000002"))

        assertEquals(
            listOf<NavKey>(
                NodesRoute.Nodes,
                MapRoute.MeshHud(1),
                NodesRoute.NodeDetail(2),
                ContactsRoute.Messages("0!00000002"),
            ),
            stack,
        )
    }

    @Test
    fun `mesh view directly on top of a mesh view is pushed`() {
        val stack = mutableListOf<NavKey>(NodesRoute.Nodes, MapRoute.MeshHud(1))

        stack.navigateFromNodeDetail(MapRoute.MeshHud(focusNodeNum = 2))

        assertEquals(listOf<NavKey>(NodesRoute.Nodes, MapRoute.MeshHud(1), MapRoute.MeshHud(focusNodeNum = 2)), stack)
    }

    @Test
    fun `mesh view onto an empty stack is pushed`() {
        val stack = mutableListOf<NavKey>()

        stack.navigateFromNodeDetail(MapRoute.MeshHud(focusNodeNum = 2))

        assertEquals(listOf<NavKey>(MapRoute.MeshHud(focusNodeNum = 2)), stack)
    }
}
