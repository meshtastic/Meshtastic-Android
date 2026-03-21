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
@file:Suppress("detekt:ALL")

package org.meshtastic.feature.node.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_invalid
import org.meshtastic.core.resources.node_count_template
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MeshtasticImportFAB
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.component.smartScrollToTop
import org.meshtastic.core.ui.qr.ScannedQrCodeDialog
import org.meshtastic.feature.node.component.NodeContextMenu
import org.meshtastic.feature.node.component.NodeFilterTextField
import org.meshtastic.feature.node.component.NodeItem
import org.meshtastic.proto.SharedContact

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeListScreen(
    navigateToNodeDetails: (Int) -> Unit,
    viewModel: NodeListViewModel,
    onNavigateToChannels: () -> Unit = {},
    scrollToTopEvents: Flow<ScrollToTopEvent>? = null,
    activeNodeId: Int? = null,
) {
    val showToast = org.meshtastic.core.ui.util.rememberShowToastResource()
    val scope = rememberCoroutineScope()
    val state by viewModel.nodesUiState.collectAsStateWithLifecycle()

    val nodes by viewModel.nodeList.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val onlineNodeCount by viewModel.onlineNodeCount.collectAsStateWithLifecycle(0)
    val totalNodeCount by viewModel.totalNodeCount.collectAsStateWithLifecycle(0)
    val unfilteredNodes by viewModel.unfilteredNodeList.collectAsStateWithLifecycle()
    val ignoredNodeCount = unfilteredNodes.count { it.isIgnored }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents?.collectLatest { event ->
            if (event is ScrollToTopEvent.NodesTabPressed) {
                listState.smartScrollToTop(coroutineScope)
            }
        }
    }

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    val isScrollInProgress by remember {
        derivedStateOf { listState.isScrollInProgress && (listState.canScrollForward || listState.canScrollBackward) }
    }

    val requestChannelSet by viewModel.requestChannelSet.collectAsStateWithLifecycle()
    requestChannelSet?.let { ScannedQrCodeDialog(it, onDismiss = { viewModel.clearRequestChannelSet() }) }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.nodes),
                subtitle = stringResource(Res.string.node_count_template, onlineNodeCount, nodes.size, totalNodeCount),
                ourNode = ourNode,
                showNodeChip = false,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = {},
            )
        },
        floatingActionButton = {
            val shareCapable = ourNode?.capabilities?.supportsQrCodeSharing ?: false
            val sharedContact: SharedContact? by viewModel.sharedContactRequested.collectAsStateWithLifecycle(null)
            if (!isScrollInProgress && connectionState == ConnectionState.Connected && shareCapable) {
                MeshtasticImportFAB(
                    sharedContact = sharedContact,
                    onImport = { uriString ->
                        viewModel.handleScannedUri(uriString) { scope.launch { showToast(Res.string.channel_invalid) } }
                    },
                    onDismissSharedContact = { viewModel.setSharedContactRequested(null) },
                    isContactContext = true,
                )
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding).focusable()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                stickyHeader {
                    val animatedAlpha by
                        animateFloatAsState(targetValue = if (!isScrollInProgress) 1.0f else 0f, label = "alpha")
                    NodeFilterTextField(
                        modifier =
                        Modifier.fillMaxWidth()
                            .graphicsLayer(alpha = animatedAlpha)
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
                        excludeMqtt = state.filter.excludeMqtt,
                        onToggleExcludeMqtt = { viewModel.nodeFilterPreferences.toggleExcludeMqtt() },
                    )
                }

                items(nodes, key = { it.num }) { node ->
                    var expanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        val longClick =
                            if (node.num != ourNode?.num) {
                                { expanded = true }
                            } else {
                                null
                            }

                        val isActive = remember(activeNodeId, node.num) { activeNodeId == node.num }

                        NodeItem(
                            modifier = Modifier.animateItem(),
                            thisNode = ourNode,
                            thatNode = node,
                            distanceUnits = state.distanceUnits,
                            tempInFahrenheit = state.tempInFahrenheit,
                            onClick = { navigateToNodeDetails(node.num) },
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
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}
