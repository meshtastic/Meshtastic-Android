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

package org.meshtastic.feature.node.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.NodeDetailAction

@Suppress("LongMethod")
@Composable
fun NodeDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricsViewModel = hiltViewModel(),
    nodeDetailViewModel: NodeDetailViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
    val lastTracerouteTime by nodeDetailViewModel.lastTraceRouteTime.collectAsStateWithLifecycle()
    val ourNode by nodeDetailViewModel.ourNodeInfo.collectAsStateWithLifecycle()

    val availableLogs by
        remember(state, environmentState) {
            derivedStateOf {
                buildSet {
                    if (state.hasDeviceMetrics()) add(LogsType.DEVICE)
                    if (state.hasPositionLogs()) {
                        add(LogsType.NODE_MAP)
                        add(LogsType.POSITIONS)
                    }
                    if (environmentState.hasEnvironmentMetrics()) add(LogsType.ENVIRONMENT)
                    if (state.hasSignalMetrics()) add(LogsType.SIGNAL)
                    if (state.hasPowerMetrics()) add(LogsType.POWER)
                    if (state.hasTracerouteLogs()) add(LogsType.TRACEROUTE)
                    if (state.hasHostMetrics()) add(LogsType.HOST)
                    if (state.hasPaxMetrics()) add(LogsType.PAX)
                }
            }
        }

    val node = state.node

    @Suppress("ModifierNotUsedAtRoot")
    Scaffold(
        topBar = {
            MainAppBar(
                title = node?.user?.longName ?: "",
                ourNode = ourNode,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        if (node != null) {
            @Suppress("ViewModelForwarding")
            NodeDetailContent(
                node = node,
                ourNode = ourNode,
                metricsState = state,
                lastTracerouteTime = lastTracerouteTime,
                availableLogs = availableLogs,
                onAction = { action ->
                    handleNodeAction(
                        action = action,
                        ourNode = ourNode,
                        node = node,
                        navigateToMessages = navigateToMessages,
                        onNavigateUp = onNavigateUp,
                        onNavigate = onNavigate,
                        viewModel = viewModel,
                        handleNodeMenuAction = { nodeDetailViewModel.handleNodeMenuAction(it) },
                    )
                },
                modifier = modifier.padding(paddingValues),
                onSaveNotes = { num, notes -> nodeDetailViewModel.setNodeNotes(num, notes) },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun handleNodeAction(
    action: NodeDetailAction,
    ourNode: Node?,
    node: Node,
    navigateToMessages: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: MetricsViewModel,
    handleNodeMenuAction: (NodeMenuAction) -> Unit,
) {
    when (action) {
        is NodeDetailAction.Navigate -> onNavigate(action.route)
        is NodeDetailAction.TriggerServiceAction -> viewModel.onServiceAction(action.action)
        is NodeDetailAction.HandleNodeMenuAction -> {
            when (val menuAction = action.action) {
                is NodeMenuAction.DirectMessage -> {
                    val hasPKC = ourNode?.hasPKC == true
                    val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
                    navigateToMessages("$channel${node.user.id}")
                }

                is NodeMenuAction.Remove -> {
                    handleNodeMenuAction(menuAction)
                    onNavigateUp()
                }

                else -> handleNodeMenuAction(menuAction)
            }
        }

        is NodeDetailAction.ShareContact -> {
            /* Handled in NodeDetailContent */
        }
    }
}
