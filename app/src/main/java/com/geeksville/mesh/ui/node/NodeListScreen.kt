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

package com.geeksville.mesh.ui.node

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.AddContactFAB
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.rememberTimeTickWithLifecycle
import org.meshtastic.core.ui.component.supportsQrCodeSharing
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.feature.node.component.NodeActionDialogs
import org.meshtastic.feature.node.component.NodeFilterTextField
import org.meshtastic.feature.node.component.NodeItem
import org.meshtastic.feature.node.list.NodeListViewModel
import org.meshtastic.proto.AdminProtos

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeListScreen(viewModel: NodeListViewModel = hiltViewModel(), navigateToNodeDetails: (Int) -> Unit) {
    val state by viewModel.nodesUiState.collectAsStateWithLifecycle()

    val nodes by viewModel.nodeList.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val onlineNodeCount by viewModel.onlineNodeCount.collectAsStateWithLifecycle(0)
    val totalNodeCount by viewModel.totalNodeCount.collectAsStateWithLifecycle(0)
    val unfilteredNodes by viewModel.unfilteredNodeList.collectAsStateWithLifecycle()
    val ignoredNodeCount = unfilteredNodes.count { it.isIgnored }

    val listState = rememberLazyListState()

    val currentTimeMillis = rememberTimeTickWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    val isScrollInProgress by remember {
        derivedStateOf { listState.isScrollInProgress && (listState.canScrollForward || listState.canScrollBackward) }
    }
    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(R.string.nodes),
                subtitle = stringResource(R.string.node_count_template, onlineNodeCount, totalNodeCount),
                ourNode = ourNode,
                showNodeChip = false,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = {},
            )
        },
        floatingActionButton = {
            val firmwareVersion = DeviceVersion(ourNode?.metadata?.firmwareVersion ?: "0.0.0")
            val shareCapable = firmwareVersion.supportsQrCodeSharing()
            val sharedContact: AdminProtos.SharedContact? by
                viewModel.sharedContactRequested.collectAsStateWithLifecycle(null)
            AddContactFAB(
                sharedContact = sharedContact,
                modifier =
                Modifier.animateFloatingActionButton(
                    visible = !isScrollInProgress && connectionState == ConnectionState.CONNECTED && shareCapable,
                    alignment = Alignment.BottomEnd,
                ),
                onSharedContactRequested = { contact -> viewModel.setSharedContactRequested(contact) },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
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
                        onTextChange = viewModel::setNodeFilterText,
                        currentSortOption = state.sort,
                        onSortSelect = viewModel::setSortOption,
                        includeUnknown = state.filter.includeUnknown,
                        onToggleIncludeUnknown = viewModel::toggleIncludeUnknown,
                        onlyOnline = state.filter.onlyOnline,
                        onToggleOnlyOnline = viewModel::toggleOnlyOnline,
                        onlyDirect = state.filter.onlyDirect,
                        onToggleOnlyDirect = viewModel::toggleOnlyDirect,
                        showIgnored = state.filter.showIgnored,
                        onToggleShowIgnored = viewModel::toggleShowIgnored,
                        ignoredNodeCount = ignoredNodeCount,
                    )
                }

                items(nodes, key = { it.num }) { node ->
                    var displayFavoriteDialog by remember { mutableStateOf(false) }
                    var displayIgnoreDialog by remember { mutableStateOf(false) }
                    var displayRemoveDialog by remember { mutableStateOf(false) }

                    NodeActionDialogs(
                        node = node,
                        displayFavoriteDialog = displayFavoriteDialog,
                        displayIgnoreDialog = displayIgnoreDialog,
                        displayRemoveDialog = displayRemoveDialog,
                        onDismissMenuRequest = {
                            displayFavoriteDialog = false
                            displayIgnoreDialog = false
                            displayRemoveDialog = false
                        },
                        onConfirmFavorite = viewModel::favoriteNode,
                        onConfirmIgnore = viewModel::ignoreNode,
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

                        NodeItem(
                            modifier = Modifier.animateItem(),
                            thisNode = ourNode,
                            thatNode = node,
                            distanceUnits = state.distanceUnits,
                            tempInFahrenheit = state.tempInFahrenheit,
                            onClick = { navigateToNodeDetails(node.num) },
                            onLongClick = longClick,
                            currentTimeMillis = currentTimeMillis,
                            isConnected = connectionState.isConnected(),
                        )
                        val isThisNode = remember(node) { ourNode?.num == node.num }
                        if (!isThisNode) {
                            ContextMenu(
                                expanded = expanded,
                                node = node,
                                onClickFavorite = { displayFavoriteDialog = true },
                                onClickIgnore = { displayIgnoreDialog = true },
                                onClickRemove = { displayRemoveDialog = true },
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
    onClickFavorite: (Node) -> Unit,
    onClickIgnore: (Node) -> Unit,
    onClickRemove: (Node) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val isFavorite = node.isFavorite
        val isIgnored = node.isIgnored

        DropdownMenuItem(
            onClick = {
                onClickFavorite(node)
                onDismiss()
            },
            enabled = !isIgnored,
            leadingIcon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite)) },
        )

        DropdownMenuItem(
            onClick = {
                onClickIgnore(node)
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
                    text = stringResource(if (isIgnored) R.string.remove_ignored else R.string.ignore),
                    color = MaterialTheme.colorScheme.StatusRed,
                )
            },
        )

        DropdownMenuItem(
            onClick = {
                onClickRemove(node)
                onDismiss()
            },
            enabled = !isIgnored,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = if (isIgnored) LocalContentColor.current else MaterialTheme.colorScheme.StatusRed,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.remove),
                    color = if (isIgnored) Color.Unspecified else MaterialTheme.colorScheme.StatusRed,
                )
            },
        )
    }
}
