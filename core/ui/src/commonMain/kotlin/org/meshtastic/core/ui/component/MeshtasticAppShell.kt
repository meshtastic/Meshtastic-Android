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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Shared shell for setting up global UI logic across platforms (Android, Desktop).
 *
 * This component handles deep linking, shared dialogs (via [MeshtasticCommonAppSetup]), and provides the global
 * [MeshtasticSnackbarProvider]. Platform entry points should wrap their navigation layout inside this shell.
 */
@Composable
fun MeshtasticAppShell(
    backStack: NavBackStack<NavKey>,
    uiViewModel: UIViewModel,
    hostModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(uiViewModel) {
        uiViewModel.navigationDeepLink.collect { navKeys ->
            backStack.clear()
            backStack.addAll(navKeys)
        }
    }

    MeshtasticCommonAppSetup(
        uiViewModel = uiViewModel,
        onNavigateToTracerouteMap = { destNum, requestId, logUuid ->
            backStack.add(NodeDetailRoutes.TracerouteMap(destNum = destNum, requestId = requestId, logUuid = logUuid))
        },
    )

    MeshtasticSnackbarProvider(snackbarManager = uiViewModel.snackbarManager, hostModifier = hostModifier) { content() }
}
