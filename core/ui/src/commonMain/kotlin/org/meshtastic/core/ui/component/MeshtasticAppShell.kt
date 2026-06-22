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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.navigation.NodeDetailRoute
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Shared shell for setting up global UI logic across platforms (Android, Desktop).
 *
 * This component handles deep linking, shared dialogs (via [MeshtasticCommonAppSetup]), and provides the global
 * [MeshtasticSnackbarProvider]. Platform entry points should wrap their navigation layout inside this shell.
 */
@Composable
fun MeshtasticAppShell(
    multiBackstack: MultiBackstack,
    uiViewModel: UIViewModel,
    hostModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(uiViewModel) {
        uiViewModel.navigationDeepLink.collect { navKeys -> multiBackstack.handleDeepLink(navKeys) }
    }

    MeshtasticCommonAppSetup(
        uiViewModel = uiViewModel,
        onNavigateToTracerouteMap = { destNum, requestId, logUuid ->
            multiBackstack.handleDeepLink(
                listOf(
                    NodesRoute.Nodes,
                    NodeDetailRoute.TracerouteMap(destNum = destNum, requestId = requestId, logUuid = logUuid),
                ),
            )
        },
    )

    // Connected device's modem preset, provided once here so signal-quality rating is preset-relative across all
    // screens without per-screen plumbing. Distinct so the value only changes when the preset itself does.
    val radioConfigRepository = koinInject<RadioConfigRepository>()
    val modemPreset by
        remember(radioConfigRepository) {
            radioConfigRepository.localConfigFlow.map { it.lora?.modem_preset }.distinctUntilChanged()
        }
            .collectAsStateWithLifecycle(initialValue = null)

    MeshtasticSnackbarProvider(snackbarManager = uiViewModel.snackbarManager, hostModifier = hostModifier) {
        CompositionLocalProvider(LocalModemPreset provides modemPreset) { content() }
    }
}
