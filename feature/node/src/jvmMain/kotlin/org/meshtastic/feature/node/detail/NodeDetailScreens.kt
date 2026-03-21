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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.navigation.Route
import org.meshtastic.feature.node.compass.CompassViewModel
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.model.NodeDetailAction

@Composable
actual fun NodeDetailScreen(
    nodeId: Int,
    modifier: Modifier,
    viewModel: NodeDetailViewModel,
    navigateToMessages: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
    compassViewModel: CompassViewModel?,
) {
    LaunchedEffect(nodeId) { viewModel.start(nodeId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Desktop just renders the NodeDetailContent directly. Overlays like Compass are no-ops.
    NodeDetailContent(
        uiState = uiState,
        modifier = modifier,
        onAction = { action ->
            when (action) {
                is NodeDetailAction.Navigate -> onNavigate(action.route)
                is NodeDetailAction.TriggerServiceAction -> viewModel.onServiceAction(action.action)
                is NodeDetailAction.HandleNodeMenuAction -> {
                    when (val menuAction = action.action) {
                        is NodeMenuAction.DirectMessage -> {
                            val route = viewModel.getDirectMessageRoute(menuAction.node, uiState.ourNode)
                            navigateToMessages(route)
                        }
                        is NodeMenuAction.Remove -> {
                            viewModel.handleNodeMenuAction(menuAction)
                            onNavigateUp()
                        }
                        else -> viewModel.handleNodeMenuAction(menuAction)
                    }
                }
                else -> {}
            }
        },
        onFirmwareSelect = { /* No-op on desktop for now */ },
        onSaveNotes = { num, notes -> viewModel.setNodeNotes(num, notes) },
    )
}
