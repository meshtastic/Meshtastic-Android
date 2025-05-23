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

package com.geeksville.mesh.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.NodeFilterTextField
import com.geeksville.mesh.ui.components.NodeMenuAction
import com.geeksville.mesh.ui.components.rememberTimeTickWithLifecycle

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeScreen(
    model: UIViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
) {
    val state by model.nodesUiState.collectAsStateWithLifecycle()

    val nodes by model.nodeList.collectAsStateWithLifecycle()
    val ourNode by model.ourNodeInfo.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    val currentTimeMillis = rememberTimeTickWithLifecycle()
    val connectionState by model.connectionState.collectAsStateWithLifecycle()

    var showSharedContact: Node? by remember { mutableStateOf(null) }
    if (showSharedContact != null) {
        SharedContactDialog(
            contact = showSharedContact,
            onDismiss = { showSharedContact = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            stickyHeader {
                val animatedAlpha by animateFloatAsState(
                    targetValue = if (!listState.isScrollInProgress) 1.0f else 0f,
                    label = "alpha"
                )
                NodeFilterTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceDim.copy(alpha = animatedAlpha))
                        .graphicsLayer(alpha = animatedAlpha)
                        .padding(8.dp),
                    filterText = state.filter,
                    onTextChange = model::setNodeFilterText,
                    currentSortOption = state.sort,
                    onSortSelect = model::setSortOption,
                    includeUnknown = state.includeUnknown,
                    onToggleIncludeUnknown = model::toggleIncludeUnknown,
                    showDetails = state.showDetails,
                    onToggleShowDetails = model::toggleShowDetails,
                )
            }

            items(nodes, key = { it.num }) { node ->
                NodeItem(
                    modifier = Modifier.animateContentSize(),
                    thisNode = ourNode,
                    thatNode = node,
                    gpsFormat = state.gpsFormat,
                    distanceUnits = state.distanceUnits,
                    tempInFahrenheit = state.tempInFahrenheit,
                    onAction = { menuItem ->
                        when (menuItem) {
                            is NodeMenuAction.Remove -> model.removeNode(node.num)
                            is NodeMenuAction.Ignore -> model.ignoreNode(node)
                            is NodeMenuAction.Favorite -> model.favoriteNode(node)
                            is NodeMenuAction.DirectMessage -> {
                                val hasPKC = model.ourNodeInfo.value?.hasPKC == true && node.hasPKC
                                val channel =
                                    if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
                                navigateToMessages("$channel${node.user.id}")
                            }

                            is NodeMenuAction.RequestUserInfo -> model.requestUserInfo(node.num)
                            is NodeMenuAction.RequestPosition -> model.requestPosition(node.num)
                            is NodeMenuAction.TraceRoute -> model.requestTraceroute(node.num)
                            is NodeMenuAction.MoreDetails -> navigateToNodeDetails(node.num)
                            is NodeMenuAction.Share -> showSharedContact = node
                        }
                    },
                    expanded = state.showDetails,
                    currentTimeMillis = currentTimeMillis,
                    isConnected = connectionState.isConnected(),
                )
            }
        }

        val firmwareVersion = DeviceVersion(ourNode?.metadata?.firmwareVersion ?: "0.0.0")
        val shareCapable = firmwareVersion.supportsQrCodeSharing()

        AnimatedVisibility(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd),
            visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !listState.isScrollInProgress &&
                    connectionState.isConnected() &&
                    shareCapable
        ) {
            @Suppress("NewApi")
            AddContactFAB(
                model = model,
                onSharedContactImport = { contact ->
                    model.addSharedContact(contact)
                }
            )
        }
    }
}
