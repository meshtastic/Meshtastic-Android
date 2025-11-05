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

import androidx.annotation.StringRes
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.feature.map.node.NodeMapScreen
import org.meshtastic.feature.map.node.NodeMapViewModel
import org.meshtastic.feature.node.detail.NodeDetailScreen
import org.meshtastic.feature.node.list.NodeListScreen
import org.meshtastic.feature.node.metrics.DeviceMetricsScreen
import org.meshtastic.feature.node.metrics.EnvironmentMetricsScreen
import org.meshtastic.feature.node.metrics.HostMetricsLogScreen
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.PaxMetricsScreen
import org.meshtastic.feature.node.metrics.PositionLogScreen
import org.meshtastic.feature.node.metrics.PowerMetricsScreen
import org.meshtastic.feature.node.metrics.SignalMetricsScreen
import org.meshtastic.feature.node.metrics.TracerouteLogScreen
import org.meshtastic.core.strings.R as Res

fun NavGraphBuilder.nodesGraph(navController: NavHostController) {
    navigation<NodesRoutes.NodesGraph>(startDestination = NodesRoutes.Nodes) {
        composable<NodesRoutes.Nodes>(
            deepLinks = listOf(navDeepLink<NodesRoutes.Nodes>(basePath = "$DEEP_LINK_BASE_URI/nodes")),
        ) {
            NodeListScreen(navigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) })
        }
        nodeDetailGraph(navController)
    }
}

@Suppress("LongMethod")
fun NavGraphBuilder.nodeDetailGraph(navController: NavHostController) {
    navigation<NodesRoutes.NodeDetailGraph>(startDestination = NodesRoutes.NodeDetail()) {
        composable<NodesRoutes.NodeDetail>(
            deepLinks =
            listOf(
                navDeepLink<NodesRoutes.NodeDetail>( // Handles both /node and /node/{destNum} due to destNum: Int?
                    basePath = "$DEEP_LINK_BASE_URI/node",
                ),
            ),
        ) { backStackEntry ->
            val parentEntry =
                remember(backStackEntry) { navController.getBackStackEntry(NodesRoutes.NodeDetailGraph::class) }
            NodeDetailScreen(
                navigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) },
                onNavigate = { navController.navigate(it) },
                onNavigateUp = { navController.navigateUp() },
                viewModel = hiltViewModel(parentEntry),
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
            NodeMapScreen(
                hiltViewModel<NodeMapViewModel>(parentGraphBackStackEntry),
                onNavigateUp = navController::navigateUp,
            )
        }

        NodeDetailRoute.entries.forEach { entry ->
            when (entry.route) {
                is NodeDetailRoutes.DeviceMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.DeviceMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.PositionLog ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PositionLog>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.EnvironmentMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.EnvironmentMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.SignalMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.SignalMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.PowerMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PowerMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.TracerouteLog ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.TracerouteLog>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.HostMetricsLog ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.HostMetricsLog>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.PaxMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PaxMetrics>(
                        navController,
                        entry,
                        entry.screenComposable,
                    )
                else -> Unit
            }
        }
    }
}

fun NavDestination.isNodeDetailRoute(): Boolean = NodeDetailRoute.entries.any { hasRoute(it.route::class) }

/**
 * Helper to define a composable route for a screen within the node detail graph.
 *
 * This function simplifies adding screens by handling common tasks like:
 * - Setting up deep links based on the [NodeDetailRoute] definition.
 * - Retrieving the parent [NavBackStackEntry] for the [NodesRoutes.NodeDetailGraph].
 * - Providing the [MetricsViewModel] scoped to the parent graph.
 *
 * @param R The type of the [Route] object, must be serializable.
 * @param navController The [NavHostController] for navigation.
 * @param routeInfo The [NodeDetailRoute] enum entry that defines the path and metadata for this route.
 * @param screenContent A lambda that defines the composable content for the screen. It receives the shared
 *   [MetricsViewModel].
 */
private inline fun <reified R : Route> NavGraphBuilder.addNodeDetailScreenComposable(
    navController: NavHostController,
    routeInfo: NodeDetailRoute,
    crossinline screenContent: @Composable (metricsViewModel: MetricsViewModel, onNavigateUp: () -> Unit) -> Unit,
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
        screenContent(metricsViewModel, navController::navigateUp)
    }
}

enum class NodeDetailRoute(
    @StringRes val title: Int,
    val route: Route,
    val icon: ImageVector?,
    val screenComposable: @Composable (metricsViewModel: MetricsViewModel, onNavigateUp: () -> Unit) -> Unit,
) {
    DEVICE(
        Res.string.device,
        NodeDetailRoutes.DeviceMetrics,
        Icons.Default.Router,
        { metricsVM, onNavigateUp -> DeviceMetricsScreen(metricsVM, onNavigateUp) },
    ),
    POSITION_LOG(
        Res.string.position_log,
        NodeDetailRoutes.PositionLog,
        Icons.Default.LocationOn,
        { metricsVM, onNavigateUp -> PositionLogScreen(metricsVM, onNavigateUp) },
    ),
    ENVIRONMENT(
        Res.string.environment,
        NodeDetailRoutes.EnvironmentMetrics,
        Icons.Default.LightMode,
        { metricsVM, onNavigateUp -> EnvironmentMetricsScreen(metricsVM, onNavigateUp) },
    ),
    SIGNAL(
        Res.string.signal,
        NodeDetailRoutes.SignalMetrics,
        Icons.Default.CellTower,
        { metricsVM, onNavigateUp -> SignalMetricsScreen(metricsVM, onNavigateUp) },
    ),
    TRACEROUTE(
        Res.string.traceroute,
        NodeDetailRoutes.TracerouteLog,
        Icons.Default.PermScanWifi,
        { metricsVM, onNavigateUp -> TracerouteLogScreen(viewModel = metricsVM, onNavigateUp = onNavigateUp) },
    ),
    POWER(
        Res.string.power,
        NodeDetailRoutes.PowerMetrics,
        Icons.Default.Power,
        { metricsVM, onNavigateUp -> PowerMetricsScreen(metricsVM, onNavigateUp) },
    ),
    HOST(
        Res.string.host,
        NodeDetailRoutes.HostMetricsLog,
        Icons.Default.Memory,
        { metricsVM, onNavigateUp -> HostMetricsLogScreen(metricsVM, onNavigateUp) },
    ),
    PAX(
        Res.string.pax,
        NodeDetailRoutes.PaxMetrics,
        Icons.Default.People,
        { metricsVM, onNavigateUp -> PaxMetricsScreen(metricsVM, onNavigateUp) },
    ),
}
