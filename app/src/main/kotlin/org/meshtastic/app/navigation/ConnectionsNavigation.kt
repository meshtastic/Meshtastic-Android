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
package org.meshtastic.app.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.ConnectionsRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.feature.connections.AndroidScannerViewModel
import org.meshtastic.feature.connections.ui.ConnectionsScreen
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

/** Navigation graph for for the top level ConnectionsScreen - [ConnectionsRoutes.Connections]. */
fun EntryProviderScope<NavKey>.connectionsGraph(backStack: NavBackStack<NavKey>) {
    entry<ConnectionsRoutes.ConnectionsGraph> {
        ConnectionsScreen(
            scanModel = koinViewModel<AndroidScannerViewModel>(),
            radioConfigViewModel = koinViewModel<RadioConfigViewModel>(),
            onClickNodeChip = {
                // Navigation 3 ignores back stack behavior options; we handle this by popping if necessary.
                backStack.add(NodesRoutes.NodeDetailGraph(it))
            },
            onNavigateToNodeDetails = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }

    entry<ConnectionsRoutes.Connections> {
        ConnectionsScreen(
            scanModel = koinViewModel<AndroidScannerViewModel>(),
            radioConfigViewModel = koinViewModel<RadioConfigViewModel>(),
            onClickNodeChip = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
            onNavigateToNodeDetails = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }
}
