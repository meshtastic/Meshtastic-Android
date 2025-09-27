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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.service.ConnectionState
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.node.components.NodeFilterTextField
import com.geeksville.mesh.ui.node.components.NodeItem
import com.geeksville.mesh.ui.sharing.AddContactFAB
import com.geeksville.mesh.ui.sharing.supportsQrCodeSharing
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.rememberTimeTickWithLifecycle

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeScreen(nodesViewModel: NodesViewModel = hiltViewModel(), navigateToNodeDetails: (Int) -> Unit) {
    val state by nodesViewModel.nodesUiState.collectAsStateWithLifecycle()

    val nodes by nodesViewModel.nodeList.collectAsStateWithLifecycle()
    val ourNode by nodesViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val onlineNodeCount by nodesViewModel.onlineNodeCount.collectAsStateWithLifecycle(0)
    val totalNodeCount by nodesViewModel.totalNodeCount.collectAsStateWithLifecycle(0)
    val unfilteredNodes by nodesViewModel.unfilteredNodeList.collectAsStateWithLifecycle()
    val ignoredNodeCount = unfilteredNodes.count { it.isIgnored }

    val listState = rememberLazyListState()

    val currentTimeMillis = rememberTimeTickWithLifecycle()
    val connectionState by nodesViewModel.connectionState.collectAsStateWithLifecycle()

    val isScrollInProgress by remember { derivedStateOf { listState.isScrollInProgress } }
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
                onAction = {},
            )
        },
        floatingActionButton = {
            val firmwareVersion = DeviceVersion(ourNode?.metadata?.firmwareVersion ?: "0.0.0")
            val shareCapable = firmwareVersion.supportsQrCodeSharing()
            val scannedContact: AdminProtos.SharedContact? by
                nodesViewModel.sharedContactRequested.collectAsStateWithLifecycle(null)
            AddContactFAB(
                unfilteredNodes = unfilteredNodes,
                scannedContact = scannedContact,
                modifier =
                Modifier.animateFloatingActionButton(
                    visible = !isScrollInProgress && connectionState == ConnectionState.CONNECTED && shareCapable,
                    alignment = Alignment.BottomEnd,
                ),
                onSharedContactImport = { contact -> nodesViewModel.addSharedContact(contact) },
                onSharedContactRequested = { contact -> nodesViewModel.setSharedContactRequested(contact) },
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
                        onTextChange = nodesViewModel::setNodeFilterText,
                        currentSortOption = state.sort,
                        onSortSelect = nodesViewModel::setSortOption,
                        includeUnknown = state.filter.includeUnknown,
                        onToggleIncludeUnknown = nodesViewModel::toggleIncludeUnknown,
                        onlyOnline = state.filter.onlyOnline,
                        onToggleOnlyOnline = nodesViewModel::toggleOnlyOnline,
                        onlyDirect = state.filter.onlyDirect,
                        onToggleOnlyDirect = nodesViewModel::toggleOnlyDirect,
                        showDetails = state.showDetails,
                        onToggleShowDetails = nodesViewModel::toggleShowDetails,
                        showIgnored = state.filter.showIgnored,
                        onToggleShowIgnored = nodesViewModel::toggleShowIgnored,
                        ignoredNodeCount = ignoredNodeCount,
                    )
                }

                items(nodes, key = { it.num }) { node ->
                    NodeItem(
                        modifier = Modifier.animateItem(),
                        thisNode = ourNode,
                        thatNode = node,
                        distanceUnits = state.distanceUnits,
                        tempInFahrenheit = state.tempInFahrenheit,
                        onClickChip = { navigateToNodeDetails(it.num) },
                        expanded = state.showDetails,
                        currentTimeMillis = currentTimeMillis,
                        isConnected = connectionState.isConnected(),
                    )
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}
