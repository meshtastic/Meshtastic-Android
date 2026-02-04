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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DoDisturbOn
import androidx.compose.material.icons.outlined.DoDisturbOn
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add_favorite
import org.meshtastic.core.strings.ignore
import org.meshtastic.core.strings.mute_always
import org.meshtastic.core.strings.node_count_template
import org.meshtastic.core.strings.nodes
import org.meshtastic.core.strings.remove
import org.meshtastic.core.strings.remove_favorite
import org.meshtastic.core.strings.remove_ignored
import org.meshtastic.core.strings.unmute
import org.meshtastic.core.ui.component.AddContactFAB
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.component.smartScrollToTop
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.feature.node.component.NodeActionDialogs
import org.meshtastic.feature.node.component.NodeFilterTextField
import org.meshtastic.feature.node.component.NodeItem
import org.meshtastic.proto.SharedContact

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeListScreen(
    navigateToNodeDetails: (Int) -> Unit,
    viewModel: NodeListViewModel = hiltViewModel(),
    scrollToTopEvents: Flow<ScrollToTopEvent>? = null,
    activeNodeId: Int? = null,
) {
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
            AddContactFAB(
                sharedContact = sharedContact,
                modifier =
                Modifier.animateFloatingActionButton(
                    visible = !isScrollInProgress && connectionState == ConnectionState.Connected && shareCapable,
                    alignment = Alignment.BottomEnd,
                ),
                onSharedContactRequested = { contact -> viewModel.setSharedContactRequested(contact) },
            )
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
                    )
                }

                items(nodes, key = { it.num }) { node ->
                    var displayFavoriteDialog by remember { mutableStateOf(false) }
                    var displayIgnoreDialog by remember { mutableStateOf(false) }
                    var displayMuteDialog by remember { mutableStateOf(false) }
                    var displayRemoveDialog by remember { mutableStateOf(false) }

                    NodeActionDialogs(
                        node = node,
                        displayFavoriteDialog = displayFavoriteDialog,
                        displayIgnoreDialog = displayIgnoreDialog,
                        displayMuteDialog = displayMuteDialog,
                        displayRemoveDialog = displayRemoveDialog,
                        onDismissMenuRequest = {
                            displayFavoriteDialog = false
                            displayIgnoreDialog = false
                            displayMuteDialog = false
                            displayRemoveDialog = false
                        },
                        onConfirmFavorite = viewModel::favoriteNode,
                        onConfirmIgnore = viewModel::ignoreNode,
                        onConfirmMute = viewModel::muteNode,
                        onConfirmRemove = { viewModel.removeNode(it.num) },
                    )

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
                            ContextMenu(
                                expanded = expanded,
                                node = node,
                                onFavorite = { displayFavoriteDialog = true },
                                onIgnore = { displayIgnoreDialog = true },
                                onMute = { displayMuteDialog = true },
                                onRemove = { displayRemoveDialog = true },
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

@Composable
private fun ContextMenu(
    expanded: Boolean,
    node: Node,
    onFavorite: () -> Unit,
    onIgnore: () -> Unit,
    onMute: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        FavoriteMenuItem(node, onFavorite, onDismiss)
        IgnoreMenuItem(node, onIgnore, onDismiss)
        if (node.capabilities.canMuteNode) {
            MuteMenuItem(node, onMute, onDismiss)
        }
        RemoveMenuItem(node, onRemove, onDismiss)
    }
}

@Composable
private fun FavoriteMenuItem(node: Node, onFavorite: () -> Unit, onDismiss: () -> Unit) {
    val isFavorite = node.isFavorite
    DropdownMenuItem(
        onClick = {
            onFavorite()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
            )
        },
        text = { Text(stringResource(if (isFavorite) Res.string.remove_favorite else Res.string.add_favorite)) },
    )
}

@Composable
private fun IgnoreMenuItem(node: Node, onIgnore: () -> Unit, onDismiss: () -> Unit) {
    val isIgnored = node.isIgnored
    DropdownMenuItem(
        onClick = {
            onIgnore()
            onDismiss()
        },
        leadingIcon = {
            Icon(
                imageVector = if (isIgnored) Icons.Filled.DoDisturbOn else Icons.Outlined.DoDisturbOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.StatusRed,
            )
        },
        text = {
            Text(
                text = stringResource(if (isIgnored) Res.string.remove_ignored else Res.string.ignore),
                color = MaterialTheme.colorScheme.StatusRed,
            )
        },
    )
}

@Composable
private fun MuteMenuItem(node: Node, onMute: () -> Unit, onDismiss: () -> Unit) {
    val isMuted = node.isMuted
    DropdownMenuItem(
        onClick = {
            onMute()
            onDismiss()
        },
        leadingIcon = {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
            )
        },
        text = { Text(text = stringResource(if (isMuted) Res.string.unmute else Res.string.mute_always)) },
    )
}

@Composable
private fun RemoveMenuItem(node: Node, onRemove: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        onClick = {
            onRemove()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = if (node.isIgnored) LocalContentColor.current else MaterialTheme.colorScheme.StatusRed,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.remove),
                color = if (node.isIgnored) Color.Unspecified else MaterialTheme.colorScheme.StatusRed,
            )
        },
    )
}
