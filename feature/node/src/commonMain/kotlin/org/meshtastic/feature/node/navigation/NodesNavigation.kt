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
package org.meshtastic.feature.node.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PermScanWifi
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Router
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.device
import org.meshtastic.core.resources.environment
import org.meshtastic.core.resources.host
import org.meshtastic.core.resources.neighbor_info
import org.meshtastic.core.resources.pax
import org.meshtastic.core.resources.position_log
import org.meshtastic.core.resources.power
import org.meshtastic.core.resources.signal
import org.meshtastic.core.resources.traceroute
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.node.metrics.DeviceMetricsScreen
import org.meshtastic.feature.node.metrics.EnvironmentMetricsScreen
import org.meshtastic.feature.node.metrics.HostMetricsLogScreen
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.NeighborInfoLogScreen
import org.meshtastic.feature.node.metrics.PaxMetricsScreen
import org.meshtastic.feature.node.metrics.PositionLogScreen
import org.meshtastic.feature.node.metrics.PowerMetricsScreen
import org.meshtastic.feature.node.metrics.SignalMetricsScreen
import org.meshtastic.feature.node.metrics.TracerouteLogScreen
import kotlin.reflect.KClass

@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.nodesGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent> = MutableSharedFlow(),
    onHandleDeepLink: (org.meshtastic.core.common.util.MeshtasticUri, onInvalid: () -> Unit) -> Unit = { _, _ -> },
) {
    entry<NodesRoutes.NodesGraph> {
        AdaptiveNodeListScreen(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            onNavigate = { backStack.add(it) },
            onNavigateToMessages = { backStack.add(ContactsRoutes.Messages(it)) },
            onHandleDeepLink = onHandleDeepLink,
        )
    }

    entry<NodesRoutes.Nodes> {
        AdaptiveNodeListScreen(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            onNavigate = { backStack.add(it) },
            onNavigateToMessages = { backStack.add(ContactsRoutes.Messages(it)) },
            onHandleDeepLink = onHandleDeepLink,
        )
    }

    nodeDetailGraph(backStack, scrollToTopEvents, onHandleDeepLink)
}

@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.nodeDetailGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    onHandleDeepLink: (org.meshtastic.core.common.util.MeshtasticUri, onInvalid: () -> Unit) -> Unit = { _, _ -> },
) {
    entry<NodesRoutes.NodeDetailGraph> { args ->
        AdaptiveNodeListScreen(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            initialNodeId = args.destNum,
            onNavigate = { backStack.add(it) },
            onNavigateToMessages = { backStack.add(ContactsRoutes.Messages(it)) },
            onHandleDeepLink = onHandleDeepLink,
        )
    }

    entry<NodesRoutes.NodeDetail> { args ->
        AdaptiveNodeListScreen(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            initialNodeId = args.destNum,
            onNavigate = { backStack.add(it) },
            onNavigateToMessages = { backStack.add(ContactsRoutes.Messages(it)) },
            onHandleDeepLink = onHandleDeepLink,
        )
    }

    entry<NodeDetailRoutes.NodeMap> { args ->
        val mapScreen = org.meshtastic.core.ui.util.LocalNodeMapScreenProvider.current
        mapScreen(args.destNum) { backStack.removeLastOrNull() }
    }

    entry<NodeDetailRoutes.TracerouteLog> { args ->
        val metricsViewModel =
            koinViewModel<MetricsViewModel>(key = "metrics-${args.destNum}") { parametersOf(args.destNum) }
        metricsViewModel.setNodeId(args.destNum)

        TracerouteLogScreen(
            viewModel = metricsViewModel,
            onNavigateUp = { backStack.removeLastOrNull() },
            onViewOnMap = { requestId, responseLogUuid ->
                backStack.add(
                    NodeDetailRoutes.TracerouteMap(
                        destNum = args.destNum,
                        requestId = requestId,
                        logUuid = responseLogUuid,
                    ),
                )
            },
        )
    }

    entry<NodeDetailRoutes.TracerouteMap> { args ->
        TracerouteMapScreen(
            destNum = args.destNum,
            requestId = args.requestId,
            logUuid = args.logUuid,
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }

    NodeDetailRoute.entries.forEach { routeInfo ->
        when (routeInfo.routeClass) {
            NodeDetailRoutes.DeviceMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.DeviceMetrics>(backStack, routeInfo) { it.destNum }
            NodeDetailRoutes.PositionLog::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.PositionLog>(backStack, routeInfo) { it.destNum }
            NodeDetailRoutes.EnvironmentMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.EnvironmentMetrics>(backStack, routeInfo) { it.destNum }
            NodeDetailRoutes.SignalMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.SignalMetrics>(backStack, routeInfo) { it.destNum }
            NodeDetailRoutes.PowerMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.PowerMetrics>(backStack, routeInfo) { it.destNum }
            NodeDetailRoutes.HostMetricsLog::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.HostMetricsLog>(backStack, routeInfo) { it.destNum }
            NodeDetailRoutes.PaxMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.PaxMetrics>(backStack, routeInfo) { it.destNum }
            NodeDetailRoutes.NeighborInfoLog::class ->
                addNodeDetailScreenComposable<NodeDetailRoutes.NeighborInfoLog>(backStack, routeInfo) { it.destNum }
            else -> Unit
        }
    }
}

fun NavKey.isNodeDetailRoute(): Boolean = NodeDetailRoute.entries.any { this::class == it.routeClass }

private inline fun <reified R : Route> EntryProviderScope<NavKey>.addNodeDetailScreenComposable(
    backStack: NavBackStack<NavKey>,
    routeInfo: NodeDetailRoute,
    crossinline getDestNum: (R) -> Int,
) {
    entry<R> { args ->
        val destNum = getDestNum(args)
        val metricsViewModel = koinViewModel<MetricsViewModel>(key = "metrics-$destNum") { parametersOf(destNum) }
        metricsViewModel.setNodeId(destNum)

        routeInfo.screenComposable(metricsViewModel) { backStack.removeLastOrNull() }
    }
}

/** Expect declaration for the platform-specific traceroute map screen. */
@Composable expect fun TracerouteMapScreen(destNum: Int, requestId: Int, logUuid: String?, onNavigateUp: () -> Unit)

enum class NodeDetailRoute(
    val title: StringResource,
    val routeClass: KClass<out Route>,
    val icon: ImageVector?,
    val screenComposable: @Composable (metricsViewModel: MetricsViewModel, onNavigateUp: () -> Unit) -> Unit,
) {
    DEVICE(
        Res.string.device,
        NodeDetailRoutes.DeviceMetrics::class,
        Icons.Rounded.Router,
        { metricsVM, onNavigateUp -> DeviceMetricsScreen(metricsVM, onNavigateUp) },
    ),
    POSITION_LOG(
        Res.string.position_log,
        NodeDetailRoutes.PositionLog::class,
        Icons.Rounded.LocationOn,
        { metricsVM, onNavigateUp -> PositionLogScreen(metricsVM, onNavigateUp) },
    ),
    ENVIRONMENT(
        Res.string.environment,
        NodeDetailRoutes.EnvironmentMetrics::class,
        Icons.Rounded.LightMode,
        { metricsVM, onNavigateUp -> EnvironmentMetricsScreen(metricsVM, onNavigateUp) },
    ),
    SIGNAL(
        Res.string.signal,
        NodeDetailRoutes.SignalMetrics::class,
        Icons.Rounded.CellTower,
        { metricsVM, onNavigateUp -> SignalMetricsScreen(metricsVM, onNavigateUp) },
    ),
    TRACEROUTE(
        Res.string.traceroute,
        NodeDetailRoutes.TracerouteLog::class,
        Icons.Rounded.PermScanWifi,
        { metricsVM, onNavigateUp -> TracerouteLogScreen(viewModel = metricsVM, onNavigateUp = onNavigateUp) },
    ),
    NEIGHBOR_INFO(
        Res.string.neighbor_info,
        NodeDetailRoutes.NeighborInfoLog::class,
        Icons.Rounded.Groups,
        { metricsVM, onNavigateUp -> NeighborInfoLogScreen(viewModel = metricsVM, onNavigateUp = onNavigateUp) },
    ),
    POWER(
        Res.string.power,
        NodeDetailRoutes.PowerMetrics::class,
        Icons.Rounded.Power,
        { metricsVM, onNavigateUp -> PowerMetricsScreen(metricsVM, onNavigateUp) },
    ),
    HOST(
        Res.string.host,
        NodeDetailRoutes.HostMetricsLog::class,
        Icons.Rounded.Memory,
        { metricsVM, onNavigateUp -> HostMetricsLogScreen(metricsVM, onNavigateUp) },
    ),
    PAX(
        Res.string.pax,
        NodeDetailRoutes.PaxMetrics::class,
        Icons.Rounded.People,
        { metricsVM, onNavigateUp -> PaxMetricsScreen(metricsVM, onNavigateUp) },
    ),
}
