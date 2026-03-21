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
package org.meshtastic.feature.node.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.node.DesktopAdaptiveNodeListScreen
import org.meshtastic.feature.node.list.NodeListViewModel

@Composable
actual fun AdaptiveNodeListScreen(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    initialNodeId: Int?,
    onNavigate: (Route) -> Unit,
    onNavigateToMessages: (String) -> Unit,
) {
    val viewModel: NodeListViewModel = koinViewModel()
    DesktopAdaptiveNodeListScreen(viewModel = viewModel, initialNodeId = initialNodeId, onNavigate = onNavigate)
}

@Composable
actual fun TracerouteMapScreen(destNum: Int, requestId: Int, logUuid: String?, onNavigateUp: () -> Unit) {
    // Desktop placeholder for now
    org.meshtastic.feature.node.navigation.PlaceholderScreen(name = "Traceroute Map")
}

@Composable
internal fun PlaceholderScreen(name: String) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = name,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
