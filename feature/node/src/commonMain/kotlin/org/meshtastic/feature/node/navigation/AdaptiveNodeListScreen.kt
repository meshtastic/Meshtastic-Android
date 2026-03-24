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

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.ui.component.AdaptiveListDetailScaffold
import org.meshtastic.core.ui.component.EmptyDetailPlaceholder
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.feature.node.compass.CompassViewModel
import org.meshtastic.feature.node.detail.NodeDetailScreen
import org.meshtastic.feature.node.detail.NodeDetailViewModel
import org.meshtastic.feature.node.list.NodeListScreen
import org.meshtastic.feature.node.list.NodeListViewModel

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveNodeListScreen(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    initialNodeId: Int? = null,
    onNavigate: (Route) -> Unit = {},
    onNavigateToMessages: (String) -> Unit = {},
    onHandleDeepLink: (org.meshtastic.core.common.util.MeshtasticUri, onInvalid: () -> Unit) -> Unit = { _, _ -> },
) {
    val nodeListViewModel: NodeListViewModel = koinViewModel()
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()

    val onBackToGraph: () -> Unit = {
        val currentKey = backStack.lastOrNull()
        val isNodesRoute = currentKey is NodesRoutes.Nodes || currentKey is NodesRoutes.NodesGraph
        val previousKey = if (backStack.size > 1) backStack[backStack.size - 2] else null
        val isFromDifferentGraph =
            previousKey != null && previousKey !is NodesRoutes.NodesGraph && previousKey !is NodesRoutes.Nodes

        if (isFromDifferentGraph && !isNodesRoute) {
            // Navigate back via NavController to return to the previous screen
            backStack.removeLastOrNull()
        }
    }

    AdaptiveListDetailScaffold(
        navigator = navigator,
        scrollToTopEvents = scrollToTopEvents,
        onBackToGraph = onBackToGraph,
        onTabPressedEvent = { it is ScrollToTopEvent.NodesTabPressed },
        initialKey = initialNodeId,
        listPane = { isActive, activeNodeId ->
            NodeListScreen(
                viewModel = nodeListViewModel,
                navigateToNodeDetails = { nodeId ->
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, nodeId) }
                },
                onNavigateToChannels = { backStack.add(ChannelsRoutes.ChannelsGraph) },
                scrollToTopEvents = scrollToTopEvents,
                activeNodeId = activeNodeId,
                onHandleDeepLink = onHandleDeepLink,
            )
        },
        detailPane = { contentKey, handleBack ->
            val nodeDetailViewModel: NodeDetailViewModel = koinViewModel()
            val compassViewModel: CompassViewModel = koinViewModel()
            NodeDetailScreen(
                nodeId = contentKey,
                viewModel = nodeDetailViewModel,
                compassViewModel = compassViewModel,
                navigateToMessages = onNavigateToMessages,
                onNavigate = onNavigate,
                onNavigateUp = handleBack,
            )
        },
        emptyDetailPane = {
            EmptyDetailPlaceholder(icon = MeshtasticIcons.Nodes, title = stringResource(Res.string.nodes))
        },
    )
}
