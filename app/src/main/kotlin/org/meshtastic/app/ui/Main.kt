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
@file:Suppress("MatchingDeclarationName")

package org.meshtastic.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.BuildConfig
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.MeshtasticNavSavedStateConfig
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.navigateTopLevel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_too_old
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.must_update
import org.meshtastic.core.ui.component.MeshtasticCommonAppSetup
import org.meshtastic.core.ui.component.MeshtasticSnackbarProvider
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.navigation.icon
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.navigation.connectionsGraph
import org.meshtastic.feature.firmware.navigation.firmwareGraph
import org.meshtastic.feature.map.navigation.mapGraph
import org.meshtastic.feature.messaging.navigation.contactsGraph
import org.meshtastic.feature.node.navigation.nodesGraph
import org.meshtastic.feature.settings.navigation.settingsGraph
import org.meshtastic.feature.settings.radio.channel.channelsGraph

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MainScreen(uIViewModel: UIViewModel = koinViewModel(), scanModel: ScannerViewModel = koinViewModel()) {
    val backStack = rememberNavBackStack(MeshtasticNavSavedStateConfig, NodesRoutes.NodesGraph as NavKey)
    // LaunchedEffect(uIViewModel) { uIViewModel.navigationDeepLink.collectLatest { uri -> navController.navigate(uri) }
    // }
    val connectionState by uIViewModel.connectionState.collectAsStateWithLifecycle()
    val unreadMessageCount by uIViewModel.unreadMessageCount.collectAsStateWithLifecycle()

    MeshtasticCommonAppSetup(
        uiViewModel = uIViewModel,
        onNavigateToTracerouteMap = { destNum, requestId, logUuid ->
            backStack.add(NodeDetailRoutes.TracerouteMap(destNum = destNum, requestId = requestId, logUuid = logUuid))
        },
    )

    AndroidAppVersionCheck(uIViewModel)
    val navSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(
            currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true),
        )
    val currentKey = backStack.lastOrNull()
    val topLevelDestination = TopLevelDestination.fromNavKey(currentKey)

    // State for determining the connection type icon to display
    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()

    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == topLevelDestination
                val isConnectionsRoute = destination == TopLevelDestination.Connections
                item(
                    icon = {
                        TooltipBox(
                            positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    Text(
                                        if (isConnectionsRoute) {
                                            when (connectionState) {
                                                ConnectionState.Connected -> stringResource(Res.string.connected)
                                                ConnectionState.Connecting -> stringResource(Res.string.connecting)
                                                ConnectionState.DeviceSleep ->
                                                    stringResource(Res.string.device_sleeping)
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
                                org.meshtastic.feature.connections.ui.components.AnimatedConnectionsNavIcon(
                                    connectionState = connectionState,
                                    deviceType = DeviceType.fromAddress(selectedDevice),
                                    meshActivityFlow = uIViewModel.meshActivity,
                                    colorScheme = colorScheme,
                                )
                            } else {
                                BadgedBox(
                                    badge = {
                                        if (destination == TopLevelDestination.Conversations) {
                                            // Keep track of the last non-zero count for display during exit animation
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
                                            tint =
                                            if (isSelectedState) colorScheme.primary else LocalContentColor.current,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    selected = isSelected,
                    label = {
                        Text(
                            text = stringResource(destination.label),
                            modifier =
                            if (navSuiteType == NavigationSuiteType.ShortNavigationBarCompact) {
                                Modifier.width(1.dp)
                                    .height(1.dp) // hide on phone - min 1x1 or talkback won't see it.
                            } else {
                                Modifier
                            },
                        )
                    },
                    onClick = {
                        val isRepress = destination == topLevelDestination
                        if (isRepress) {
                            when (destination) {
                                TopLevelDestination.Nodes -> {
                                    val onNodesList = currentKey is NodesRoutes.Nodes
                                    if (!onNodesList) {
                                        backStack.navigateTopLevel(destination.route)
                                    }
                                    uIViewModel.emitScrollToTopEvent(ScrollToTopEvent.NodesTabPressed)
                                }
                                TopLevelDestination.Conversations -> {
                                    val onConversationsList = currentKey is ContactsRoutes.Contacts
                                    if (!onConversationsList) {
                                        backStack.navigateTopLevel(destination.route)
                                    }
                                    uIViewModel.emitScrollToTopEvent(ScrollToTopEvent.ConversationsTabPressed)
                                }
                                else -> Unit
                            }
                        } else {
                            backStack.navigateTopLevel(destination.route)
                        }
                    },
                )
            }
        },
    ) {
        MeshtasticSnackbarProvider(
            snackbarManager = uIViewModel.snackbarManager,
            hostModifier = Modifier.safeDrawingPadding().padding(bottom = 16.dp),
        ) {
            val provider =
                entryProvider<NavKey> {
                    contactsGraph(backStack, uIViewModel.scrollToTopEventFlow)
                    nodesGraph(
                        backStack = backStack,
                        scrollToTopEvents = uIViewModel.scrollToTopEventFlow,
                        nodeMapScreen = { destNum, onNavigateUp ->
                            val vm =
                                org.koin.compose.viewmodel.koinViewModel<
                                    org.meshtastic.feature.map.node.NodeMapViewModel,
                                    >()
                            vm.setDestNum(destNum)
                            org.meshtastic.app.map.node.NodeMapScreen(vm, onNavigateUp = onNavigateUp)
                        },
                    )
                    mapGraph(backStack)
                    channelsGraph(backStack)
                    connectionsGraph(backStack)
                    settingsGraph(backStack)
                    firmwareGraph(backStack)
                }
            NavDisplay(
                backStack = backStack,
                entryProvider = provider,
                modifier = Modifier.fillMaxSize().recalculateWindowInsets().safeDrawingPadding(),
            )
        }
    }
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun AndroidAppVersionCheck(viewModel: UIViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()

    // Check if the device is running an old app version
    LaunchedEffect(connectionState, myNodeInfo) {
        if (connectionState == ConnectionState.Connected) {
            myNodeInfo?.let { info ->
                val isOld = info.minAppVersion > BuildConfig.VERSION_CODE && BuildConfig.DEBUG.not()
                Logger.d {
                    "[FW_CHECK] App version check - minAppVersion: ${info.minAppVersion}, " +
                        "currentVersion: ${BuildConfig.VERSION_CODE}, isOld: $isOld"
                }

                if (isOld) {
                    Logger.w { "[FW_CHECK] App too old - showing update prompt" }
                    viewModel.showAlert(
                        titleRes = Res.string.app_too_old,
                        messageRes = Res.string.must_update,
                        onConfirm = { viewModel.setDeviceAddress("n") },
                    )
                }
            }
        }
    }
}
