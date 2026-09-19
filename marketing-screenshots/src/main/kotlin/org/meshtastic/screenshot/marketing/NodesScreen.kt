/*
 * Copyright (c) 2026 Meshtastic LLC
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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.feature.node.detail.NodeDetailContent
import org.meshtastic.feature.node.detail.NodeDetailUiState
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState

/**
 * The nodes tab: the list, and beside it on an expanded window a node's detail page. Summit Solar sits in the detail
 * pane here so this shot and [NodeDetailScreen], which opens Ridge Top, are two different pages on a wide window.
 */
@Composable
internal fun NodesScreen(mesh: SampleMesh) {
    MarketingTheme {
        AppShell(TopLevelDestination.Nodes) {
            ListDetail(
                compactPane = Pane.List,
                list = { NodesPane(mesh) },
                detail = { NodeDetailPane(mesh, mesh.summitSolar) },
            )
        }
    }
}

/** Ridge Top's detail page, with the list beside it on an expanded window. */
@Composable
internal fun NodeDetailScreen(mesh: SampleMesh) {
    MarketingTheme {
        AppShell(TopLevelDestination.Nodes) {
            ListDetail(
                compactPane = Pane.Detail,
                list = { NodesPane(mesh) },
                detail = { NodeDetailPane(mesh, mesh.ridgeTop) },
            )
        }
    }
}

/** The node list as the app shows it: our node's chip in the bar, one [NodeItem] card per node. */
@Composable
private fun NodesPane(mesh: SampleMesh) {
    val ourNode = mesh.baseCamp
    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.nodes),
                ourNode = ourNode,
                showNodeChip = true,
                canNavigateUp = false,
                onNavigateUp = {},
                onClickChip = {},
                actions = {},
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp)) {
            items(mesh.nodes, key = { it.num }) { node ->
                NodeItem(
                    thisNode = ourNode,
                    thatNode = node,
                    distanceUnits = MeasurementSystem.METRIC,
                    tempInFahrenheit = false,
                    connectionState = ConnectionState.Connected,
                    isActive = node.num == ourNode.num,
                )
            }
        }
    }
}

/** A remote, unmanaged node's page from the node feature's own [NodeDetailContent]. */
@Composable
private fun NodeDetailPane(mesh: SampleMesh, node: Node) {
    Scaffold(
        topBar = {
            MainAppBar(
                title = node.user.long_name,
                ourNode = mesh.baseCamp,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = {},
                onClickChip = {},
                actions = {},
            )
        },
    ) { padding ->
        NodeDetailContent(
            uiState =
            NodeDetailUiState(
                node = node,
                ourNode = mesh.baseCamp,
                metricsState = MetricsState(isLocal = false, isManaged = false),
                availableLogs =
                setOf(
                    LogsType.DEVICE,
                    LogsType.POSITIONS,
                    LogsType.ENVIRONMENT,
                    LogsType.SIGNAL,
                    LogsType.TRACEROUTE,
                ),
            ),
            onAction = {},
            onFirmwareSelect = {},
            onSaveNotes = { _, _ -> },
            modifier = Modifier.padding(padding),
        )
    }
}
