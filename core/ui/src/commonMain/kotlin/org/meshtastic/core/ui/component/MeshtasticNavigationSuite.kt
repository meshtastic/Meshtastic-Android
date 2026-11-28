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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.navigation.ContactsRoute
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_restarting
import org.meshtastic.core.ui.navigation.icon
import org.meshtastic.core.ui.util.label
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.isInProgress

/**
 * Shared adaptive navigation shell using [NavigationSuiteScaffold].
 *
 * This implementation uses the [MultiBackstack] state holder to manage independent histories for each tab, aligning
 * with Navigation 3 best practices for state preservation during tab switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshtasticNavigationSuite(
    multiBackstack: MultiBackstack,
    uiViewModel: UIViewModel,
    modifier: Modifier = Modifier,
    // A host hides a tab it registers no entries for, as the web host does Map.
    visibleDestinations: List<TopLevelDestination> = TopLevelDestination.entries,
    content: @Composable () -> Unit,
) {
    val connectionState by uiViewModel.connectionState.collectAsStateWithLifecycle()
    val nodeRestartExpected by uiViewModel.nodeRestartExpected.collectAsStateWithLifecycle()
    val unreadMessageCount by uiViewModel.unreadMessageCount.collectAsStateWithLifecycle()
    val selectedDevice by uiViewModel.currentDeviceAddressFlow.collectAsStateWithLifecycle()

    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    val currentTabRoute = multiBackstack.currentTabRoute
    val topLevelDestination = TopLevelDestination.fromNavKey(currentTabRoute)

    val layoutType = navigationSuiteLayoutType(adaptiveInfo)
    val showLabels =
        layoutType == NavigationSuiteType.NavigationRail &&
            adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteItems = {
            visibleDestinations.forEach { destination ->
                val isSelected = destination == topLevelDestination
                item(
                    selected = isSelected,
                    onClick = { handleNavigation(destination, topLevelDestination, multiBackstack, uiViewModel) },
                    icon = {
                        NavigationIconContent(
                            destination = destination,
                            isSelected = isSelected,
                            connectionState = connectionState,
                            nodeRestartExpected = nodeRestartExpected,
                            unreadMessageCount = unreadMessageCount,
                            selectedDevice = selectedDevice,
                            meshActivityFlow = uiViewModel.meshActivity,
                        )
                    },
                    label =
                    if (showLabels) {
                        { Text(stringResource(destination.label)) }
                    } else {
                        null
                    },
                )
            }
        },
    ) {
        Row { content() }
    }
}

/**
 * A bottom bar on compact widths and in tabletop posture, a rail everywhere else. Unlike
 * [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo], a compact height (a phone in landscape) keeps the rail:
 * a bottom bar there takes height the screen has least of, while the rail takes width it has spare.
 */
internal fun navigationSuiteLayoutType(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType {
    val compactWidth =
        !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    return if (compactWidth || adaptiveInfo.windowPosture.isTabletop) {
        NavigationSuiteType.NavigationBar
    } else {
        NavigationSuiteType.NavigationRail
    }
}

private fun handleNavigation(
    destination: TopLevelDestination,
    topLevelDestination: TopLevelDestination?,
    multiBackstack: MultiBackstack,
    uiViewModel: UIViewModel,
) {
    val isRepress = destination == topLevelDestination
    if (isRepress) {
        val currentKey = multiBackstack.activeBackStack.lastOrNull()
        when (destination) {
            TopLevelDestination.Nodes -> {
                val onNodesList = currentKey is NodesRoute.Nodes
                if (!onNodesList) {
                    multiBackstack.navigateTopLevel(destination.route)
                } else {
                    uiViewModel.emitScrollToTopEvent(ScrollToTopEvent.NodesTabPressed)
                }
            }

            TopLevelDestination.Messages -> {
                val onConversationsList = currentKey is ContactsRoute.Contacts
                if (!onConversationsList) {
                    multiBackstack.navigateTopLevel(destination.route)
                } else {
                    uiViewModel.emitScrollToTopEvent(ScrollToTopEvent.ConversationsTabPressed)
                }
            }

            else -> {
                if (currentKey != destination.route) {
                    multiBackstack.navigateTopLevel(destination.route)
                }
            }
        }
    } else {
        multiBackstack.navigateTopLevel(destination.route)
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
    meshActivityFlow: Flow<MeshActivity>,
    nodeRestartExpected: Boolean = false,
) {
    val isConnectionsRoute = destination == TopLevelDestination.Connect
    // An expected node restart (reboot-applying config save) presents as an in-progress state, not a scary
    // red disconnect: the icon borrows the Connecting treatment and the tooltip says "Restarting".
    val restarting = nodeRestartExpected && connectionState != ConnectionState.Connected
    val presentedState =
        if (restarting && !connectionState.isInProgress) ConnectionState.Connecting(attempt = 1) else connectionState

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(
                    if (isConnectionsRoute) {
                        connectionTooltipLabel(restarting, connectionState)
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
                connectionState = presentedState,
                deviceType = DeviceType.fromAddress(selectedDevice ?: "NoDevice"),
                meshActivityFlow = meshActivityFlow,
            )
        } else {
            BadgedBox(
                badge = {
                    if (destination == TopLevelDestination.Messages) {
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
                        imageVector = vectorResource(destination.icon),
                        contentDescription = stringResource(destination.label),
                        tint = if (isSelectedState) colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
        }
    }
}

@Composable
private fun connectionTooltipLabel(restarting: Boolean, connectionState: ConnectionState): String = when {
    restarting -> stringResource(Res.string.node_restarting)
    else -> stringResource(connectionState.label)
}
