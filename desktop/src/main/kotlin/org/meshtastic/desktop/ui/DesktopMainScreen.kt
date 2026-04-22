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
package org.meshtastic.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.ui.component.MeshtasticAppShell
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
import org.meshtastic.core.ui.component.MeshtasticNavigationSuite
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.desktop.navigation.desktopNavGraph

/**
 * Desktop main screen — assembles the shared [MeshtasticAppShell], [MeshtasticNavigationSuite], and
 * [MeshtasticNavDisplay] with the desktop-specific [desktopNavGraph] entry provider.
 */
@Composable
fun DesktopMainScreen(uiViewModel: UIViewModel, multiBackstack: MultiBackstack) {
    val backStack = multiBackstack.activeBackStack

    Surface(modifier = Modifier.fillMaxSize()) {
        MeshtasticAppShell(
            multiBackstack = multiBackstack,
            uiViewModel = uiViewModel,
            hostModifier = Modifier.padding(bottom = 24.dp),
        ) {
            MeshtasticNavigationSuite(
                multiBackstack = multiBackstack,
                uiViewModel = uiViewModel,
                modifier = Modifier.fillMaxSize(),
            ) {
                val provider = entryProvider<NavKey> { desktopNavGraph(backStack, uiViewModel, multiBackstack) }
                MeshtasticNavDisplay(
                    multiBackstack = multiBackstack,
                    entryProvider = provider,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
