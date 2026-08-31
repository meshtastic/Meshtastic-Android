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
package org.meshtastic.web.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.ui.component.MeshtasticAppShell
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
import org.meshtastic.core.ui.component.MeshtasticNavigationSuite
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.settings.navigation.rememberSettingsRadioConfigViewModelProvider
import org.meshtastic.web.navigation.webNavGraph
import org.meshtastic.web.navigation.webVisibleDestinations

/**
 * Web main screen — same assembly as `desktopApp`'s `DesktopMainScreen` (shared
 * [MeshtasticAppShell] + [MeshtasticNavigationSuite] + [MeshtasticNavDisplay]), wired to [webNavGraph] instead of
 * `desktopNavGraph` and [webVisibleDestinations] instead of the full
 * [org.meshtastic.core.navigation.TopLevelDestination] set (no Map tab — v0 scope, AC9).
 */
@Suppress("ViewModelForwarding")
@Composable
fun WebMainScreen(uiViewModel: UIViewModel, multiBackstack: MultiBackstack, modifier: Modifier = Modifier) {
    val backStack = multiBackstack.activeBackStack
    val settingsRadioConfigViewModelProvider = rememberSettingsRadioConfigViewModelProvider(backStack)

    Surface(modifier = modifier.fillMaxSize()) {
        MeshtasticAppShell(multiBackstack = multiBackstack, uiViewModel = uiViewModel) {
            MeshtasticNavigationSuite(
                multiBackstack = multiBackstack,
                uiViewModel = uiViewModel,
                modifier = Modifier.fillMaxSize(),
                visibleDestinations = webVisibleDestinations,
            ) {
                val provider =
                    entryProvider<NavKey> {
                        webNavGraph(
                            backStack = backStack,
                            uiViewModel = uiViewModel,
                            multiBackstack = multiBackstack,
                            settingsRadioConfigViewModel = settingsRadioConfigViewModelProvider,
                        )
                    }
                MeshtasticNavDisplay(
                    multiBackstack = multiBackstack,
                    entryProvider = provider,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
