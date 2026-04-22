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
package org.meshtastic.feature.connections.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connections
import org.meshtastic.core.resources.no_device_selected
import org.meshtastic.core.resources.set_your_region
import org.meshtastic.core.resources.unknown_device
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.util.isLocalNetworkPermissionGranted
import org.meshtastic.core.ui.util.rememberRequestLocalNetworkPermission
import org.meshtastic.core.ui.viewmodel.ConnectionStatus
import org.meshtastic.core.ui.viewmodel.ConnectionsViewModel
import org.meshtastic.feature.connections.MOCK_DEVICE_PREFIX
import org.meshtastic.feature.connections.NO_DEVICE_SELECTED
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.TCP_DEVICE_PREFIX
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.ui.components.ConnectingDeviceInfo
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedInfo
import org.meshtastic.feature.connections.ui.components.DeviceList
import org.meshtastic.feature.connections.ui.components.TransportFilterChips
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.getNavRouteFrom
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import kotlin.uuid.ExperimentalUuidApi

/**
 * Fixed minimum height for the "connected device" card at the top of the Connections screen. Shared across the three UI
 * states (NO_DEVICE, CONNECTING, CONNECTED_WITH_NODE) so the card never collapses or jumps size between state
 * transitions. Sized to comfortably fit the CONNECTED state (battery/RSSI row + node row + disconnect button).
 */
private val CardMinHeight = 100.dp

/** Composable screen for managing device connections (BLE, TCP, USB). It displays connection status. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ModifierMissing", "ComposableParamOrder")
@Composable
fun ConnectionsScreen(
    connectionsViewModel: ConnectionsViewModel = koinViewModel(),
    scanModel: ScannerViewModel = koinViewModel(),
    radioConfigViewModel: RadioConfigViewModel = koinViewModel(),
    onClickNodeChip: (Int) -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    onConfigNavigate: (Route) -> Unit,
) {
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val connectionProgress by scanModel.connectionProgressText.collectAsStateWithLifecycle()
    val connectionStatus by connectionsViewModel.connectionStatus.collectAsStateWithLifecycle()
    val connectionState by connectionsViewModel.connectionState.collectAsStateWithLifecycle()
    val ourNode by connectionsViewModel.ourNodeForDisplay.collectAsStateWithLifecycle()
    val regionUnset by connectionsViewModel.regionUnset.collectAsStateWithLifecycle()

    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()
    val persistedDeviceName by scanModel.persistedDeviceName.collectAsStateWithLifecycle()

    val bleDevices by scanModel.bleDevicesForUi.collectAsStateWithLifecycle()
    val discoveredTcpDevices by scanModel.discoveredTcpDevicesForUi.collectAsStateWithLifecycle()
    val recentTcpDevices by scanModel.recentTcpDevicesForUi.collectAsStateWithLifecycle()
    val usbDevices by scanModel.usbDevicesForUi.collectAsStateWithLifecycle()
    val isBleScanning by scanModel.isBleScanning.collectAsStateWithLifecycle()
    val isNetworkScanning by scanModel.isNetworkScanning.collectAsStateWithLifecycle()

    val bleAutoScan by scanModel.bleAutoScan.collectAsStateWithLifecycle()
    val networkAutoScan by scanModel.networkAutoScan.collectAsStateWithLifecycle()
    val showBleTransport by scanModel.showBleTransport.collectAsStateWithLifecycle()
    val showNetworkTransport by scanModel.showNetworkTransport.collectAsStateWithLifecycle()
    val showUsbTransport by scanModel.showUsbTransport.collectAsStateWithLifecycle()
    val localNetworkPermissionGranted = isLocalNetworkPermissionGranted()

    // Android 17 (API 37) gates NSD/mDNS behind ACCESS_LOCAL_NETWORK. Without this prompt the platform
    // falls back to the system "Choose a device to connect" picker on every discoverServices() call.
    // Granting the permission upfront lets discovery run silently in-app.
    val requestLocalNetworkPermission =
        rememberRequestLocalNetworkPermission(
            onGranted = { scanModel.startNetworkScan() },
            onDenied = { scanModel.stopNetworkScan() },
        )

    // Auto-start scans on screen entry when the user has previously opted in via the toggle. Stop on exit so we don't
    // drain battery in the background. Network auto-start is additionally gated on the runtime local-network grant so
    // we don't trigger the system picker for users who declined the permission.
    DisposableEffect(localNetworkPermissionGranted) {
        if (bleAutoScan) scanModel.startBleScan()
        if (networkAutoScan && localNetworkPermissionGranted) scanModel.startNetworkScan()
        onDispose {
            scanModel.stopBleScan()
            scanModel.stopNetworkScan()
        }
    }

    /* Animate waiting for the configurations */
    var isWaiting by remember { mutableStateOf(false) }
    if (isWaiting) {
        PacketResponseStateDialog(
            state = radioConfigState.responseState,
            onDismiss = {
                isWaiting = false
                radioConfigViewModel.clearPacketResponse()
            },
            onComplete = {
                getNavRouteFrom(radioConfigState.route)?.let { route ->
                    isWaiting = false
                    radioConfigViewModel.clearPacketResponse()
                    if (route == SettingsRoute.LoRa) {
                        onConfigNavigate(SettingsRoute.LoRa)
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.connections),
                ourNode = ourNode,
                showNodeChip = ourNode != null && connectionState is ConnectionState.Connected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                AdaptiveTwoPane(
                    first = {
                        val uiState =
                            when {
                                connectionState is ConnectionState.Connected && ourNode != null ->
                                    ConnectionUiState.CONNECTED_WITH_NODE

                                connectionState is ConnectionState.Connected ||
                                    connectionState == ConnectionState.Connecting ||
                                    selectedDevice != NO_DEVICE_SELECTED -> ConnectionUiState.CONNECTING

                                else -> ConnectionUiState.NO_DEVICE
                            }

                        // ── Connected Device slot ──
                        // A single Card shell hosts all three states. `animateContentSize` smooths any
                        // height changes, while `heightIn(min = CardMinHeight)` reserves a stable floor so
                        // the card never collapses between states.
                        Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                            AnimatedContent(
                                targetState = uiState,
                                label = "connection_state",
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { state ->
                                Box(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = CardMinHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (state) {
                                        ConnectionUiState.CONNECTED_WITH_NODE ->
                                            ConnectedDeviceContent(
                                                ourNode = ourNode,
                                                selectedDevice = selectedDevice,
                                                bleDevices = bleDevices,
                                                onNavigateToNodeDetails = onNavigateToNodeDetails,
                                                onClickDisconnect = { scanModel.disconnect() },
                                            )

                                        ConnectionUiState.CONNECTING ->
                                            ConnectingDeviceContent(
                                                selectedDevice = selectedDevice,
                                                persistedDeviceName = persistedDeviceName,
                                                bleDevices = bleDevices,
                                                discoveredTcpDevices = discoveredTcpDevices,
                                                recentTcpDevices = recentTcpDevices,
                                                usbDevices = usbDevices,
                                                connectionStatus = connectionStatus,
                                                connectionProgress = connectionProgress,
                                                onClickDisconnect = { scanModel.disconnect() },
                                            )

                                        else -> NoDeviceContent()
                                    }
                                }
                            }
                        }

                        // Region warning sits outside the animated card so it does not affect the
                        // CONNECTED ↔ CONNECTING ↔ NO_DEVICE size transition.
                        if (
                            uiState == ConnectionUiState.CONNECTED_WITH_NODE &&
                            regionUnset &&
                            selectedDevice != MOCK_DEVICE_PREFIX
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    leadingIcon = MeshtasticIcons.Language,
                                    text = stringResource(Res.string.set_your_region),
                                    onClick = {
                                        isWaiting = true
                                        radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
                                    },
                                )
                            }
                        }

                        // Inclusive transport-visibility filter chips. Sit between the connection card and the
                        // device list so users can hide entire transports they're not using.
                        TransportFilterChips(
                            showBle = showBleTransport,
                            showNetwork = showNetworkTransport,
                            showUsb = showUsbTransport,
                            onToggleBle = { scanModel.setShowBleTransport(!showBleTransport) },
                            onToggleNetwork = { scanModel.setShowNetworkTransport(!showNetworkTransport) },
                            onToggleUsb = { scanModel.setShowUsbTransport(!showUsbTransport) },
                        )
                    },
                    second = {
                        // ── Unified device list ──
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            DeviceList(
                                connectionState = connectionState,
                                selectedDevice = selectedDevice,
                                bleDevices = bleDevices,
                                usbDevices = usbDevices,
                                discoveredTcpDevices = discoveredTcpDevices,
                                recentTcpDevices = recentTcpDevices,
                                isBleScanning = isBleScanning,
                                isNetworkScanning = isNetworkScanning,
                                showBleSection = showBleTransport,
                                showNetworkSection = showNetworkTransport,
                                showUsbSection = showUsbTransport,
                                onSelectDevice = { scanModel.onSelected(it) },
                                onToggleBleScan = { scanModel.toggleBleScan() },
                                onToggleNetworkScan = {
                                    if (isNetworkScanning || localNetworkPermissionGranted) {
                                        scanModel.toggleNetworkScan()
                                    } else {
                                        // Prefer requesting the runtime grant over letting the platform fall
                                        // back to the system NSD picker. Persist the user's intent so that if
                                        // they grant after the prompt, the scan starts via the launcher's
                                        // onGranted callback and stays on for next session.
                                        scanModel.persistNetworkAutoScanIntent(true)
                                        requestLocalNetworkPermission()
                                    }
                                },
                                onAddManualAddress = { _, fullAddress ->
                                    val displayAddress = fullAddress.removePrefix(TCP_DEVICE_PREFIX)
                                    scanModel.addRecentAddress(fullAddress, displayAddress)
                                    scanModel.changeDeviceAddress(fullAddress)
                                },
                                onRemoveRecentAddress = { scanModel.removeRecentAddress(it.fullAddress) },
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Body for the CONNECTED state — sits inside the shared outer Card in [ConnectionsScreen]. */
@Composable
private fun ConnectedDeviceContent(
    ourNode: org.meshtastic.core.model.Node?,
    selectedDevice: String,
    bleDevices: List<DeviceListEntry>,
    onNavigateToNodeDetails: (Int) -> Unit,
    onClickDisconnect: () -> Unit,
) {
    ourNode?.let { node ->
        CurrentlyConnectedInfo(
            node = node,
            bleDevice = bleDevices.find { it.fullAddress == selectedDevice } as DeviceListEntry.Ble?,
            onNavigateToNodeDetails = onNavigateToNodeDetails,
            onClickDisconnect = onClickDisconnect,
        )
    }
}

/** Body for the CONNECTING state — sits inside the shared outer Card in [ConnectionsScreen]. */
@Composable
private fun ConnectingDeviceContent(
    selectedDevice: String,
    persistedDeviceName: String?,
    bleDevices: List<DeviceListEntry>,
    discoveredTcpDevices: List<DeviceListEntry>,
    recentTcpDevices: List<DeviceListEntry>,
    usbDevices: List<DeviceListEntry>,
    connectionStatus: ConnectionStatus,
    connectionProgress: String?,
    onClickDisconnect: () -> Unit,
) {
    val selectedEntry =
        bleDevices.find { it.fullAddress == selectedDevice }
            ?: discoveredTcpDevices.find { it.fullAddress == selectedDevice }
            ?: recentTcpDevices.find { it.fullAddress == selectedDevice }
            ?: usbDevices.find { it.fullAddress == selectedDevice }

    // Use the entry name if found in scan lists, otherwise fall back to the persisted name
    // from the last successful selection, and only show "Unknown Device" as a last resort.
    val name = selectedEntry?.name ?: persistedDeviceName ?: stringResource(Res.string.unknown_device)
    val address = selectedEntry?.address ?: selectedDevice

    ConnectingDeviceInfo(
        deviceName = name,
        deviceAddress = address,
        connectionStatus = connectionStatus,
        connectionProgress = connectionProgress,
        onClickDisconnect = onClickDisconnect,
    )
}

/** Body for the NO_DEVICE state — sits inside the shared outer Card in [ConnectionsScreen]. */
@Composable
private fun NoDeviceContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MeshtasticIcons.NoDevice,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = stringResource(Res.string.no_device_selected),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Visual state for the connection screen's [AnimatedContent] transition between the three card body variants. */
private enum class ConnectionUiState {
    /** No device is selected. */
    NO_DEVICE,

    /** A device is selected or we are actively connecting. */
    CONNECTING,

    /** Connected with node info available. */
    CONNECTED_WITH_NODE,
}
