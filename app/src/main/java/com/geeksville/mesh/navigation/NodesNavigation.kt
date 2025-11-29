/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PermScanWifi
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.geeksville.mesh.ui.node.AdaptiveNodeListScreen
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.device
import org.meshtastic.core.strings.environment
import org.meshtastic.core.strings.host
import org.meshtastic.core.strings.pax
import org.meshtastic.core.strings.position_log
import org.meshtastic.core.strings.power
import org.meshtastic.core.strings.signal
import org.meshtastic.core.strings.traceroute
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.map.node.NodeMapScreen
import org.meshtastic.feature.map.node.NodeMapViewModel
import org.meshtastic.feature.node.metrics.DeviceMetricsScreen
import org.meshtastic.feature.node.metrics.EnvironmentMetricsScreen
import org.meshtastic.feature.node.metrics.HostMetricsLogScreen
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.PaxMetricsScreen
import org.meshtastic.feature.node.metrics.PositionLogScreen
import org.meshtastic.feature.node.metrics.PowerMetricsScreen
import org.meshtastic.feature.node.metrics.SignalMetricsScreen
import org.meshtastic.feature.node.metrics.TracerouteLogScreen
import kotlin.reflect.KClass

fun NavGraphBuilder.nodesGraph(navController: NavHostController, scrollToTopEvents: Flow<ScrollToTopEvent>) {
    navigation<NodesRoutes.NodesGraph>(startDestination = NodesRoutes.Nodes) {
        composable<NodesRoutes.Nodes>(
            deepLinks = listOf(navDeepLink<NodesRoutes.Nodes>(basePath = "$DEEP_LINK_BASE_URI/nodes")),
        ) {
            AdaptiveNodeListScreen(
                navController = navController,
                scrollToTopEvents = scrollToTopEvents,
                onNavigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) },
            )
        }
        nodeDetailGraph(navController, scrollToTopEvents)
    }
}

@Suppress("LongMethod")
fun NavGraphBuilder.nodeDetailGraph(navController: NavHostController, scrollToTopEvents: Flow<ScrollToTopEvent>) {
    // We keep this route for deep linking or direct navigation to details,
    // but typically users will navigate via the Adaptive screen in NodesRoutes.Nodes
    navigation<NodesRoutes.NodeDetailGraph>(startDestination = NodesRoutes.NodeDetail()) {
        composable<NodesRoutes.NodeDetail>(
            deepLinks =
            listOf(
                navDeepLink<NodesRoutes.NodeDetail>( // Handles both /node and /node/{destNum} due to destNum: Int?
                    basePath = "$DEEP_LINK_BASE_URI/node",
                ),
            ),
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<NodesRoutes.NodeDetail>()
            // When navigating directly to NodeDetail (e.g. from Map or deep link),
            // we use the Adaptive screen initialized with the specific node ID.
            AdaptiveNodeListScreen(
                navController = navController,
                scrollToTopEvents = scrollToTopEvents,
                initialNodeId = args.destNum,
                onNavigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) },
            )
        }

        composable<NodeDetailRoutes.NodeMap>(
            deepLinks =
            listOf(
                navDeepLink<NodeDetailRoutes.NodeMap>(basePath = "$DEEP_LINK_BASE_URI/node/{destNum}/node_map"),
                navDeepLink<NodeDetailRoutes.NodeMap>(basePath = "$DEEP_LINK_BASE_URI/node/node_map"),
            ),
        ) { backStackEntry ->
            val parentGraphBackStackEntry =
                remember(backStackEntry) { navController.getBackStackEntry(NodesRoutes.NodeDetailGraph::class) }
            val vm = hiltViewModel<NodeMapViewModel>(parentGraphBackStackEntry)
            NodeMapScreen(vm, onNavigateUp = navController::navigateUp)
        }

        NodeDetailRoute.entries.forEach { entry ->
            when (entry.routeClass) {
                NodeDetailRoutes.DeviceMetrics::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.DeviceMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                NodeDetailRoutes.PositionLog::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PositionLog>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                NodeDetailRoutes.EnvironmentMetrics::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.EnvironmentMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                NodeDetailRoutes.SignalMetrics::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.SignalMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                NodeDetailRoutes.PowerMetrics::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PowerMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                NodeDetailRoutes.TracerouteLog::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.TracerouteLog>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                NodeDetailRoutes.HostMetricsLog::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.HostMetricsLog>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                NodeDetailRoutes.PaxMetrics::class ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PaxMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    ) {
                        it.destNum
                    }
                else -> Unit
            }
        }
    }
}

fun NavDestination.isNodeDetailRoute(): Boolean = NodeDetailRoute.entries.any { hasRoute(it.routeClass) }

/**
 * Helper to define a composable route for a screen within the node detail graph.
 *
 * @param R The type of the [Route] object, must be serializable.
 * @param navController The [NavHostController] for navigation.
 * @param routeInfo The [NodeDetailRoute] enum entry that defines the path and metadata for this route.
 * @param screenContent A lambda that defines the composable content for the screen.
 * @param getDestNum A lambda to extract the destination number from the route arguments.
 */
private inline fun <reified R : Route> NavGraphBuilder.addNodeDetailScreenComposable(
    navController: NavHostController,
    routeInfo: NodeDetailRoute,
    crossinline screenContent: @Composable (metricsViewModel: MetricsViewModel, onNavigateUp: () -> Unit) -> Unit,
    crossinline getDestNum: (R) -> Int,
) {
    composable<R>(
        deepLinks =
        listOf(
            navDeepLink<R>(basePath = "$DEEP_LINK_BASE_URI/node/{destNum}/${routeInfo.name.lowercase()}"),
            navDeepLink<R>(basePath = "$DEEP_LINK_BASE_URI/node/${routeInfo.name.lowercase()}"),
        ),
    ) { backStackEntry ->
        val parentGraphBackStackEntry =
            remember(backStackEntry) { navController.getBackStackEntry(NodesRoutes.NodeDetailGraph::class) }
        val metricsViewModel = hiltViewModel<MetricsViewModel>(parentGraphBackStackEntry)

        val args = backStackEntry.toRoute<R>()
        val destNum = getDestNum(args)
        metricsViewModel.setNodeId(destNum)

        screenContent(metricsViewModel, navController::navigateUp)
    }
}

enum class NodeDetailRoute(
    val title: StringResource,
    val routeClass: KClass<out Route>,
    val icon: ImageVector?,
    val screenComposable: @Composable (metricsViewModel: MetricsViewModel, onNavigateUp: () -> Unit) -> Unit,
) {
    DEVICE(
        Res.string.device,
        NodeDetailRoutes.DeviceMetrics::class,
        Icons.Default.Router,
        { metricsVM, onNavigateUp -> DeviceMetricsScreen(metricsVM, onNavigateUp) },
    ),
    POSITION_LOG(
        Res.string.position_log,
        NodeDetailRoutes.PositionLog::class,
        Icons.Default.LocationOn,
        { metricsVM, onNavigateUp -> PositionLogScreen(metricsVM, onNavigateUp) },
    ),
    ENVIRONMENT(
        Res.string.environment,
        NodeDetailRoutes.EnvironmentMetrics::class,
        Icons.Default.LightMode,
        { metricsVM, onNavigateUp -> EnvironmentMetricsScreen(metricsVM, onNavigateUp) },
    ),
    SIGNAL(
        Res.string.signal,
        NodeDetailRoutes.SignalMetrics::class,
        Icons.Default.CellTower,
        { metricsVM, onNavigateUp -> SignalMetricsScreen(metricsVM, onNavigateUp) },
    ),
    TRACEROUTE(
        Res.string.traceroute,
        NodeDetailRoutes.TracerouteLog::class,
        Icons.Default.PermScanWifi,
        { metricsVM, onNavigateUp -> TracerouteLogScreen(viewModel = metricsVM, onNavigateUp = onNavigateUp) },
    ),
    POWER(
        Res.string.power,
        NodeDetailRoutes.PowerMetrics::class,
        Icons.Default.Power,
        { metricsVM, onNavigateUp -> PowerMetricsScreen(metricsVM, onNavigateUp) },
    ),
    HOST(
        Res.string.host,
        NodeDetailRoutes.HostMetricsLog::class,
        Icons.Default.Memory,
        { metricsVM, onNavigateUp -> HostMetricsLogScreen(metricsVM, onNavigateUp) },
    ),
    PAX(
        Res.string.pax,
        NodeDetailRoutes.PaxMetrics::class,
        Icons.Default.People,
        { metricsVM, onNavigateUp -> PaxMetricsScreen(metricsVM, onNavigateUp) },
    ),
}
