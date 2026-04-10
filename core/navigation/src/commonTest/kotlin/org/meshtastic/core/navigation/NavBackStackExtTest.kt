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
package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class NavBackStackExtTest {

    // region replaceLast

    @Test
    fun `replaceLast on non-empty list replaces the last element`() {
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph, NodesRoute.Nodes)
        stack.replaceLast(NodesRoute.NodeDetail(destNum = 42))

        assertEquals(2, stack.size)
        assertEquals(NodesRoute.NodesGraph, stack[0])
        assertEquals(NodesRoute.NodeDetail(destNum = 42), stack[1])
    }

    @Test
    fun `replaceLast on single-element list replaces that element`() {
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph)
        stack.replaceLast(SettingsRoute.SettingsGraph())

        assertEquals(1, stack.size)
        assertEquals(SettingsRoute.SettingsGraph(), stack[0])
    }

    @Test
    fun `replaceLast on empty list adds the element`() {
        val stack = mutableListOf<NavKey>()
        stack.replaceLast(NodesRoute.Nodes)

        assertEquals(1, stack.size)
        assertEquals(NodesRoute.Nodes, stack[0])
    }

    @Test
    fun `replaceLast with same element does not mutate`() {
        val route = NodesRoute.Nodes
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph, route)
        stack.replaceLast(route)

        assertEquals(2, stack.size)
        assertEquals(route, stack[1])
    }

    // endregion

    // region replaceAll

    @Test
    fun `replaceAll replaces entire stack with new routes`() {
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph, NodesRoute.Nodes)
        val newRoutes = listOf<NavKey>(SettingsRoute.SettingsGraph(), SettingsRoute.About)

        stack.replaceAll(newRoutes)

        assertEquals(newRoutes, stack)
    }

    @Test
    fun `replaceAll with shorter list trims excess elements`() {
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph, NodesRoute.Nodes, NodesRoute.NodeDetail(destNum = 42))
        val newRoutes = listOf<NavKey>(SettingsRoute.SettingsGraph())

        stack.replaceAll(newRoutes)

        assertEquals(1, stack.size)
        assertEquals(SettingsRoute.SettingsGraph(), stack[0])
    }

    @Test
    fun `replaceAll with longer list appends new elements`() {
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph)
        val newRoutes = listOf<NavKey>(NodesRoute.NodesGraph, NodesRoute.Nodes, NodesRoute.NodeDetail(destNum = 99))

        stack.replaceAll(newRoutes)

        assertEquals(newRoutes, stack)
    }

    @Test
    fun `replaceAll with empty list clears the stack`() {
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph, NodesRoute.Nodes)

        stack.replaceAll(emptyList())

        assertEquals(0, stack.size)
    }

    @Test
    fun `replaceAll on empty stack with new routes populates it`() {
        val stack = mutableListOf<NavKey>()
        val newRoutes = listOf<NavKey>(ContactsRoute.ContactsGraph, ContactsRoute.Contacts)

        stack.replaceAll(newRoutes)

        assertEquals(newRoutes, stack)
    }

    @Test
    fun `replaceAll with identical routes does not mutate entries`() {
        val routes = listOf<NavKey>(NodesRoute.NodesGraph, NodesRoute.Nodes)
        val stack = routes.toMutableList()

        stack.replaceAll(routes)

        assertEquals(routes, stack)
    }

    @Test
    fun `replaceAll with partial overlap only changes differing elements`() {
        val stack = mutableListOf<NavKey>(NodesRoute.NodesGraph, NodesRoute.Nodes, NodesRoute.NodeDetail(destNum = 1))
        val newRoutes =
            listOf<NavKey>(
                NodesRoute.NodesGraph, // same
                SettingsRoute.About, // different
            )

        stack.replaceAll(newRoutes)

        assertEquals(2, stack.size)
        assertEquals(NodesRoute.NodesGraph, stack[0])
        assertEquals(SettingsRoute.About, stack[1])
    }

    // endregion
}
