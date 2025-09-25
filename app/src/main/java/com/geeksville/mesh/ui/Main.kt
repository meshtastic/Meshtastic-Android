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

@file:Suppress("MatchingDeclarationName")

package com.geeksville.mesh.ui

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.android.AddNavigationTracking
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.setAttributes
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.channelsGraph
import com.geeksville.mesh.navigation.connectionsGraph
import com.geeksville.mesh.navigation.contactsGraph
import com.geeksville.mesh.navigation.mapGraph
import com.geeksville.mesh.navigation.nodesGraph
import com.geeksville.mesh.navigation.settingsGraph
import com.geeksville.mesh.repository.radio.MeshActivity
import com.geeksville.mesh.service.ConnectionState
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.common.components.MultipleChoiceAlertDialog
import com.geeksville.mesh.ui.common.components.ScannedQrCodeDialog
import com.geeksville.mesh.ui.common.components.SimpleAlertDialog
import org.meshtastic.core.ui.icon.Conversations
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.theme.StatusColors.StatusBlue
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import com.geeksville.mesh.ui.connections.DeviceType
import com.geeksville.mesh.ui.connections.components.TopLevelNavIcon
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import com.geeksville.mesh.ui.sharing.SharedContactDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.navigation.ConnectionsRoutes
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.MapRoutes
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.R
import kotlin.reflect.KClass

enum class TopLevelDestination(@StringRes val label: Int, val icon: ImageVector, val route: Route) {
    Conversations(R.string.conversations, MeshtasticIcons.Conversations, ContactsRoutes.ContactsGraph),
    Nodes(R.string.nodes, MeshtasticIcons.Nodes, NodesRoutes.NodesGraph),
    Map(R.string.map, MeshtasticIcons.Map, MapRoutes.Map),
    Settings(R.string.bottom_nav_settings, MeshtasticIcons.Settings, SettingsRoutes.SettingsGraph()),
    Connections(R.string.connections, Icons.Rounded.Wifi, ConnectionsRoutes.ConnectionsGraph),
    ;

    companion object {
        fun NavDestination.isTopLevel(): Boolean = listOf<KClass<out Route>>(
            ContactsRoutes.Contacts::class,
            MapRoutes.Map::class,
            ConnectionsRoutes.Connections::class,
        )
            .any { this.hasRoute(it) }

        fun fromNavDestination(destination: NavDestination?): TopLevelDestination? =
            entries.find { dest -> destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MainScreen(uIViewModel: UIViewModel = hiltViewModel(), scanModel: BTScanModel = hiltViewModel()) {
    val navController = rememberNavController()
    val connectionState by uIViewModel.connectionState.collectAsStateWithLifecycle()
    val requestChannelSet by uIViewModel.requestChannelSet.collectAsStateWithLifecycle()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(connectionState, notificationPermissionState) {
            if (connectionState == ConnectionState.CONNECTED && !notificationPermissionState.status.isGranted) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }

    AddNavigationTracking(navController)

    if (connectionState == ConnectionState.CONNECTED) {
        requestChannelSet?.let { newChannelSet -> ScannedQrCodeDialog(uIViewModel, newChannelSet) }
    }

    VersionChecks(uIViewModel)

    val alertDialogState by uIViewModel.currentAlert.collectAsStateWithLifecycle()
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

    val clientNotification by uIViewModel.clientNotification.collectAsStateWithLifecycle()
    clientNotification?.let { notification ->
        var message = notification.message
        val compromisedKeys =
            if (notification.hasLowEntropyKey() || notification.hasDuplicatedPublicKey()) {
                message = stringResource(R.string.compromised_keys)
                true
            } else {
                false
            }
        SimpleAlertDialog(
            title = R.string.client_notification,
            text = { Text(text = message) },
            onConfirm = {
                if (compromisedKeys) {
                    navController.navigate(SettingsRoutes.Security)
                }
                uIViewModel.clearClientNotification(notification)
            },
        )
    }

    val traceRouteResponse by uIViewModel.tracerouteResponse.observeAsState()
    traceRouteResponse?.let { response ->
        SimpleAlertDialog(
            title = R.string.traceroute,
            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(text = response) } },
            dismissText = stringResource(id = R.string.okay),
            onDismiss = { uIViewModel.clearTracerouteResponse() },
        )
    }
    val navSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val topLevelDestination = TopLevelDestination.fromNavDestination(currentDestination)

    // State for determining the connection type icon to display
    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()

    // State for managing the glow animation around the Connections icon
    var currentGlowColor by remember { mutableStateOf(Color.Transparent) }
    val animatedGlowAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val capturedColorScheme = colorScheme // Capture current colorScheme instance for LaunchedEffect

    val sendColor = capturedColorScheme.StatusGreen
    val receiveColor = capturedColorScheme.StatusBlue
    LaunchedEffect(uIViewModel.meshActivity, capturedColorScheme) {
        uIViewModel.meshActivity.collectLatest { activity ->
            debug("MeshActivity Event: $activity, Current Alpha: ${animatedGlowAlpha.value}")

            val newTargetColor =
                when (activity) {
                    is MeshActivity.Send -> sendColor
                    is MeshActivity.Receive -> receiveColor
                }

            currentGlowColor = newTargetColor
            // Stop any existing animation and launch a new one.
            // Launching in a new coroutine ensures the collect block is not suspended.
            coroutineScope.launch {
                animatedGlowAlpha.stop() // Stop before snapping/animating
                animatedGlowAlpha.snapTo(1.0f) // Show glow instantly
                animatedGlowAlpha.animateTo(
                    targetValue = 0.0f, // Fade out
                    animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                )
            }
        }
    }

    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
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
                                            when (connectionState) {
                                                ConnectionState.CONNECTED -> stringResource(R.string.connected)
                                                ConnectionState.DEVICE_SLEEP -> stringResource(R.string.device_sleeping)
                                                ConnectionState.DISCONNECTED -> stringResource(R.string.disconnected)
                                            }
                                        } else {
                                            stringResource(id = destination.label)
                                        },
                                    )
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            val iconModifier =
                                if (isConnectionsRoute) {
                                    Modifier.drawWithCache {
                                        onDrawWithContent {
                                            drawContent()
                                            if (animatedGlowAlpha.value > 0f) {
                                                val glowRadius = size.minDimension
                                                drawCircle(
                                                    brush =
                                                    Brush.radialGradient(
                                                        colors =
                                                        listOf(
                                                            currentGlowColor.copy(
                                                                alpha = 0.8f * animatedGlowAlpha.value,
                                                            ),
                                                            currentGlowColor.copy(
                                                                alpha = 0.4f * animatedGlowAlpha.value,
                                                            ),
                                                            Color.Transparent,
                                                        ),
                                                        center = center,
                                                        radius = glowRadius,
                                                    ),
                                                    radius = glowRadius,
                                                    blendMode = BlendMode.Screen,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            Box(modifier = iconModifier) {
                                TopLevelNavIcon(destination, connectionState, DeviceType.fromAddress(selectedDevice))
                            }
                        }
                    },
                    selected = isSelected,
                    label = {
                        if (navSuiteType != NavigationSuiteType.ShortNavigationBarCompact) {
                            Text(stringResource(id = destination.label))
                        }
                    },
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) {
        Scaffold(snackbarHost = { SnackbarHost(uIViewModel.snackBarHostState) }) { _ ->
            Column(modifier = Modifier.fillMaxSize()) {
                var sharedContact: Node? by remember { mutableStateOf(null) }
                if (sharedContact != null) {
                    SharedContactDialog(contact = sharedContact, onDismiss = { sharedContact = null })
                }

                fun NavDestination.hasGlobalAppBar(): Boolean =
                    // List of screens to exclude from having the global app bar
                    listOf(
                        ConnectionsRoutes.Connections::class,
                        ContactsRoutes.Contacts::class,
                        MapRoutes.Map::class,
                        NodeDetailRoutes.NodeMap::class,
                        NodesRoutes.Nodes::class,
                        NodesRoutes.NodeDetail::class,
                        SettingsRoutes.Settings::class,
                        SettingsRoutes.AmbientLighting::class,
                        SettingsRoutes.LoRa::class,
                        SettingsRoutes.Security::class,
                        SettingsRoutes.Audio::class,
                        SettingsRoutes.Bluetooth::class,
                        SettingsRoutes.ChannelConfig::class,
                        SettingsRoutes.DetectionSensor::class,
                        SettingsRoutes.Display::class,
                        SettingsRoutes.Telemetry::class,
                        SettingsRoutes.Network::class,
                        SettingsRoutes.Paxcounter::class,
                        SettingsRoutes.Power::class,
                        SettingsRoutes.Position::class,
                        SettingsRoutes.User::class,
                        SettingsRoutes.StoreForward::class,
                        SettingsRoutes.MQTT::class,
                        SettingsRoutes.Serial::class,
                        SettingsRoutes.ExtNotification::class,
                        SettingsRoutes.CleanNodeDb::class,
                        SettingsRoutes.DebugPanel::class,
                        SettingsRoutes.RangeTest::class,
                        SettingsRoutes.CannedMessage::class,
                        SettingsRoutes.RemoteHardware::class,
                        SettingsRoutes.NeighborInfo::class,
                    )
                        .none { this.hasRoute(it) }

                AnimatedVisibility(visible = currentDestination?.hasGlobalAppBar() ?: false) {
                    MainAppBar(
                        viewModel = uIViewModel,
                        navController = navController,
                        onAction = { action ->
                            when (action) {
                                is NodeMenuAction.MoreDetails -> {
                                    navController.navigate(
                                        NodesRoutes.NodeDetailGraph(action.node.num),
                                        {
                                            launchSingleTop = true
                                            restoreState = true
                                        },
                                    )
                                }

                                is NodeMenuAction.Share -> sharedContact = action.node
                                else -> {}
                            }
                        },
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = NodesRoutes.NodesGraph,
                    modifier = Modifier.fillMaxSize().recalculateWindowInsets().safeDrawingPadding().imePadding(),
                ) {
                    contactsGraph(navController, uiViewModel = uIViewModel)
                    nodesGraph(navController, uiViewModel = uIViewModel)
                    mapGraph(navController, uiViewModel = uIViewModel)
                    channelsGraph(navController, uiViewModel = uIViewModel)
                    connectionsGraph(navController)
                    settingsGraph(navController)
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun VersionChecks(viewModel: UIViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val myFirmwareVersion = myNodeInfo?.firmwareVersion

    val firmwareEdition by viewModel.firmwareEdition.collectAsStateWithLifecycle(null)

    val currentFirmwareVersion by viewModel.firmwareVersion.collectAsStateWithLifecycle(null)

    val currentDeviceHardware by viewModel.deviceHardware.collectAsStateWithLifecycle(null)

    val latestStableFirmwareRelease by
        viewModel.latestStableFirmwareRelease.collectAsStateWithLifecycle(DeviceVersion("2.6.4"))
    LaunchedEffect(connectionState, firmwareEdition) {
        if (connectionState == ConnectionState.CONNECTED) {
            firmwareEdition?.let { edition ->
                debug("FirmwareEdition: ${edition.name}")
                when (edition) {
                    MeshProtos.FirmwareEdition.VANILLA -> {
                        // Handle any specific logic for VANILLA firmware edition if needed
                    }

                    else -> {
                        // Handle other firmware editions if needed
                    }
                }
            }
        }
    }

    LaunchedEffect(connectionState, currentFirmwareVersion, currentDeviceHardware) {
        if (connectionState == ConnectionState.CONNECTED) {
            if (currentDeviceHardware != null && currentFirmwareVersion != null) {
                setAttributes(currentFirmwareVersion!!, currentDeviceHardware!!)
            }
        }
    }

    // Check if the device is running an old app version or firmware version
    LaunchedEffect(connectionState, myNodeInfo) {
        if (connectionState == ConnectionState.CONNECTED) {
            myNodeInfo?.let { info ->
                val isOld = info.minAppVersion > BuildConfig.VERSION_CODE && BuildConfig.DEBUG.not()
                if (isOld) {
                    viewModel.showAlert(
                        context.getString(R.string.app_too_old),
                        context.getString(R.string.must_update),
                        dismissable = false,
                        onConfirm = {
                            val service = viewModel.meshService ?: return@showAlert
                            MeshService.changeDeviceAddress(context, service, "n")
                        },
                    )
                } else {
                    myFirmwareVersion?.let {
                        val curVer = DeviceVersion(it)
                        if (curVer < MeshService.absoluteMinDeviceVersion) {
                            val title = context.getString(R.string.firmware_too_old)
                            val message = context.getString(R.string.firmware_old)
                            viewModel.showAlert(
                                title = title,
                                html = message,
                                dismissable = false,
                                onConfirm = {
                                    val service = viewModel.meshService ?: return@showAlert
                                    MeshService.changeDeviceAddress(context, service, "n")
                                },
                            )
                        } else if (curVer < MeshService.minDeviceVersion) {
                            val title = context.getString(R.string.should_update_firmware)
                            val message =
                                context.getString(R.string.should_update, latestStableFirmwareRelease.asString)
                            viewModel.showAlert(title = title, message = message, dismissable = false, onConfirm = {})
                        }
                    }
                }
            }
        }
    }
}
