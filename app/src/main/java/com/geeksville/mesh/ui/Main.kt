/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.Contactless
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.People
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.ChannelsRoutes
import com.geeksville.mesh.navigation.ConnectionsRoutes
import com.geeksville.mesh.navigation.ContactsRoutes
import com.geeksville.mesh.navigation.MapRoutes
import com.geeksville.mesh.navigation.NavGraph
import com.geeksville.mesh.navigation.NodesRoutes
import com.geeksville.mesh.navigation.RadioConfigRoutes
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.navigation.showLongNameTitle
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.TopLevelDestination.Companion.isTopLevel
import com.geeksville.mesh.ui.common.components.MultipleChoiceAlertDialog
import com.geeksville.mesh.ui.common.components.ScannedQrCodeDialog
import com.geeksville.mesh.ui.common.components.SimpleAlertDialog
import com.geeksville.mesh.ui.debug.DebugMenuActions

enum class TopLevelDestination(@StringRes val label: Int, val icon: ImageVector, val route: Route) {
    Contacts(R.string.contacts, Icons.AutoMirrored.TwoTone.Chat, ContactsRoutes.Contacts),
    Nodes(R.string.nodes, Icons.TwoTone.People, NodesRoutes.Nodes),
    Map(R.string.map, Icons.TwoTone.Map, MapRoutes.Map),
    Channels(R.string.channels, Icons.TwoTone.Contactless, ChannelsRoutes.Channels),
    Connections(R.string.connections, Icons.TwoTone.CloudOff, ConnectionsRoutes.Connections),
    ;

    companion object {
        fun NavDestination.isTopLevel(): Boolean = entries.any { hasRoute(it.route::class) }

        fun fromNavDestination(destination: NavDestination?): TopLevelDestination? = entries
            .find { dest -> destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MainScreen(
    viewModel: UIViewModel = hiltViewModel(),
    onAction: (MainMenuAction) -> Unit,
) {
    val navController = rememberNavController()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val localConfig by viewModel.localConfig.collectAsStateWithLifecycle()
    val requestChannelSet by viewModel.requestChannelSet.collectAsStateWithLifecycle()
    if (connectionState.isConnected()) {
        requestChannelSet?.let { newChannelSet ->
            ScannedQrCodeDialog(viewModel, newChannelSet)
        }
    }

    VersionChecks(viewModel)

    val title by viewModel.title.collectAsStateWithLifecycle()

    val alertDialogState by viewModel.currentAlert.collectAsStateWithLifecycle()
    alertDialogState?.let { state ->
        if (state.choices.isNotEmpty()) {
            MultipleChoiceAlertDialog(
                title = state.title,
                message = state.message,
                choices = state.choices,
                onDismissRequest = { state.onDismiss?.let { it() } },
            )
        } else {
            SimpleAlertDialog(
                title = state.title,
                message = state.message,
                html = state.html,
                onConfirmRequest = { state.onConfirm?.let { it() } },
                onDismissRequest = { state.onDismiss?.let { it() } },
            )
        }
    }

    val traceRouteResponse by viewModel.tracerouteResponse.observeAsState()
    traceRouteResponse?.let { response ->
        SimpleAlertDialog(
            title = R.string.traceroute,
            text = {
                Text(text = response)
            },
            dismissText = stringResource(id = R.string.okay),
            onDismiss = { viewModel.clearTracerouteResponse() }
        )
    }
    val navSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val topLevelDestination = TopLevelDestination.fromNavDestination(currentDestination)
    NavigationSuiteScaffold(
        modifier = Modifier.safeDrawingPadding(),
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == topLevelDestination
                val isConnectionsRoute = destination == TopLevelDestination.Connections
                item(
                    icon = {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text(
                                        if (isConnectionsRoute) {
                                            connectionState.getTooltipString()
                                        } else {
                                            stringResource(id = destination.label)
                                        },
                                    )
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            TopLevelNavIcon(destination, connectionState)
                        }
                    },
                    selected = isSelected,
                    label = {
                        if (navSuiteType != NavigationSuiteType.ShortNavigationBarCompact) {
                            Text(stringResource(id = destination.label))
                        }
                    },
                    onClick = {
                        if (!isSelected) {
                            navController.navigate(destination.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            MainAppBar(
                title = title,
                isManaged = localConfig.security.isManaged,
                navController = navController,
                onAction = { action ->
                    when (action) {
                        MainMenuAction.DEBUG -> navController.navigate(Route.DebugPanel)
                        MainMenuAction.RADIO_CONFIG -> navController.navigate(RadioConfigRoutes.RadioConfig())
                        MainMenuAction.QUICK_CHAT -> navController.navigate(ContactsRoutes.QuickChat)
                        else -> onAction(action)
                    }
                },
            )
            NavGraph(
                uIViewModel = viewModel,
                navController = navController,
            )
        }
    }
}

@Composable
private fun VersionChecks(
    viewModel: UIViewModel,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val latestStableFirmwareRelease by
    viewModel.latestStableFirmwareRelease.collectAsState(DeviceVersion("2.6.4"))
    // Check if the device is running an old app version or firmware version
    LaunchedEffect(connectionState, myNodeInfo) {
        if (connectionState == MeshService.ConnectionState.CONNECTED) {
            myNodeInfo?.let { info ->
                val isOld = info.minAppVersion > BuildConfig.VERSION_CODE
                val curVer = DeviceVersion(info.firmwareVersion ?: "0.0.0")
                if (isOld) {
                    viewModel.showAlert(
                        context.getString(R.string.app_too_old),
                        context.getString(R.string.must_update),
                        dismissable = false,
                        onConfirm = {
                            val service = viewModel.meshService ?: return@showAlert
                            MeshService.changeDeviceAddress(context, service, "n")
                        }
                    )
                } else if (curVer < MeshService.absoluteMinDeviceVersion) {
                    val title = context.getString(R.string.firmware_too_old)
                    val message = context.getString(R.string.firmware_old)
                    viewModel.showAlert(
                        title = title,
                        html = message,
                        dismissable = false,
                        onConfirm = {
                            val service = viewModel.meshService ?: return@showAlert
                            MeshService.changeDeviceAddress(context, service, "n")
                        }
                    )
                } else if (curVer < MeshService.minDeviceVersion) {
                    val title = context.getString(R.string.should_update_firmware)
                    val message =
                        context.getString(
                            R.string.should_update,
                            latestStableFirmwareRelease.asString
                        )
                    viewModel.showAlert(
                        title = title,
                        message = message,
                        dismissable = false,
                        onConfirm = {}
                    )
                }
            }
        }
    }
}

enum class MainMenuAction(@StringRes val stringRes: Int) {
    DEBUG(R.string.debug_panel),
    RADIO_CONFIG(R.string.radio_configuration),
    EXPORT_MESSAGES(R.string.save_messages),
    THEME(R.string.theme),
    LANGUAGE(R.string.preferences_language),
    SHOW_INTRO(R.string.intro_show),
    QUICK_CHAT(R.string.quick_chat),
    ABOUT(R.string.about),
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
private fun MainAppBar(
    title: String,
    isManaged: Boolean,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onAction: (MainMenuAction) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val canNavigateBack = navController.previousBackStackEntry != null
    val isTopLevelRoute = currentDestination?.isTopLevel() == true
    val navigateUp: () -> Unit = navController::navigateUp
    if (currentDestination?.hasRoute<ContactsRoutes.Messages>() == true) {
        return
    }
    TopAppBar(
        title = {
            when {
                currentDestination == null || isTopLevelRoute -> {
                    Text(
                        text = stringResource(id = R.string.app_name),
                    )
                }

                currentDestination.hasRoute<Route.DebugPanel>() ->
                    Text(
                        stringResource(id = R.string.debug_panel),
                    )

                currentDestination.hasRoute<ContactsRoutes.QuickChat>() ->
                    Text(
                        stringResource(id = R.string.quick_chat),
                    )

                currentDestination.hasRoute<ContactsRoutes.Share>() ->
                    Text(
                        stringResource(id = R.string.share_to),
                    )

                currentDestination.showLongNameTitle() -> {
                    Text(
                        title,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = if (canNavigateBack && !isTopLevelRoute) {
            {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_back),
                    )
                }
            }
        } else {
            {
                IconButton(
                    enabled = false,
                    onClick = { },
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.app_icon),
                        contentDescription = stringResource(id = R.string.application_icon),
                    )
                }
            }
        },
        actions = {
            when {
                currentDestination == null || isTopLevelRoute ->
                    MainMenuActions(isManaged, onAction)

                currentDestination.hasRoute<Route.DebugPanel>() ->
                    DebugMenuActions()

                else -> {}
            }
        },
    )
}

@Composable
private fun MainMenuActions(
    isManaged: Boolean,
    onAction: (MainMenuAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showMenu = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Overflow menu",
        )
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 1f)),
    ) {
        MainMenuAction.entries.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(id = action.stringRes)) },
                onClick = {
                    onAction(action)
                    showMenu = false
                },
                enabled = when (action) {
                    MainMenuAction.RADIO_CONFIG -> !isManaged
                    else -> true
                },
            )
        }
    }
}

@Composable
private fun MeshService.ConnectionState.getConnectionColor(): Color {
    return when (this) {
        MeshService.ConnectionState.CONNECTED -> Color(color = 0xFF30C047)
        MeshService.ConnectionState.DEVICE_SLEEP -> MaterialTheme.colorScheme.tertiary
        MeshService.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
    }
}

private fun MeshService.ConnectionState.getConnectionIcon(): ImageVector {
    return when (this) {
        MeshService.ConnectionState.CONNECTED -> Icons.TwoTone.CloudDone
        MeshService.ConnectionState.DEVICE_SLEEP -> Icons.TwoTone.CloudUpload
        MeshService.ConnectionState.DISCONNECTED -> Icons.TwoTone.CloudOff
    }
}

@Composable
private fun MeshService.ConnectionState.getTooltipString(): String {
    return when (this) {
        MeshService.ConnectionState.CONNECTED -> stringResource(R.string.connected)
        MeshService.ConnectionState.DEVICE_SLEEP -> stringResource(R.string.device_sleeping)
        MeshService.ConnectionState.DISCONNECTED -> stringResource(R.string.disconnected)
    }
}

@Composable
private fun TopLevelNavIcon(
    dest: TopLevelDestination,
    connectionState: MeshService.ConnectionState
) {
    when (dest) {
        TopLevelDestination.Connections -> Icon(
            imageVector = connectionState.getConnectionIcon(),
            contentDescription = stringResource(id = dest.label),
            tint = connectionState.getConnectionColor(),
        )

        else -> Icon(
            imageVector = dest.icon,
            contentDescription = stringResource(id = dest.label),
        )
    }
}
