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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.geeksville.mesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.metrics.DeviceMetricsScreen
import com.geeksville.mesh.ui.metrics.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.metrics.HostMetricsLogScreen
import com.geeksville.mesh.ui.metrics.PaxMetricsScreen
import com.geeksville.mesh.ui.metrics.PositionLogScreen
import com.geeksville.mesh.ui.metrics.PowerMetricsScreen
import com.geeksville.mesh.ui.metrics.SignalMetricsScreen
import com.geeksville.mesh.ui.metrics.TracerouteLogScreen
import com.geeksville.mesh.ui.node.NodeDetailScreen
import com.geeksville.mesh.ui.node.NodeMapScreen
import com.geeksville.mesh.ui.node.NodeScreen
import kotlinx.serialization.Serializable

sealed class NodesRoutes {
    @Serializable data object Nodes : Route

    @Serializable data object NodesGraph : Graph

    @Serializable data class NodeDetailGraph(val destNum: Int? = null) : Graph

    @Serializable data class NodeDetail(val destNum: Int? = null) : Route
}

sealed class NodeDetailRoutes {
    @Serializable data object DeviceMetrics : Route

    @Serializable data object NodeMap : Route

    @Serializable data object PositionLog : Route

    @Serializable data object EnvironmentMetrics : Route

    @Serializable data object SignalMetrics : Route

    @Serializable data object PowerMetrics : Route

    @Serializable data object TracerouteLog : Route

    @Serializable data object HostMetricsLog : Route

    @Serializable data object PaxMetrics : Route
}

fun NavGraphBuilder.nodesGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    navigation<NodesRoutes.NodesGraph>(startDestination = NodesRoutes.Nodes) {
        composable<NodesRoutes.Nodes>(
            deepLinks = listOf(navDeepLink<NodesRoutes.Nodes>(basePath = "$DEEP_LINK_BASE_URI/nodes")),
        ) {
            NodeScreen(
                model = uiViewModel,
                navigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) },
                navigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
            )
        }
        nodeDetailGraph(navController, uiViewModel)
    }
}

@Suppress("LongMethod")
fun NavGraphBuilder.nodeDetailGraph(navController: NavHostController, uiViewModel: UIViewModel) {
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
                uiViewModel = uiViewModel,
                navigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) },
                onNavigate = { navController.navigate(it) },
                onNavigateUp = { navController.navigateUp() },
                viewModel = hiltViewModel(parentEntry),
            )
        }

        NodeDetailRoute.entries.forEach { entry ->
            when (entry.route) {
                is NodeDetailRoutes.DeviceMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.DeviceMetrics>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.NodeMap ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.NodeMap>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.PositionLog ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PositionLog>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.EnvironmentMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.EnvironmentMetrics>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.SignalMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.SignalMetrics>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.PowerMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PowerMetrics>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.TracerouteLog ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.TracerouteLog>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.HostMetricsLog ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.HostMetricsLog>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                is NodeDetailRoutes.PaxMetrics ->
                    addNodeDetailScreenComposable<NodeDetailRoutes.PaxMetrics>(
                        navController,
                        uiViewModel,
                        entry,
                        entry.screenComposable,
                    )
                else -> Unit
            }
        }
    }
}

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
 * @param uiViewModel The shared [UIViewModel], passed to the [screenContent].
 * @param routeInfo The [NodeDetailRoute] enum entry that defines the path and metadata for this route.
 * @param screenContent A lambda that defines the composable content for the screen. It receives the shared
 *   [MetricsViewModel] and the [UIViewModel].
 */
private inline fun <reified R : Route> NavGraphBuilder.addNodeDetailScreenComposable(
    navController: NavHostController,
    uiViewModel: UIViewModel,
    routeInfo: NodeDetailRoute,
    crossinline screenContent: @Composable (metricsViewModel: MetricsViewModel, passedUiViewModel: UIViewModel) -> Unit,
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
        screenContent(metricsViewModel, uiViewModel)
    }
}

enum class NodeDetailRoute(
    @StringRes val title: Int,
    val route: Route,
    val icon: ImageVector?,
    val screenComposable: @Composable (metricsViewModel: MetricsViewModel, uiViewModel: UIViewModel) -> Unit,
) {
    DEVICE(
        R.string.device,
        NodeDetailRoutes.DeviceMetrics,
        Icons.Default.Router,
        { metricsVM, _ -> DeviceMetricsScreen(metricsVM) },
    ),
    NODE_MAP(
        R.string.node_map,
        NodeDetailRoutes.NodeMap,
        Icons.Default.LocationOn,
        { metricsVM, uiVM -> NodeMapScreen(uiVM, metricsVM) },
    ),
    POSITION_LOG(
        R.string.position_log,
        NodeDetailRoutes.PositionLog,
        Icons.Default.LocationOn,
        { metricsVM, _ -> PositionLogScreen(metricsVM) },
    ),
    ENVIRONMENT(
        R.string.environment,
        NodeDetailRoutes.EnvironmentMetrics,
        Icons.Default.LightMode,
        { metricsVM, _ -> EnvironmentMetricsScreen(metricsVM) },
    ),
    SIGNAL(
        R.string.signal,
        NodeDetailRoutes.SignalMetrics,
        Icons.Default.CellTower,
        { metricsVM, _ -> SignalMetricsScreen(metricsVM) },
    ),
    TRACEROUTE(
        R.string.traceroute,
        NodeDetailRoutes.TracerouteLog,
        Icons.Default.PermScanWifi,
        { metricsVM, _ -> TracerouteLogScreen(viewModel = metricsVM) },
    ),
    POWER(
        R.string.power,
        NodeDetailRoutes.PowerMetrics,
        Icons.Default.Power,
        { metricsVM, _ -> PowerMetricsScreen(metricsVM) },
    ),
    HOST(
        R.string.host,
        NodeDetailRoutes.HostMetricsLog,
        Icons.Default.Memory,
        { metricsVM, _ -> HostMetricsLogScreen(metricsVM) },
    ),
    PAX(
        R.string.pax,
        NodeDetailRoutes.PaxMetrics,
        Icons.Default.People,
        { metricsVM, _ -> PaxMetricsScreen(metricsVM) },
    ),
}
