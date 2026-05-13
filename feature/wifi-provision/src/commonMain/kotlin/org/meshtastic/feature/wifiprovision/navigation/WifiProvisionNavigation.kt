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
package org.meshtastic.feature.wifiprovision.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.WifiProvisionRoute
import org.meshtastic.feature.wifiprovision.ui.WifiProvisionScreen

/**
 * Registers the WiFi provisioning graph entries into the host navigation provider.
 *
 * Both the graph sentinel ([WifiProvisionRoute.WifiProvisionGraph]) and the primary screen
 * ([WifiProvisionRoute.WifiProvision]) navigate to the same composable so that the feature can be reached via either a
 * top-level push or a deep-link graph push.
 */
fun EntryProviderScope<NavKey>.wifiProvisionGraph(backStack: NavBackStack<NavKey>) {
    entry<WifiProvisionRoute.WifiProvisionGraph> {
        WifiProvisionScreen(onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }
    entry<WifiProvisionRoute.WifiProvision> { key ->
        WifiProvisionScreen(onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() }, address = key.address)
    }
}
