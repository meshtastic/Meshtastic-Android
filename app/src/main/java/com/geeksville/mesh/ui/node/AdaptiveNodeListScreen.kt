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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.nodes
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.feature.node.detail.NodeDetailScreen
import org.meshtastic.feature.node.list.NodeListScreen

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveNodeListScreen(
    navController: NavHostController,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    initialNodeId: Int? = null,
    onNavigateToMessages: (String) -> Unit = {},
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()

    val isDetailActive = navigator.currentDestination?.contentKey != null

    BackHandler(enabled = isDetailActive) {
        scope.launch {
            if (navigator.canNavigateBack()) {
                navigator.navigateBack()
            } else {
                navigator.navigateTo(ListDetailPaneScaffoldRole.List, null)
            }
        }
    }

    LaunchedEffect(initialNodeId) {
        if (initialNodeId != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, initialNodeId)
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
                    navigateToNodeDetails = { nodeId ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, nodeId) }
                    },
                    scrollToTopEvents = scrollToTopEvents,
                    activeNodeId = navigator.currentDestination?.contentKey,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val focusManager = LocalFocusManager.current
                val nodeId = navigator.currentDestination?.contentKey
                // Prevent TextFields from auto-focusing when pane animates in
                LaunchedEffect(nodeId) { focusManager.clearFocus() }
                if (nodeId != null) {
                    NodeDetailScreenWrapper(
                        nodeId = nodeId,
                        navigateToMessages = onNavigateToMessages,
                        onNavigate = { route -> navController.navigate(route) },
                        onNavigateUp = { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.List, null) } },
                    )
                } else {
                    PlaceholderScreen()
                }
            }
        },
    )
}

@Composable
private fun NodeDetailScreenWrapper(
    nodeId: Int,
    navigateToMessages: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
) {
    NodeDetailScreen(
        onNavigateUp = onNavigateUp,
        navigateToMessages = navigateToMessages,
        onNavigate = onNavigate,
        overrideNodeId = nodeId,
    )
}

@Composable
private fun PlaceholderScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = MeshtasticIcons.Nodes,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.nodes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
