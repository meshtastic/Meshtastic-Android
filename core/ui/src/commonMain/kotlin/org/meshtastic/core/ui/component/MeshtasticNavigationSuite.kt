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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowWidthSizeClass
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.navigateTopLevel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.ui.navigation.icon
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Shared adaptive navigation shell. Provides a Bottom Navigation bar on phones, and a Navigation Rail on tablets and
 * desktop targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshtasticNavigationSuite(
    backStack: NavBackStack<NavKey>,
    uiViewModel: UIViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val connectionState by uiViewModel.connectionState.collectAsStateWithLifecycle()
    val unreadMessageCount by uiViewModel.unreadMessageCount.collectAsStateWithLifecycle()
    val selectedDevice by uiViewModel.currentDeviceAddressFlow.collectAsStateWithLifecycle()

    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    val isCompact = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
    val currentKey = backStack.lastOrNull()
    val rootKey = backStack.firstOrNull()
    val topLevelDestination = TopLevelDestination.fromNavKey(rootKey)

    val onNavigate = { destination: TopLevelDestination ->
        val isRepress = destination == topLevelDestination
        if (isRepress) {
            when (destination) {
                TopLevelDestination.Nodes -> {
                    val onNodesList = currentKey is NodesRoutes.NodesGraph || currentKey is NodesRoutes.Nodes
                    if (!onNodesList) {
                        backStack.navigateTopLevel(destination.route)
                    } else {
                        uiViewModel.emitScrollToTopEvent(ScrollToTopEvent.NodesTabPressed)
                    }
                }
                TopLevelDestination.Conversations -> {
                    val onConversationsList =
                        currentKey is ContactsRoutes.ContactsGraph || currentKey is ContactsRoutes.Contacts
                    if (!onConversationsList) {
                        backStack.navigateTopLevel(destination.route)
                    } else {
                        uiViewModel.emitScrollToTopEvent(ScrollToTopEvent.ConversationsTabPressed)
                    }
                }
                else -> {
                    if (currentKey != destination.route) {
                        backStack.navigateTopLevel(destination.route)
                    }
                }
            }
        } else {
            backStack.navigateTopLevel(destination.route)
        }
    }

    if (isCompact) {
        Scaffold(
            modifier = modifier,
            bottomBar = {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == topLevelDestination,
                            onClick = { onNavigate(destination) },
                            icon = {
                                NavigationIconContent(
                                    destination = destination,
                                    isSelected = destination == topLevelDestination,
                                    connectionState = connectionState,
                                    unreadMessageCount = unreadMessageCount,
                                    selectedDevice = selectedDevice,
                                    uiViewModel = uiViewModel,
                                )
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
        }
    } else {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = destination == topLevelDestination,
                        onClick = { onNavigate(destination) },
                        icon = {
                            NavigationIconContent(
                                destination = destination,
                                isSelected = destination == topLevelDestination,
                                connectionState = connectionState,
                                unreadMessageCount = unreadMessageCount,
                                selectedDevice = selectedDevice,
                                uiViewModel = uiViewModel,
                            )
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationIconContent(
    destination: TopLevelDestination,
    isSelected: Boolean,
    connectionState: ConnectionState,
    unreadMessageCount: Int,
    selectedDevice: String?,
    uiViewModel: UIViewModel,
) {
    val isConnectionsRoute = destination == TopLevelDestination.Connections

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(
                    if (isConnectionsRoute) {
                        when (connectionState) {
                            ConnectionState.Connected -> stringResource(Res.string.connected)
                            ConnectionState.Connecting -> stringResource(Res.string.connecting)
                            ConnectionState.DeviceSleep -> stringResource(Res.string.device_sleeping)
                            ConnectionState.Disconnected -> stringResource(Res.string.disconnected)
                        }
                    } else {
                        stringResource(destination.label)
                    },
                )
            }
        },
        state = rememberTooltipState(),
    ) {
        if (isConnectionsRoute) {
            AnimatedConnectionsNavIcon(
                connectionState = connectionState,
                deviceType = DeviceType.fromAddress(selectedDevice ?: "NoDevice"),
                meshActivityFlow = uiViewModel.meshActivity,
            )
        } else {
            BadgedBox(
                badge = {
                    if (destination == TopLevelDestination.Conversations) {
                        var lastNonZeroCount by remember { mutableIntStateOf(unreadMessageCount) }
                        if (unreadMessageCount > 0) {
                            lastNonZeroCount = unreadMessageCount
                        }
                        AnimatedVisibility(
                            visible = unreadMessageCount > 0,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            Badge { Text(lastNonZeroCount.toString()) }
                        }
                    }
                },
            ) {
                Crossfade(isSelected, label = "BottomBarIcon") { isSelectedState ->
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.label),
                        tint = if (isSelectedState) colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
        }
    }
}
