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
package org.meshtastic.appshell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.ui.component.MeshtasticAppShell
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
import org.meshtastic.core.ui.component.MeshtasticNavigationSuite
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.connections.navigation.connectionsGraph
import org.meshtastic.feature.discovery.navigation.discoveryGraph
import org.meshtastic.feature.docs.navigation.docsEntries
import org.meshtastic.feature.firmware.navigation.firmwareGraph
import org.meshtastic.feature.messaging.navigation.contactsGraph
import org.meshtastic.feature.node.navigation.nodesGraph
import org.meshtastic.feature.settings.navigation.rememberSettingsRadioConfigViewModelProvider
import org.meshtastic.feature.settings.navigation.settingsGraph
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.channel.channelsGraph
import org.meshtastic.feature.wifiprovision.navigation.wifiProvisionGraph

/**
 * The app every host shows: the shell, the navigation suite and the display over [sharedNavGraph], with the lockdown
 * prompt above them. A host passes what is its own: the tabs it can show and where its window insets go.
 *
 * @param unknownDestination the entry for a key nothing registers; null keeps Navigation 3's default, which throws.
 */
@Suppress("ViewModelForwarding", "LongParameterList")
@Composable
fun MeshtasticMainScreen(
    uiViewModel: UIViewModel,
    multiBackstack: MultiBackstack,
    modifier: Modifier = Modifier,
    hostModifier: Modifier = Modifier,
    displayModifier: Modifier = Modifier,
    visibleDestinations: List<TopLevelDestination> = TopLevelDestination.entries,
    analytics: PlatformAnalytics? = null,
    unknownDestination: ((key: NavKey, backStack: NavBackStack<NavKey>) -> NavEntry<NavKey>)? = null,
) {
    val backStack = multiBackstack.activeBackStack
    val settingsRadioConfigViewModelProvider = rememberSettingsRadioConfigViewModelProvider(backStack)

    LockdownPrompt(uiViewModel)

    MeshtasticAppShell(multiBackstack = multiBackstack, uiViewModel = uiViewModel, hostModifier = hostModifier) {
        MeshtasticNavigationSuite(
            multiBackstack = multiBackstack,
            uiViewModel = uiViewModel,
            modifier = modifier.fillMaxSize(),
            visibleDestinations = visibleDestinations,
        ) {
            val entries: EntryProviderScope<NavKey>.() -> Unit = {
                sharedNavGraph(backStack, uiViewModel, multiBackstack, settingsRadioConfigViewModelProvider)
            }
            val provider =
                if (unknownDestination == null) {
                    entryProvider(builder = entries)
                } else {
                    entryProvider(fallback = { key -> unknownDestination(key, backStack) }, builder = entries)
                }
            MeshtasticNavDisplay(
                multiBackstack = multiBackstack,
                entryProvider = provider,
                modifier = displayModifier.fillMaxSize(),
                analytics = analytics,
            )
        }
    }
}

/** Every destination: the feature graphs all targets build, then [mapGraph], which the web has no build of. */
fun EntryProviderScope<NavKey>.sharedNavGraph(
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
    mapGraph(backStack)
}

/** The map's destinations where the map is built; an empty one on the web, where a deep link can still name it. */
internal expect fun EntryProviderScope<NavKey>.mapGraph(backStack: NavBackStack<NavKey>)
