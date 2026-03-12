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
package org.meshtastic.desktop.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.ConnectionsRoutes
import org.meshtastic.core.navigation.FirmwareRoutes
import org.meshtastic.core.navigation.MapRoutes
import org.meshtastic.desktop.ui.firmware.DesktopFirmwareScreen
import org.meshtastic.desktop.ui.map.KmpMapPlaceholder
import org.meshtastic.feature.connections.ui.ConnectionsScreen

/**
 * Registers entry providers for all top-level desktop destinations.
 *
 * Nodes uses real composables from `feature:node` via [desktopNodeGraph]. Conversations uses real composables from
 * `feature:messaging` via [desktopMessagingGraph]. Settings uses real composables from `feature:settings` via
 * [desktopSettingsGraph]. Connections uses the shared [ConnectionsScreen]. Other features use placeholder screens until
 * their shared composables are wired.
 */
fun EntryProviderScope<NavKey>.desktopNavGraph(backStack: NavBackStack<NavKey>) {
    // Nodes — real composables from feature:node
    desktopNodeGraph(backStack)

    // Conversations — real composables from feature:messaging
    desktopMessagingGraph(backStack)

    // Map — placeholder for now, will be replaced with feature:map real implementation
    entry<MapRoutes.Map> { KmpMapPlaceholder() }

    // Firmware — in-flow destination (for example from Settings), not a top-level rail tab
    entry<FirmwareRoutes.FirmwareGraph> { DesktopFirmwareScreen() }
    entry<FirmwareRoutes.FirmwareUpdate> { DesktopFirmwareScreen() }

    // Settings — real composables from feature:settings
    desktopSettingsGraph(backStack)

    // Channels
    entry<ChannelsRoutes.ChannelsGraph> { PlaceholderScreen("Channels") }
    entry<ChannelsRoutes.Channels> { PlaceholderScreen("Channels") }

    // Connections — shared screen
    entry<ConnectionsRoutes.ConnectionsGraph> {
        ConnectionsScreen(
            onClickNodeChip = { backStack.add(org.meshtastic.core.navigation.NodesRoutes.NodeDetailGraph(it)) },
            onNavigateToNodeDetails = { backStack.add(org.meshtastic.core.navigation.NodesRoutes.NodeDetailGraph(it)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }
    entry<ConnectionsRoutes.Connections> {
        ConnectionsScreen(
            onClickNodeChip = { backStack.add(org.meshtastic.core.navigation.NodesRoutes.NodeDetailGraph(it)) },
            onNavigateToNodeDetails = { backStack.add(org.meshtastic.core.navigation.NodesRoutes.NodeDetailGraph(it)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }
}

@Composable
internal fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
