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
package org.meshtastic.web.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.connections.navigation.connectionsGraph
import org.meshtastic.feature.messaging.navigation.contactsGraph
import org.meshtastic.feature.node.navigation.nodesGraph
import org.meshtastic.feature.settings.navigation.settingsGraph
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.channel.channelsGraph

/**
 * Registers [NavKey] entry providers for every web (v0) destination — same delegation-to-feature-graph shape as
 * `desktopApp`'s `desktopNavGraph`, but only the four v0 feature modules: `nodesGraph`, `contactsGraph`,
 * `settingsGraph`, `channelsGraph`, `connectionsGraph`. No `mapGraph`/`firmwareGraph`/`docsEntries`/`discoveryGraph`/
 * `wifiProvisionGraph` — those feature modules aren't dependencies of `webApp` at all (AC9).
 */
fun EntryProviderScope<NavKey>.webNavGraph(
    backStack: NavBackStack<NavKey>,
    uiViewModel: UIViewModel,
    multiBackstack: MultiBackstack,
    settingsRadioConfigViewModel: @Composable (SettingsRoute.Settings?) -> RadioConfigViewModel,
) {
    nodesGraph(
        backStack = backStack,
        scrollToTopEvents = uiViewModel.scrollToTopEventFlow,
        onHandleDeepLink = uiViewModel::handleDeepLink,
        onNavigateToConnections = { multiBackstack.navigateTopLevel(TopLevelDestination.Connect.route) },
    )
    contactsGraph(
        backStack = backStack,
        scrollToTopEvents = uiViewModel.scrollToTopEventFlow,
        onHandleDeepLink = uiViewModel::handleDeepLink,
    )
    settingsGraph(backStack, settingsRadioConfigViewModel)
    channelsGraph(backStack)
    connectionsGraph(backStack)

    // Defensive fallback for MapRoute.Map, reachable via DeepLinkRouter's "map" URI mapping regardless of platform
    // (see core:navigation's DeepLinkRouter.kt) even though feature:map isn't a dependency here. Renders nothing,
    // matching feature:settings' identical TakModuleConfigContent fallback for a deep-link-reachable destination
    // this platform can't serve — an honest no-op, not a crash.
    entry<MapRoute.Map> {}
}

/** v0's visible top-level tabs: every [TopLevelDestination] except Map (no `mapGraph` entry provider above). */
val webVisibleDestinations: List<TopLevelDestination> =
    TopLevelDestination.entries.filter { it != TopLevelDestination.Map }
