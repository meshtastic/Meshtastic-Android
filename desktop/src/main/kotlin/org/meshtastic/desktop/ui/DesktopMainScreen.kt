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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ui.component.MeshtasticAppShell
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.desktop.navigation.desktopNavGraph

/**
 * Desktop main screen — Navigation 3 shell with adaptive navigation and shared [MeshtasticNavDisplay].
 *
 * Uses the same shared routes from `core:navigation` and the same `MeshtasticNavDisplay` + `entryProvider` pattern as
 * the Android app, proving the shared backstack architecture works across targets.
 */
@Composable
@Suppress("LongMethod")
fun DesktopMainScreen(backStack: NavBackStack<NavKey>, uiViewModel: UIViewModel = koinViewModel()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MeshtasticAppShell(
            backStack = backStack,
            uiViewModel = uiViewModel,
            hostModifier = Modifier.padding(bottom = 24.dp),
        ) {
            org.meshtastic.core.ui.component.MeshtasticNavigationSuite(
                backStack = backStack,
                uiViewModel = uiViewModel,
            ) {
                val provider = entryProvider<NavKey> { desktopNavGraph(backStack, uiViewModel) }

                MeshtasticNavDisplay(backStack = backStack, entryProvider = provider, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
