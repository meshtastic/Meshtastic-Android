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

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiBackstackTest {

    @Test
    fun `navigateTopLevel to different tab preserves previous tab stack and activates new tab stack`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val nodesStack =
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoute.Nodes)) }
        val mapStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Map.route)) }

        multiBackstack.backStacks =
            mapOf(TopLevelDestination.Nodes.route to nodesStack, TopLevelDestination.Map.route to mapStack)

        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.currentTabRoute)
        assertEquals(2, multiBackstack.activeBackStack.size)

        multiBackstack.navigateTopLevel(TopLevelDestination.Map.route)

        assertEquals(TopLevelDestination.Map.route, multiBackstack.currentTabRoute)
        assertEquals(1, multiBackstack.activeBackStack.size)
        assertEquals(2, nodesStack.size)
    }

    @Test
    fun `navigateTopLevel to same tab resets stack to root`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val nodesStack =
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoute.Nodes)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Nodes.route to nodesStack)

        assertEquals(2, multiBackstack.activeBackStack.size)

        multiBackstack.navigateTopLevel(TopLevelDestination.Nodes.route)

        assertEquals(1, multiBackstack.activeBackStack.size)
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.activeBackStack.first())
    }

    @Test
    fun `goBack pops current stack if size is greater than 1`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val nodesStack =
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoute.Nodes)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Nodes.route to nodesStack)

        multiBackstack.goBack()

        assertEquals(1, multiBackstack.activeBackStack.size)
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.activeBackStack.first())
    }

    @Test
    fun `goBack on root of non-start tab returns to start tab`() {
        val startTab = TopLevelDestination.Connections.route
        val multiBackstack = MultiBackstack(startTab)

        val mapStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Map.route)) }
        val connectionsStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Connections.route)) }

        multiBackstack.backStacks =
            mapOf(TopLevelDestination.Map.route to mapStack, TopLevelDestination.Connections.route to connectionsStack)

        multiBackstack.navigateTopLevel(TopLevelDestination.Map.route)
        assertEquals(TopLevelDestination.Map.route, multiBackstack.currentTabRoute)

        multiBackstack.goBack()

        assertEquals(TopLevelDestination.Connections.route, multiBackstack.currentTabRoute)
    }

    @Test
    fun `handleDeepLink sets target tab and populates stack`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val settingsStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Settings.route)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Settings.route to settingsStack)

        val deepLinkPath = listOf(TopLevelDestination.Settings.route, SettingsRoute.About)
        multiBackstack.handleDeepLink(deepLinkPath)

        assertEquals(TopLevelDestination.Settings.route, multiBackstack.currentTabRoute)
        assertEquals(2, multiBackstack.activeBackStack.size)
        assertEquals(SettingsRoute.About, multiBackstack.activeBackStack.last())
    }

    @Test
    fun `handleDeepLink from different tab switches tab and sets stack`() {
        // Start on Connections tab
        val startTab = TopLevelDestination.Connections.route
        val multiBackstack = MultiBackstack(startTab)

        val connectionsStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Connections.route)) }
        val nodesStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route)) }

        multiBackstack.backStacks =
            mapOf(
                TopLevelDestination.Connections.route to connectionsStack,
                TopLevelDestination.Nodes.route to nodesStack,
            )

        // Verify we start on Connections
        assertEquals(TopLevelDestination.Connections.route, multiBackstack.currentTabRoute)

        // Deep-link to a TracerouteMap on the Nodes tab (this is the exact pattern
        // MeshtasticAppShell uses for traceroute alert "View on Map")
        val tracerouteMap = NodeDetailRoute.TracerouteMap(destNum = 100, requestId = 42, logUuid = "abc")
        multiBackstack.handleDeepLink(listOf(NodesRoute.NodesGraph, tracerouteMap))

        // Should have switched to the Nodes tab
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.currentTabRoute)
        // Stack should contain the graph root + the traceroute map route
        assertEquals(2, multiBackstack.activeBackStack.size)
        assertEquals(NodesRoute.NodesGraph, multiBackstack.activeBackStack.first())
        assertEquals(tracerouteMap, multiBackstack.activeBackStack.last())
    }
}
