/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.desktop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.desktop.ui.map.KmpMapPlaceholder
import org.meshtastic.desktop.ui.nodes.DesktopAdaptiveNodeListScreen
import org.meshtastic.feature.node.list.NodeListViewModel
import org.meshtastic.feature.node.metrics.DeviceMetricsScreen
import org.meshtastic.feature.node.metrics.EnvironmentMetricsScreen
import org.meshtastic.feature.node.metrics.HostMetricsLogScreen
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.NeighborInfoLogScreen
import org.meshtastic.feature.node.metrics.PaxMetricsScreen
import org.meshtastic.feature.node.metrics.PowerMetricsScreen
import org.meshtastic.feature.node.metrics.SignalMetricsScreen
import org.meshtastic.feature.node.metrics.TracerouteLogScreen

/**
 * Registers real node feature composables into the desktop navigation graph.
 *
 * The node list screen uses a desktop-specific adaptive composable with Material 3 Adaptive list-detail scaffolding,
 * backed by shared `NodeListViewModel` and commonMain components. The detail pane shows real shared node detail content
 * from commonMain.
 *
 * Metrics screens (logs + chart-based detail metrics) use shared composables from commonMain with `MetricsViewModel`
 * scoped to the destination node number.
 */
fun EntryProviderScope<NavKey>.desktopNodeGraph(backStack: NavBackStack<NavKey>) {
    entry<NodesRoutes.NodesGraph> {
        val viewModel: NodeListViewModel = koinViewModel()
        DesktopAdaptiveNodeListScreen(viewModel = viewModel, onNavigate = { backStack.add(it) })
    }

    entry<NodesRoutes.Nodes> {
        val viewModel: NodeListViewModel = koinViewModel()
        DesktopAdaptiveNodeListScreen(viewModel = viewModel, onNavigate = { backStack.add(it) })
    }

    // Node detail graph routes open the real shared list-detail screen focused on the requested node.
    entry<NodesRoutes.NodeDetailGraph> { route ->
        val viewModel: NodeListViewModel = koinViewModel()
        DesktopAdaptiveNodeListScreen(
            viewModel = viewModel,
            initialNodeId = route.destNum,
            onNavigate = { backStack.add(it) },
        )
    }

    entry<NodesRoutes.NodeDetail> { route ->
        val viewModel: NodeListViewModel = koinViewModel()
        DesktopAdaptiveNodeListScreen(
            viewModel = viewModel,
            initialNodeId = route.destNum,
            onNavigate = { backStack.add(it) },
        )
    }

    // Traceroute log — real shared screen from commonMain
    desktopMetricsEntry<NodeDetailRoutes.TracerouteLog>(getDestNum = { it.destNum }) { viewModel ->
        TracerouteLogScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }

    // Neighbor info log — real shared screen from commonMain
    desktopMetricsEntry<NodeDetailRoutes.NeighborInfoLog>(getDestNum = { it.destNum }) { viewModel ->
        NeighborInfoLogScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }

    // Host metrics log — real shared screen from commonMain
    desktopMetricsEntry<NodeDetailRoutes.HostMetricsLog>(getDestNum = { it.destNum }) { viewModel ->
        HostMetricsLogScreen(metricsViewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }

    // Chart-based metrics — real shared screens from commonMain
    desktopMetricsEntry<NodeDetailRoutes.DeviceMetrics>(getDestNum = { it.destNum }) { viewModel ->
        DeviceMetricsScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }
    desktopMetricsEntry<NodeDetailRoutes.EnvironmentMetrics>(getDestNum = { it.destNum }) { viewModel ->
        EnvironmentMetricsScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }
    desktopMetricsEntry<NodeDetailRoutes.SignalMetrics>(getDestNum = { it.destNum }) { viewModel ->
        SignalMetricsScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }
    desktopMetricsEntry<NodeDetailRoutes.PowerMetrics>(getDestNum = { it.destNum }) { viewModel ->
        PowerMetricsScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }
    desktopMetricsEntry<NodeDetailRoutes.PaxMetrics>(getDestNum = { it.destNum }) { viewModel ->
        PaxMetricsScreen(metricsViewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }

    // Map-based screens — placeholders (map integration needed)
    entry<NodeDetailRoutes.NodeMap> { route -> KmpMapPlaceholder(title = "Node Map (${route.destNum})") }
    entry<NodeDetailRoutes.TracerouteMap> { KmpMapPlaceholder(title = "Traceroute Map") }
    entry<NodeDetailRoutes.PositionLog> { route -> KmpMapPlaceholder(title = "Position Log (${route.destNum})") }
}

private inline fun <reified R : NavKey> EntryProviderScope<NavKey>.desktopMetricsEntry(
    crossinline getDestNum: (R) -> Int,
    crossinline content: @Composable (MetricsViewModel) -> Unit,
) {
    entry<R> { route ->
        val destNum = getDestNum(route)
        val viewModel: MetricsViewModel = koinViewModel(key = "metrics-$destNum") { parametersOf(destNum) }
        LaunchedEffect(destNum) { viewModel.setNodeId(destNum) }
        content(viewModel)
    }
}
