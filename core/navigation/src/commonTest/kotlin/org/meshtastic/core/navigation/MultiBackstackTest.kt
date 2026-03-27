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
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoutes.Nodes)) }
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
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoutes.Nodes)) }
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
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoutes.Nodes)) }
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

        val deepLinkPath = listOf(TopLevelDestination.Settings.route, SettingsRoutes.About)
        multiBackstack.handleDeepLink(deepLinkPath)

        assertEquals(TopLevelDestination.Settings.route, multiBackstack.currentTabRoute)
        assertEquals(2, multiBackstack.activeBackStack.size)
        assertEquals(SettingsRoutes.About, multiBackstack.activeBackStack.last())
    }
}
