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

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiBackstackTest {

    private fun createMultiBackstack(startTab: NavKey): MultiBackstack {
        val tabRoute = TopLevelDestination.fromNavKey(startTab)?.route ?: startTab
        return MultiBackstack(startTab, mutableStateOf(tabRoute))
    }

    @Test
    fun `navigateTopLevel to different tab preserves previous tab stack and activates new tab stack`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = createMultiBackstack(startTab)

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
        val multiBackstack = createMultiBackstack(startTab)

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
        val multiBackstack = createMultiBackstack(startTab)

        val nodesStack =
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoute.Nodes)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Nodes.route to nodesStack)

        multiBackstack.goBack()

        assertEquals(1, multiBackstack.activeBackStack.size)
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.activeBackStack.first())
    }

    @Test
    fun `goBack on root of non-start tab returns to start tab`() {
        val startTab = TopLevelDestination.Connect.route
        val multiBackstack = createMultiBackstack(startTab)

        val mapStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Map.route)) }
        val connectStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Connect.route)) }

        multiBackstack.backStacks =
            mapOf(TopLevelDestination.Map.route to mapStack, TopLevelDestination.Connect.route to connectStack)

        multiBackstack.navigateTopLevel(TopLevelDestination.Map.route)
        assertEquals(TopLevelDestination.Map.route, multiBackstack.currentTabRoute)

        multiBackstack.goBack()

        assertEquals(TopLevelDestination.Connect.route, multiBackstack.currentTabRoute)
    }

    @Test
    fun `handleDeepLink sets target tab and populates stack`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = createMultiBackstack(startTab)

        val settingsStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Settings.route)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Settings.route to settingsStack)

        val deepLinkPath = listOf(TopLevelDestination.Settings.route, SettingsRoute.About)
        multiBackstack.handleDeepLink(deepLinkPath)

        assertEquals(TopLevelDestination.Settings.route, multiBackstack.currentTabRoute)
        assertEquals(2, multiBackstack.activeBackStack.size)
        assertEquals(SettingsRoute.About, multiBackstack.activeBackStack.last())
    }

    @Test
    fun `handleDeepLink to a nested non-tab route pushes onto current tab without crashing`() {
        // Regression: deep-linking to firmware/update (root = FirmwareGraph, which is NOT a top-level
        // tab) used to set currentTabRoute to FirmwareGraph — a key with no backstack — making the
        // activeBackStack getter throw "Stack for FirmwareGraph not found" and crash the app.
        val startTab = TopLevelDestination.Connect.route
        val multiBackstack = createMultiBackstack(startTab)

        val connectStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Connect.route)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Connect.route to connectStack)

        multiBackstack.handleDeepLink(listOf(FirmwareRoute.FirmwareGraph, FirmwareRoute.FirmwareUpdate))

        // currentTabRoute must stay a real tab (not corrupted to FirmwareGraph)
        assertEquals(TopLevelDestination.Connect.route, multiBackstack.currentTabRoute)
        // the firmware routes are pushed onto the current tab's stack, and reading it does not throw
        assertEquals(3, multiBackstack.activeBackStack.size)
        assertEquals(TopLevelDestination.Connect.route, multiBackstack.activeBackStack.first())
        assertEquals(FirmwareRoute.FirmwareUpdate, multiBackstack.activeBackStack.last())
    }

    @Test
    fun `handleDeepLink to unknown nested route on missing tab is a no-op and never throws`() {
        // Defensive: even if the current tab somehow has no backstack, a nested-route deep link must
        // not throw (the getter would). Here backStacks is empty for the current tab.
        val startTab = TopLevelDestination.Connect.route
        val multiBackstack = createMultiBackstack(startTab)
        multiBackstack.backStacks = emptyMap()

        // Should simply do nothing rather than crash.
        multiBackstack.handleDeepLink(listOf(FirmwareRoute.FirmwareGraph, FirmwareRoute.FirmwareUpdate))

        assertEquals(TopLevelDestination.Connect.route, multiBackstack.currentTabRoute)
    }

    @Test
    fun `handleDeepLink from different tab switches tab and sets stack`() {
        // Start on Connect tab
        val startTab = TopLevelDestination.Connect.route
        val multiBackstack = createMultiBackstack(startTab)

        val connectStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Connect.route)) }
        val nodesStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route)) }

        multiBackstack.backStacks =
            mapOf(TopLevelDestination.Connect.route to connectStack, TopLevelDestination.Nodes.route to nodesStack)

        // Verify we start on Connect
        assertEquals(TopLevelDestination.Connect.route, multiBackstack.currentTabRoute)

        // Deep-link to a TracerouteMap on the Nodes tab (this is the exact pattern
        // MeshtasticAppShell uses for traceroute alert "View on Map")
        val tracerouteMap = NodeDetailRoute.TracerouteMap(destNum = 100, requestId = 42, logUuid = "abc")
        multiBackstack.handleDeepLink(listOf(NodesRoute.Nodes, tracerouteMap))

        // Should have switched to the Nodes tab
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.currentTabRoute)
        // Stack should contain the graph root + the traceroute map route
        assertEquals(2, multiBackstack.activeBackStack.size)
        assertEquals(NodesRoute.Nodes, multiBackstack.activeBackStack.first())
        assertEquals(tracerouteMap, multiBackstack.activeBackStack.last())
    }
}
