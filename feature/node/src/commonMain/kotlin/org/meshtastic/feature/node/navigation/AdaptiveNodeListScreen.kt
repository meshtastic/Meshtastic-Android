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

import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.NavigationEventInfo
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
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
) {
    val nodeListViewModel: NodeListViewModel = koinViewModel()
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()
    val backNavigationBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange

    val handleBack: () -> Unit = {
        val currentKey = backStack.lastOrNull()
        val isNodesRoute = currentKey is NodesRoutes.Nodes || currentKey is NodesRoutes.NodesGraph
        val previousKey = if (backStack.size > 1) backStack[backStack.size - 2] else null
        val isFromDifferentGraph =
            previousKey != null && previousKey !is NodesRoutes.NodesGraph && previousKey !is NodesRoutes.Nodes

        if (isFromDifferentGraph && !isNodesRoute) {
            // Navigate back via NavController to return to the previous screen
            backStack.removeLastOrNull()
        } else {
            // Close the detail pane within the adaptive scaffold
            scope.launch { navigator.navigateBack(backNavigationBehavior) }
        }
    }

    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navState,
        isBackEnabled = navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail,
        onBackCancelled = { },
        onBackCompleted = { handleBack() }
    )

    LaunchedEffect(initialNodeId) {
        if (initialNodeId != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, initialNodeId)
        }
    }

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents.collect { event ->
            if (
                event is ScrollToTopEvent.NodesTabPressed &&
                navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail
            ) {
                if (navigator.canNavigateBack(backNavigationBehavior)) {
                    navigator.navigateBack(backNavigationBehavior)
                } else {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.List)
                }
            }
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                val focusManager = LocalFocusManager.current
                // Prevent TextFields from auto-focusing when pane animates in
                LaunchedEffect(Unit) { focusManager.clearFocus() }
                NodeListScreen(
                    viewModel = nodeListViewModel,
                    navigateToNodeDetails = { nodeId ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, nodeId) }
                    },
                    onNavigateToChannels = { backStack.add(ChannelsRoutes.ChannelsGraph) },
                    scrollToTopEvents = scrollToTopEvents,
                    activeNodeId = navigator.currentDestination?.contentKey,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val focusManager = LocalFocusManager.current
                // Prevent TextFields from auto-focusing when pane animates in
                navigator.currentDestination?.contentKey?.let { nodeId ->
                    key(nodeId) {
                        LaunchedEffect(nodeId) { focusManager.clearFocus() }
                        val nodeDetailViewModel: NodeDetailViewModel = koinViewModel()
                        val compassViewModel: CompassViewModel = koinViewModel()
                        NodeDetailScreen(
                            nodeId = nodeId,
                            viewModel = nodeDetailViewModel,
                            compassViewModel = compassViewModel,
                            navigateToMessages = onNavigateToMessages,
                            onNavigate = onNavigate,
                            onNavigateUp = handleBack,
                        )
                    }
                } ?: EmptyDetailPlaceholder(icon = MeshtasticIcons.Nodes, title = stringResource(Res.string.nodes))
            }
        },
    )
}
