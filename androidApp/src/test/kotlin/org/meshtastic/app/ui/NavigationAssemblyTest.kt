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
package org.meshtastic.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
import org.meshtastic.core.ui.util.LocalMapMainScreenProvider
import org.meshtastic.feature.connections.navigation.connectionsGraph
import org.meshtastic.feature.discovery.navigation.discoveryGraph
import org.meshtastic.feature.firmware.navigation.firmwareGraph
import org.meshtastic.feature.map.navigation.mapGraph
import org.meshtastic.feature.messaging.navigation.contactsGraph
import org.meshtastic.feature.node.navigation.nodesGraph
import org.meshtastic.feature.settings.navigation.settingsGraph
import org.meshtastic.feature.settings.radio.channel.channelsGraph
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// Graph assembly only builds entry providers, so a bare Application is enough. Booting
// MeshUtilApplication here leaks its applicationScope launches into the rest of the fork.
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NavigationAssemblyTest {

    @Test
    fun verifyNavigationGraphsAssembleWithoutCrashing() = runComposeUiTest {
        setContent {
            val backStack = rememberNavBackStack(NodesRoute.Nodes)
            entryProvider<NavKey> {
                // contactsGraph.onHandleDeepLink is intentionally mandatory (see contactsGraph kdoc);
                // tests don't run imports, so a no-op handler is sufficient here.
                contactsGraph(backStack, emptyFlow(), onHandleDeepLink = { _, _ -> })
                nodesGraph(backStack = backStack, scrollToTopEvents = emptyFlow())
                mapGraph(backStack)
                channelsGraph(backStack)
                connectionsGraph(backStack)
                discoveryGraph(backStack)
                settingsGraph(backStack) { _ -> error("Settings ViewModel is not composed in this assembly test") }
                firmwareGraph(backStack)
            }
        }
    }

    @Test
    fun mapRouteForwardsWaypointAndSitePlannerNode() = runComposeUiTest {
        val route = MapRoute.Map(waypointId = 42, sitePlannerNodeNum = 8675309)
        var receivedWaypointId: Int? = null
        var receivedSitePlannerNodeNum: Int? = null

        setContent {
            val backStack = rememberNavBackStack(route)
            CompositionLocalProvider(
                LocalMapMainScreenProvider provides
                    { _, _, waypointId, sitePlannerNodeNum ->
                        SideEffect {
                            receivedWaypointId = waypointId
                            receivedSitePlannerNodeNum = sitePlannerNodeNum
                        }
                    },
            ) {
                MeshtasticNavDisplay(
                    backStack = backStack,
                    entryProvider = entryProvider<NavKey> { mapGraph(backStack) },
                )
            }
        }

        waitForIdle()
        runOnIdle {
            assertEquals(route.waypointId, receivedWaypointId)
            assertEquals(route.sitePlannerNodeNum, receivedSitePlannerNodeNum)
        }
    }

    @Test
    fun mapRouteEffectRestartsWhenEntryReturnsFromBackStack() = runComposeUiTest {
        val route = MapRoute.Map(sitePlannerNodeNum = 8675309)
        lateinit var backStack: NavBackStack<NavKey>
        var effectStarts = 0
        var disposals = 0

        setContent {
            backStack = rememberNavBackStack(route)
            CompositionLocalProvider(
                LocalMapMainScreenProvider provides
                    { _, _, _, sitePlannerNodeNum ->
                        LaunchedEffect(sitePlannerNodeNum) { effectStarts += 1 }
                        DisposableEffect(Unit) { onDispose { disposals += 1 } }
                    },
            ) {
                MeshtasticNavDisplay(
                    backStack = backStack,
                    entryProvider =
                    entryProvider<NavKey> {
                        mapGraph(backStack)
                        // MapRoute has no list-pane metadata, so the adaptive strategy cannot pair it with the
                        // detail.
                        entry<NodesRoute.NodeDetail> {}
                    },
                )
            }
        }

        waitForIdle()
        runOnIdle {
            assertEquals(1, effectStarts)
            assertEquals(0, disposals)
            backStack.add(NodesRoute.NodeDetail(destNum = 11))
        }

        waitForIdle()
        runOnIdle {
            assertEquals(1, disposals)
            backStack.removeLastOrNull()
        }

        waitForIdle()
        runOnIdle {
            assertEquals(2, effectStarts)
            assertEquals(1, disposals)
        }
    }
}
