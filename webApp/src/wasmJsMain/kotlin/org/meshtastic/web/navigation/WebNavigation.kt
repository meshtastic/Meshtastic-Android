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
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Logger
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.connections.navigation.connectionsGraph
import org.meshtastic.feature.discovery.navigation.discoveryGraph
import org.meshtastic.feature.docs.navigation.docsEntries
import org.meshtastic.feature.firmware.navigation.firmwareGraph
import org.meshtastic.feature.messaging.navigation.contactsGraph
import org.meshtastic.feature.node.navigation.nodesGraph
import org.meshtastic.feature.settings.navigation.settingsGraph
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.channel.channelsGraph
import org.meshtastic.feature.wifiprovision.navigation.wifiProvisionGraph

/** Registers [NavKey] entry providers for the web destinations: the feature graphs with a wasmJs target. */
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
    firmwareGraph(backStack)
    settingsGraph(backStack, settingsRadioConfigViewModel)
    docsEntries(backStack)
    channelsGraph(backStack)
    connectionsGraph(backStack)
    discoveryGraph(backStack)
    wifiProvisionGraph(backStack)

    // A "map" deep link still routes here, so the destination exists and renders nothing.
    entry<MapRoute.Map> {}
}

/**
 * The entry for a key no web graph registers, which a deep link or a shared screen can still push. Navigation 3's
 * default throws there, and the exception leaves every later click dead until a reload; this logs the key and steps
 * back instead.
 */
fun unknownWebDestination(key: NavKey, backStack: NavBackStack<NavKey>): NavEntry<NavKey> = NavEntry(key) {
    LaunchedEffect(key) {
        Logger.w { "No web destination for ${key::class.simpleName}; going back" }
        if (backStack.size > 1) backStack.remove(key)
    }
}

/** Every [TopLevelDestination] except Map, which has no entries on web. */
val webVisibleDestinations: List<TopLevelDestination> =
    TopLevelDestination.entries.filter { it != TopLevelDestination.Map }

/**
 * Whether the web registers the screen [deepLink] (a `NotificationDeepLinks` path) opens. The map has no wasmJs target,
 * so a waypoint notification opens the app where it was instead.
 */
fun isWebDeepLink(deepLink: String): Boolean =
    deepLink.substringBefore('?').substringBefore('/').lowercase() in webDeepLinkSegments

private val webDeepLinkSegments =
    setOf("messages", "share", "quickchat", "nodes", "settings", "channels", "connections", "discovery", "firmware")
