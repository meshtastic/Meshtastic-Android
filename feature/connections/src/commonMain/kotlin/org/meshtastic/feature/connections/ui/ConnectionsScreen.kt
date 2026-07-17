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
package org.meshtastic.feature.connections.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.FirmwareUpdateDestination
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.navigation.FirmwareRoute
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_disabled
import org.meshtastic.core.resources.connections
import org.meshtastic.core.resources.firmware_event_ended_banner
import org.meshtastic.core.resources.firmware_event_ended_button
import org.meshtastic.core.resources.firmware_recovery_banner
import org.meshtastic.core.resources.firmware_recovery_button
import org.meshtastic.core.resources.firmware_recovery_dismiss
import org.meshtastic.core.resources.firmware_update_available
import org.meshtastic.core.resources.firmware_update_notification_android
import org.meshtastic.core.resources.firmware_update_notification_flasher
import org.meshtastic.core.resources.firmware_update_open
import org.meshtastic.core.resources.firmware_update_open_flasher
import org.meshtastic.core.resources.no_device_selected
import org.meshtastic.core.resources.open_bluetooth_settings
import org.meshtastic.core.resources.open_wifi_settings
import org.meshtastic.core.resources.set_your_region
import org.meshtastic.core.resources.unknown_device
import org.meshtastic.core.resources.wifi_unavailable
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.RecoveryCard
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.icon.SystemUpdate
import org.meshtastic.core.ui.util.LocalEventBranding
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.hasEnded
import org.meshtastic.core.ui.util.isBluetoothDisabled
import org.meshtastic.core.ui.util.isWifiUnavailable
import org.meshtastic.core.ui.util.rememberBluetoothPermissionState
import org.meshtastic.core.ui.util.rememberLocalNetworkPermissionState
import org.meshtastic.core.ui.util.rememberOpenBluetoothSettings
import org.meshtastic.core.ui.util.rememberOpenWifiSettings
import org.meshtastic.core.ui.util.shouldShowWifiUnavailableBanner
import org.meshtastic.core.ui.viewmodel.ConnectionStatus
import org.meshtastic.core.ui.viewmodel.ConnectionsViewModel
import org.meshtastic.feature.connections.NO_DEVICE_SELECTED
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.ui.components.ConnectingDeviceInfo
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedInfo
import org.meshtastic.feature.connections.ui.components.DeviceList
import org.meshtastic.feature.connections.ui.components.TransportSelector
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.getNavRouteFrom
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog

/**
 * Fixed minimum height for the "connected device" card at the top of the Connections screen. Shared across the three UI
 * states (NO_DEVICE, CONNECTING, CONNECTED_WITH_NODE) so the card never collapses or jumps size between state
 * transitions. Sized to comfortably fit the CONNECTED state (battery/RSSI row + node row + disconnect button).
 */
private val CardMinHeight = 100.dp

/** Composable screen for managing device connections (BLE, TCP, USB). It displays connection status. */
@OptIn(ExperimentalMaterial3Api::class)
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
    val firmwareUpdateNotice by connectionsViewModel.firmwareUpdateNotice.collectAsStateWithLifecycle()
    val regionUnset by connectionsViewModel.regionUnset.collectAsStateWithLifecycle()
    val sessionAuthorized by connectionsViewModel.sessionAuthorized.collectAsStateWithLifecycle()

    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()
    val persistedDeviceName by scanModel.persistedDeviceName.collectAsStateWithLifecycle()
    val pendingRecovery by scanModel.pendingRecovery.collectAsStateWithLifecycle()

    val bleDevices by scanModel.bleDevicesForUi.collectAsStateWithLifecycle()
    val discoveredTcpDevices by scanModel.discoveredTcpDevicesForUi.collectAsStateWithLifecycle()
    val recentTcpDevices by scanModel.recentTcpDevicesForUi.collectAsStateWithLifecycle()
    val usbDevices by scanModel.usbDevicesForUi.collectAsStateWithLifecycle()
    val isBleScanning by scanModel.isBleScanning.collectAsStateWithLifecycle()
    val isNetworkScanning by scanModel.isNetworkScanning.collectAsStateWithLifecycle()
    val activeTransport by scanModel.activeTransport.collectAsStateWithLifecycle()
    val bleAutoScan by scanModel.bleAutoScan.collectAsStateWithLifecycle()
    val networkAutoScan by scanModel.networkAutoScan.collectAsStateWithLifecycle()

    // Android 17 (API 37) gates NSD/mDNS behind ACCESS_LOCAL_NETWORK. Without this prompt the platform falls back to
    // the system "Choose a device to connect" picker on every discoverServices() call. The reactive state lets the
    // network-scan toggle request in-context and route a permanent denial to settings.
    val localNetworkPermission = rememberLocalNetworkPermissionState()
    val bluetoothPermission = rememberBluetoothPermissionState()

    // Adapter-state, distinct from permission state: a permission can be granted while Bluetooth is off or the device
    // is off Wi-Fi. Detected separately so the UI can route to the adapter's settings rather than re-prompting.
    val bluetoothDisabled = isBluetoothDisabled()
    val wifiUnavailable = isWifiUnavailable()
    val openBluetoothSettings = rememberOpenBluetoothSettings()
    val openWifiSettings = rememberOpenWifiSettings()
    val uriHandler = LocalUriHandler.current

    // Auto-start BLE discovery when the screen is visible (lifecycle ≥ STARTED) and the user has previously opted in.
    // ScannerViewModel skips screen-entry discovery when a selected device can reconnect through the transport's
    // fresh-advertisement scan. LifecycleStartEffect stops scanning on ON_STOP (app backgrounded) and restarts on
    // ON_START — preventing continuous background BLE radio usage that drains the battery.
    // Keyed on the active pane so a persisted TCP/USB pane loaded after first composition disposes this effect and
    // stops an initially eligible BLE scan. The toggle handler starts/stops scans directly; this effect owns lifecycle
    // cleanup, while the LaunchedEffect below handles async preference loading without disposing the lifecycle owner.
    LifecycleStartEffect(activeTransport) {
        if (activeTransport == DeviceType.BLE && bleAutoScan && !isBleScanning) {
            scanModel.startBleAutoScan()
        }
        onStopOrDispose { scanModel.stopBleScan() }
    }

    LaunchedEffect(activeTransport, bleAutoScan) {
        if (activeTransport == DeviceType.BLE && bleAutoScan && !isBleScanning) {
            scanModel.startBleAutoScan()
        }
    }

    // Keyed on active pane and permission status so the lifecycle owner re-fires when the user grants local-network
    // permission or the persisted Network pane loads. The separate LaunchedEffect handles later pref arrival without a
    // dispose+restart cycle on manual scan preference writes.
    LifecycleStartEffect(activeTransport, localNetworkPermission.isGranted) {
        if (
            activeTransport == DeviceType.TCP &&
            networkAutoScan &&
            localNetworkPermission.isGranted &&
            !isNetworkScanning
        ) {
            scanModel.startNetworkAutoScan()
        }
        onStopOrDispose { scanModel.stopNetworkScan() }
    }

    LaunchedEffect(activeTransport, localNetworkPermission.isGranted, networkAutoScan) {
        if (
            activeTransport == DeviceType.TCP &&
            networkAutoScan &&
            localNetworkPermission.isGranted &&
            !isNetworkScanning
        ) {
            scanModel.startNetworkAutoScan()
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

                        firmwareUpdateNotice?.let { notice ->
                            FirmwareUpdateNoticeCard(
                                notice = notice,
                                onAction = {
                                    when (notice.destination) {
                                        FirmwareUpdateDestination.AndroidUpdate ->
                                            onConfigNavigate(FirmwareRoute.FirmwareUpdate)

                                        FirmwareUpdateDestination.MeshtasticFlasher ->
                                            uriHandler.openUri("https://flasher.meshtastic.org")
                                    }
                                },
                            )
                        }

                        // A device stranded in bootloader mode by an interrupted update can be re-flashed without
                        // reconnecting first. Shown only while disconnected so the Firmware screen enters its recovery
                        // path (it uses the live connection when connected); cleared automatically once the device
                        // returns on its own.
                        pendingRecovery
                            ?.takeIf { connectionState !is ConnectionState.Connected }
                            ?.let { recovery ->
                                Spacer(modifier = Modifier.height(8.dp))
                                RecoveryCard(
                                    message = stringResource(Res.string.firmware_recovery_banner, recovery.deviceName),
                                    actionLabel = stringResource(Res.string.firmware_recovery_button),
                                    onAction = { onConfigNavigate(FirmwareRoute.FirmwareUpdate) },
                                    actionIcon = MeshtasticIcons.Bluetooth,
                                    // Let the user dismiss a recovery that can't succeed (e.g. an unflashable stock
                                    // bootloader) so it doesn't nag forever; it otherwise only clears on
                                    // reconnect/success.
                                    onDismiss = { scanModel.dismissRecovery() },
                                    dismissContentDescription = stringResource(Res.string.firmware_recovery_dismiss),
                                )
                            }

                        // Once an event is over, nudge users still on that event's firmware back to standard
                        // firmware. Driven purely by the metadata end date (LocalEventBranding is only populated
                        // while connected to event firmware), so it appears whenever an ended-event device is
                        // connected and disappears on its own once the device is re-flashed to vanilla. Not
                        // dismissable — it stays until the underlying condition is actually resolved.
                        LocalEventBranding.current
                            ?.takeIf { it.hasEnded() }
                            ?.let { endedEvent ->
                                Spacer(modifier = Modifier.height(8.dp))
                                RecoveryCard(
                                    message =
                                    stringResource(Res.string.firmware_event_ended_banner, endedEvent.displayName),
                                    actionLabel = stringResource(Res.string.firmware_event_ended_button),
                                    onAction = { onConfigNavigate(FirmwareRoute.FirmwareUpdate) },
                                )
                            }

                        // Region warning sits outside the animated card so it does not affect the
                        // CONNECTED ↔ CONNECTING ↔ NO_DEVICE size transition.
                        val isPhysicalDevice =
                            selectedDevice != InterfaceId.MOCK.id.toString() &&
                                selectedDevice != InterfaceId.REPLAY.id.toString()
                        if (
                            uiState == ConnectionUiState.CONNECTED_WITH_NODE &&
                            regionUnset &&
                            sessionAuthorized &&
                            isPhysicalDevice
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

                        // Transport selector sits between the connection card and device list; it controls only the
                        // visible discovery pane, not the globally selected/connected device shown above.
                        TransportSelector(
                            activeTransport = activeTransport,
                            onSelectTransport = scanModel::selectTransport,
                        )

                        // Adapter-off hints: shown only when the relevant permission is granted but the radio/network
                        // is unavailable, so they don't overlap the permission-recovery flow on the scan toggles.
                        // The WiFi-unavailable banner only renders while a network scan is actively running —
                        // discovery is the only moment the user needs to know WiFi is missing. The auto-scan case is
                        // covered because `isNetworkScanning` is true during auto-scan regardless of pane state.
                        if (activeTransport == DeviceType.BLE && bluetoothPermission.isGranted && bluetoothDisabled) {
                            RecoveryCard(
                                message = stringResource(Res.string.bluetooth_disabled),
                                actionLabel = stringResource(Res.string.open_bluetooth_settings),
                                onAction = openBluetoothSettings,
                                actionIcon = MeshtasticIcons.Bluetooth,
                            )
                        }
                        if (
                            activeTransport == DeviceType.TCP &&
                            shouldShowWifiUnavailableBanner(
                                isNetworkScanning = isNetworkScanning,
                                localNetworkPermissionGranted = localNetworkPermission.isGranted,
                                wifiUnavailable = wifiUnavailable,
                                discoveredTcpDevicesEmpty = discoveredTcpDevices.isEmpty(),
                            )
                        ) {
                            RecoveryCard(
                                message = stringResource(Res.string.wifi_unavailable),
                                actionLabel = stringResource(Res.string.open_wifi_settings),
                                onAction = openWifiSettings,
                            )
                        }
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
                                activeTransport = activeTransport,
                                onSelectDevice = { scanModel.onSelected(it) },
                                onToggleBleScan = {
                                    when {
                                        // Always allow stopping an in-progress scan.
                                        isBleScanning -> scanModel.toggleBleScan()

                                        // Granted but the radio is off — scanning can't work, so open BT settings.
                                        bluetoothPermission.isGranted && bluetoothDisabled -> openBluetoothSettings()

                                        bluetoothPermission.isGranted -> scanModel.toggleBleScan()

                                        // Permanently denied: the system won't prompt again, so send to settings.
                                        bluetoothPermission.status == PermissionStatus.PERMANENTLY_DENIED ->
                                            bluetoothPermission.openAppSettings()

                                        // Request in-context; once granted the user can start scanning.
                                        else -> bluetoothPermission.request()
                                    }
                                },
                                onToggleNetworkScan = {
                                    when {
                                        isNetworkScanning || localNetworkPermission.isGranted ->
                                            scanModel.toggleNetworkScan()

                                        localNetworkPermission.status == PermissionStatus.PERMANENTLY_DENIED ->
                                            localNetworkPermission.openAppSettings()

                                        else -> {
                                            // Prefer requesting the runtime grant over letting the platform fall back
                                            // to the system NSD picker. Persist the user's intent so that if they
                                            // grant after the prompt, the scan starts via the LifecycleStartEffect and
                                            // stays on for next session.
                                            scanModel.persistNetworkAutoScanIntent(true)
                                            localNetworkPermission.request()
                                        }
                                    }
                                },
                                onAddManualAddress = { _, fullAddress ->
                                    scanModel.connectToManualAddress(fullAddress)
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

/** Informational, non-dismissible nudge for a connected device with a newer stable firmware release. */
@Composable
private fun FirmwareUpdateNoticeCard(notice: FirmwareUpdateNotice, onAction: () -> Unit) {
    val actionLabel =
        stringResource(
            when (notice.destination) {
                FirmwareUpdateDestination.AndroidUpdate -> Res.string.firmware_update_open
                FirmwareUpdateDestination.MeshtasticFlasher -> Res.string.firmware_update_open_flasher
            },
        )
    val message =
        stringResource(
            when (notice.destination) {
                FirmwareUpdateDestination.AndroidUpdate -> Res.string.firmware_update_notification_android
                FirmwareUpdateDestination.MeshtasticFlasher -> Res.string.firmware_update_notification_flasher
            },
            notice.currentVersion,
            notice.stableVersion,
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = MeshtasticIcons.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = stringResource(Res.string.firmware_update_available),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(modifier = Modifier.padding(top = 12.dp), onClick = onAction) {
                    Icon(imageVector = MeshtasticIcons.SystemUpdate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel)
                }
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
