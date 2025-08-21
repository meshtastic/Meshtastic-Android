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

import android.content.Intent
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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.geeksville.mesh.R
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
            deepLinks =
            listOf(
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/nodes"
                    action = Intent.ACTION_VIEW
                },
            ),
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

fun NavGraphBuilder.nodeDetailGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    navigation<NodesRoutes.NodeDetailGraph>(startDestination = NodesRoutes.NodeDetail()) {
        composable<NodesRoutes.NodeDetail>(
            deepLinks =
            listOf(
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/node/{destNum}"
                    action = Intent.ACTION_VIEW
                },
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/node"
                    action = Intent.ACTION_VIEW
                },
            ),
        ) { backStackEntry ->
            val parentEntry =
                remember(backStackEntry) {
                    val parentRoute = backStackEntry.destination.parent!!.route!!
                    navController.getBackStackEntry(parentRoute)
                }
            NodeDetailScreen(
                uiViewModel = uiViewModel,
                navigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) },
                onNavigate = { navController.navigate(it) },
                onNavigateUp = { navController.navigateUp() },
                viewModel = hiltViewModel(parentEntry),
            )
        }
        NodeDetailRoute.entries.forEach { nodeDetailRoute ->
            val pathSegment = nodeDetailRoute.name.lowercase()
            composable(
                route = nodeDetailRoute.route::class,
                deepLinks =
                listOf(
                    navDeepLink {
                        uriPattern = "$DEEP_LINK_BASE_URI/node/{destNum}/$pathSegment"
                        action = Intent.ACTION_VIEW
                    },
                    navDeepLink {
                        uriPattern = "$DEEP_LINK_BASE_URI/node/$pathSegment"
                        action = Intent.ACTION_VIEW
                    },
                ),
            ) { backStackEntry ->
                val parentEntry =
                    remember(backStackEntry) {
                        val parentRoute = backStackEntry.destination.parent!!.route!!
                        navController.getBackStackEntry(parentRoute)
                    }
                when (nodeDetailRoute) {
                    NodeDetailRoute.DEVICE -> DeviceMetricsScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.NODE_MAP -> NodeMapScreen(uiViewModel, hiltViewModel(parentEntry))
                    NodeDetailRoute.POSITION_LOG -> PositionLogScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.ENVIRONMENT -> EnvironmentMetricsScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.SIGNAL -> SignalMetricsScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.TRACEROUTE -> TracerouteLogScreen(viewModel = hiltViewModel(parentEntry))
                    NodeDetailRoute.POWER -> PowerMetricsScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.HOST -> HostMetricsLogScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.PAX -> PaxMetricsScreen(hiltViewModel(parentEntry))
                }
            }
        }
    }
}

enum class NodeDetailRoute(@StringRes val title: Int, val route: Route, val icon: ImageVector?) {
    DEVICE(R.string.device, NodeDetailRoutes.DeviceMetrics, Icons.Default.Router),
    NODE_MAP(R.string.node_map, NodeDetailRoutes.NodeMap, Icons.Default.LocationOn),
    POSITION_LOG(R.string.position_log, NodeDetailRoutes.PositionLog, Icons.Default.LocationOn),
    ENVIRONMENT(R.string.environment, NodeDetailRoutes.EnvironmentMetrics, Icons.Default.LightMode),
    SIGNAL(R.string.signal, NodeDetailRoutes.SignalMetrics, Icons.Default.CellTower),
    TRACEROUTE(R.string.traceroute, NodeDetailRoutes.TracerouteLog, Icons.Default.PermScanWifi),
    POWER(R.string.power, NodeDetailRoutes.PowerMetrics, Icons.Default.Power),
    HOST(R.string.host, NodeDetailRoutes.HostMetricsLog, Icons.Default.Memory),
    PAX(R.string.pax, NodeDetailRoutes.PaxMetrics, Icons.Default.People),
}
