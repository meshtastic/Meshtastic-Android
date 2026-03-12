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
package org.meshtastic.desktop.ui.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_count_template
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.ui.component.EmptyDetailPlaceholder
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.feature.node.component.NodeContextMenu
import org.meshtastic.feature.node.component.NodeFilterTextField
import org.meshtastic.feature.node.component.NodeItem
import org.meshtastic.feature.node.detail.NodeDetailContent
import org.meshtastic.feature.node.detail.NodeDetailViewModel
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.list.NodeListViewModel
import org.meshtastic.feature.node.model.NodeDetailAction

/**
 * Desktop adaptive node list screen using [ListDetailPaneScaffold] from JetBrains Material 3 Adaptive.
 *
 * On wide screens, the node list is shown on the left and the selected node detail on the right. On narrow screens, the
 * scaffold automatically switches to a single-pane layout.
 *
 * Uses the shared [NodeListViewModel] and commonMain composables ([NodeItem], [NodeFilterTextField], [MainAppBar]). The
 * detail pane renders the shared [NodeDetailContent] from commonMain with the full node detail sections (identity,
 * device actions, position, hardware details, notes, administration). Android-only overlays (compass permissions,
 * bottom sheets) are no-ops on desktop.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod")
@Composable
fun DesktopAdaptiveNodeListScreen(
    viewModel: NodeListViewModel,
    initialNodeId: Int? = null,
    onNavigate: (Route) -> Unit = {},
) {
    val state by viewModel.nodesUiState.collectAsStateWithLifecycle()
    val nodes by viewModel.nodeList.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val onlineNodeCount by viewModel.onlineNodeCount.collectAsStateWithLifecycle(0)
    val totalNodeCount by viewModel.totalNodeCount.collectAsStateWithLifecycle(0)
    val unfilteredNodes by viewModel.unfilteredNodeList.collectAsStateWithLifecycle()
    val ignoredNodeCount = unfilteredNodes.count { it.isIgnored }
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(initialNodeId) {
        initialNodeId?.let { nodeId -> navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, nodeId) }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Scaffold(
                    topBar = {
                        MainAppBar(
                            title = stringResource(Res.string.nodes),
                            subtitle =
                            stringResource(
                                Res.string.node_count_template,
                                onlineNodeCount,
                                nodes.size,
                                totalNodeCount,
                            ),
                            ourNode = ourNode,
                            showNodeChip = false,
                            canNavigateUp = false,
                            onNavigateUp = {},
                            actions = {},
                            onClickChip = {},
                        )
                    },
                ) { contentPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            item {
                                NodeFilterTextField(
                                    modifier =
                                    Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceDim)
                                        .padding(8.dp),
                                    filterText = state.filter.filterText,
                                    onTextChange = { viewModel.nodeFilterText = it },
                                    currentSortOption = state.sort,
                                    onSortSelect = viewModel::setSortOption,
                                    includeUnknown = state.filter.includeUnknown,
                                    onToggleIncludeUnknown = { viewModel.nodeFilterPreferences.toggleIncludeUnknown() },
                                    excludeInfrastructure = state.filter.excludeInfrastructure,
                                    onToggleExcludeInfrastructure = {
                                        viewModel.nodeFilterPreferences.toggleExcludeInfrastructure()
                                    },
                                    onlyOnline = state.filter.onlyOnline,
                                    onToggleOnlyOnline = { viewModel.nodeFilterPreferences.toggleOnlyOnline() },
                                    onlyDirect = state.filter.onlyDirect,
                                    onToggleOnlyDirect = { viewModel.nodeFilterPreferences.toggleOnlyDirect() },
                                    showIgnored = state.filter.showIgnored,
                                    onToggleShowIgnored = { viewModel.nodeFilterPreferences.toggleShowIgnored() },
                                    ignoredNodeCount = ignoredNodeCount,
                                )
                            }

                            items(nodes, key = { it.num }) { node ->
                                var expanded by remember { mutableStateOf(false) }
                                val isActive = navigator.currentDestination?.contentKey == node.num

                                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    val longClick =
                                        if (node.num != ourNode?.num) {
                                            { expanded = true }
                                        } else {
                                            null
                                        }

                                    NodeItem(
                                        thisNode = ourNode,
                                        thatNode = node,
                                        distanceUnits = state.distanceUnits,
                                        tempInFahrenheit = state.tempInFahrenheit,
                                        onClick = {
                                            scope.launch {
                                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, node.num)
                                            }
                                        },
                                        onLongClick = longClick,
                                        connectionState = connectionState,
                                        isActive = isActive,
                                    )

                                    val isThisNode = remember(node) { ourNode?.num == node.num }
                                    if (!isThisNode) {
                                        NodeContextMenu(
                                            expanded = expanded,
                                            node = node,
                                            onFavorite = { viewModel.favoriteNode(node) },
                                            onIgnore = { viewModel.ignoreNode(node) },
                                            onMute = { viewModel.muteNode(node) },
                                            onRemove = { viewModel.removeNode(node) },
                                            onDismiss = { expanded = false },
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { nodeNum ->
                    val detailViewModel: NodeDetailViewModel = koinViewModel(key = "node-detail-$nodeNum")
                    LaunchedEffect(nodeNum) { detailViewModel.start(nodeNum) }
                    val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
                    val snackbarHostState = remember { SnackbarHostState() }

                    LaunchedEffect(Unit) {
                        detailViewModel.effects.collect { effect ->
                            if (effect is NodeRequestEffect.ShowFeedback) {
                                snackbarHostState.showSnackbar(effect.text.resolve())
                            }
                        }
                    }

                    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
                        NodeDetailContent(
                            modifier = Modifier.padding(paddingValues),
                            uiState = detailUiState,
                            onAction = { action ->
                                when (action) {
                                    is NodeDetailAction.Navigate -> onNavigate(action.route)
                                    is NodeDetailAction.TriggerServiceAction ->
                                        detailViewModel.onServiceAction(action.action)
                                    is NodeDetailAction.HandleNodeMenuAction -> {
                                        val menuAction = action.action
                                        if (
                                            menuAction
                                                is org.meshtastic.feature.node.component.NodeMenuAction.DirectMessage
                                        ) {
                                            val routeStr =
                                                detailViewModel.getDirectMessageRoute(
                                                    menuAction.node,
                                                    detailUiState.ourNode,
                                                )
                                            onNavigate(
                                                org.meshtastic.core.navigation.ContactsRoutes.Messages(
                                                    contactKey = routeStr,
                                                ),
                                            )
                                        } else {
                                            detailViewModel.handleNodeMenuAction(menuAction)
                                        }
                                    }
                                    else -> {} // Actions requiring Android APIs are no-ops on desktop
                                }
                            },
                            onFirmwareSelect = { /* Firmware update not available on desktop */ },
                            onSaveNotes = { num, notes -> detailViewModel.setNodeNotes(num, notes) },
                        )
                    }
                } ?: EmptyDetailPlaceholder(icon = MeshtasticIcons.Nodes, title = stringResource(Res.string.nodes))
            }
        },
    )
}
