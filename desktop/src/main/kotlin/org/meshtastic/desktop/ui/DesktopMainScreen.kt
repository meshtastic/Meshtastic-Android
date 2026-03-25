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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.navigateTopLevel
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.ui.component.MeshtasticAppShell
import org.meshtastic.core.ui.navigation.icon
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.desktop.navigation.desktopNavGraph

/**
 * Desktop main screen — Navigation 3 shell with a persistent [NavigationRail] and [NavDisplay].
 *
 * Uses the same shared routes from `core:navigation` and the same `NavDisplay` + `entryProvider` pattern as the Android
 * app, proving the shared backstack architecture works across targets.
 */
@Composable
@Suppress("LongMethod")
fun DesktopMainScreen(
    backStack: NavBackStack<NavKey>,
    radioService: RadioInterfaceService = koinInject(),
    uiViewModel: UIViewModel = koinViewModel(),
) {
    val currentKey = backStack.lastOrNull()
    val selected = TopLevelDestination.fromNavKey(currentKey)

    LaunchedEffect(uiViewModel) {
        uiViewModel.navigationDeepLink.collect { uri ->
            val commonUri = org.meshtastic.core.common.util.CommonUri.parse(uri.uriString)
            org.meshtastic.core.navigation.DeepLinkRouter.route(commonUri)?.let { navKeys ->
                backStack.clear()
                backStack.addAll(navKeys)
            }
        }
    }

    val connectionState by radioService.connectionState.collectAsStateWithLifecycle()
    val selectedDevice by radioService.currentDeviceAddressFlow.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MeshtasticAppShell(
            backStack = backStack,
            uiViewModel = uiViewModel,
            hostModifier = Modifier.padding(bottom = 24.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = destination == selected,
                                onClick = {
                                    if (destination != selected) {
                                        backStack.navigateTopLevel(destination.route)
                                    }
                                },
                                icon = {
                                    if (destination == TopLevelDestination.Connections) {
                                        org.meshtastic.feature.connections.ui.components.AnimatedConnectionsNavIcon(
                                            connectionState = connectionState,
                                            deviceType = DeviceType.fromAddress(selectedDevice ?: "NoDevice"),
                                            meshActivityFlow = radioService.meshActivity,
                                            colorScheme = colorScheme,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = stringResource(destination.label),
                                        )
                                    }
                                },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }

                    val provider = entryProvider<NavKey> { desktopNavGraph(backStack, uiViewModel) }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = provider,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}
