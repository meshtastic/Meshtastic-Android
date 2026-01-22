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
package org.meshtastic.feature.node.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
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
    nodeId: Int,
    modifier: Modifier = Modifier,
    metricsViewModel: MetricsViewModel = hiltViewModel(),
    nodeDetailViewModel: NodeDetailViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    LaunchedEffect(nodeId) {
        metricsViewModel.setNodeId(nodeId)
        nodeDetailViewModel.start(nodeId)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        nodeDetailViewModel.effects.collect { effect ->
            when (effect) {
                is NodeRequestEffect.ShowFeedback -> {
                    @Suppress("SpreadOperator")
                    snackbarHostState.showSnackbar(getString(effect.resource, *effect.args.toTypedArray()))
                }
            }
        }
    }

    val metricsState by metricsViewModel.state.collectAsStateWithLifecycle()
    val environmentMetricsState by metricsViewModel.environmentState.collectAsStateWithLifecycle()
    val lastTracerouteTime by nodeDetailViewModel.lastTraceRouteTime.collectAsStateWithLifecycle()
    val lastRequestNeighborsTime by nodeDetailViewModel.lastRequestNeighborsTime.collectAsStateWithLifecycle()
    val ourNode by nodeDetailViewModel.ourNodeInfo.collectAsStateWithLifecycle()

    val availableLogs by
        remember(metricsState, environmentMetricsState) {
            derivedStateOf {
                buildSet {
                    if (metricsState.hasDeviceMetrics()) add(LogsType.DEVICE)
                    if (metricsState.hasPositionLogs()) {
                        add(LogsType.NODE_MAP)
                        add(LogsType.POSITIONS)
                    }
                    if (environmentMetricsState.hasEnvironmentMetrics()) add(LogsType.ENVIRONMENT)
                    if (metricsState.hasSignalMetrics()) add(LogsType.SIGNAL)
                    if (metricsState.hasPowerMetrics()) add(LogsType.POWER)
                    if (metricsState.hasTracerouteLogs()) add(LogsType.TRACEROUTE)
                    if (metricsState.hasHostMetrics()) add(LogsType.HOST)
                    if (metricsState.hasPaxMetrics()) add(LogsType.PAX)
                }
            }
        }

    val node = metricsState.node

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (node != null) {
            NodeDetailContent(
                node = node,
                ourNode = ourNode,
                metricsState = metricsState,
                lastTracerouteTime = lastTracerouteTime,
                lastRequestNeighborsTime = lastRequestNeighborsTime,
                availableLogs = availableLogs,
                onAction = { action ->
                    handleNodeAction(
                        action = action,
                        ourNode = ourNode,
                        node = node,
                        navigateToMessages = navigateToMessages,
                        onNavigateUp = onNavigateUp,
                        onNavigate = onNavigate,
                        metricsViewModel = metricsViewModel,
                        nodeDetailViewModel = nodeDetailViewModel,
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
    metricsViewModel: MetricsViewModel,
    nodeDetailViewModel: NodeDetailViewModel,
) {
    when (action) {
        is NodeDetailAction.Navigate -> onNavigate(action.route)
        is NodeDetailAction.TriggerServiceAction -> metricsViewModel.onServiceAction(action.action)
        is NodeDetailAction.HandleNodeMenuAction -> {
            when (val menuAction = action.action) {
                is NodeMenuAction.DirectMessage -> {
                    val hasPKC = ourNode?.hasPKC == true
                    val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
                    navigateToMessages("${channel}${node.user.id}")
                }

                is NodeMenuAction.Remove -> {
                    nodeDetailViewModel.handleNodeMenuAction(menuAction)
                    onNavigateUp()
                }

                else -> nodeDetailViewModel.handleNodeMenuAction(menuAction)
            }
        }

        is NodeDetailAction.ShareContact -> {
            /* Handled in NodeDetailContent */
        }

        is NodeDetailAction.OpenCompass -> {
            /* Handled in NodeDetailList */
        }
    }
}
