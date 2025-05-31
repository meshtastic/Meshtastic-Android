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
import androidx.compose.material.icons.filled.PermScanWifi
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.metrics.DeviceMetricsScreen
import com.geeksville.mesh.ui.metrics.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.metrics.HostMetricsLogScreen
import com.geeksville.mesh.ui.metrics.PositionLogScreen
import com.geeksville.mesh.ui.metrics.PowerMetricsScreen
import com.geeksville.mesh.ui.metrics.SignalMetricsScreen
import com.geeksville.mesh.ui.metrics.TracerouteLogScreen
import com.geeksville.mesh.ui.node.NodeDetailScreen
import com.geeksville.mesh.ui.node.NodeMapScreen

fun NavGraphBuilder.nodeDetailGraph(
    navController: NavHostController,
    uiViewModel: UIViewModel,
) {
    navigation<Graph.NodeDetailGraph>(
        startDestination = Route.NodeDetail(),
    ) {
        composable<Route.NodeDetail> { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<Graph.NodeDetailGraph>()
            }
            NodeDetailScreen(
                uiViewModel = uiViewModel,
                navigateToMessages = {
                    navController.navigate(Route.Messages(it)) {
                        popUpTo(Route.NodeDetail()) {
                            inclusive = false
                        }
                    }
                },
                onNavigate = {
                    navController.navigate(it) {
                        popUpTo(Route.NodeDetail()) {
                            inclusive = false
                        }
                    }
                },
                viewModel = hiltViewModel(parentEntry),
            )
        }
        NodeDetailRoute.entries.forEach { nodeDetailRoute ->
            composable(nodeDetailRoute.route::class) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<Graph.NodeDetailGraph>()
                }
                when (nodeDetailRoute) {
                    NodeDetailRoute.DEVICE -> DeviceMetricsScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.NODE_MAP -> NodeMapScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.POSITION_LOG -> PositionLogScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.ENVIRONMENT -> EnvironmentMetricsScreen(
                        hiltViewModel(
                            parentEntry
                        )
                    )

                    NodeDetailRoute.SIGNAL -> SignalMetricsScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.TRACEROUTE -> TracerouteLogScreen(viewModel = hiltViewModel(parentEntry))
                    NodeDetailRoute.POWER -> PowerMetricsScreen(hiltViewModel(parentEntry))
                    NodeDetailRoute.HOST -> HostMetricsLogScreen(hiltViewModel(parentEntry))
                }
            }
        }
    }
}

enum class NodeDetailRoute(
    @StringRes val title: Int,
    val route: Route,
    val icon: ImageVector?,
) {
    DEVICE(R.string.device, Route.DeviceMetrics, Icons.Default.Router),
    NODE_MAP(R.string.node_map, Route.NodeMap, Icons.Default.LocationOn),
    POSITION_LOG(R.string.position_log, Route.PositionLog, Icons.Default.LocationOn),
    ENVIRONMENT(R.string.environment, Route.EnvironmentMetrics, Icons.Default.LightMode),
    SIGNAL(R.string.signal, Route.SignalMetrics, Icons.Default.CellTower),
    TRACEROUTE(R.string.traceroute, Route.TracerouteLog, Icons.Default.PermScanWifi),
    POWER(R.string.power, Route.PowerMetrics, Icons.Default.Power),
    HOST(R.string.host, Route.HostMetricsLog, Icons.Default.Memory),
}
