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
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.navigation.FirmwareRoute
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ble_scan_needs_location_services
import org.meshtastic.core.resources.bluetooth_disabled
import org.meshtastic.core.resources.bluetooth_permission_blocked_rationale
import org.meshtastic.core.resources.bluetooth_permission_blocked_rationale_pre31
import org.meshtastic.core.resources.bluetooth_permission_rationale
import org.meshtastic.core.resources.bluetooth_permission_rationale_pre31
import org.meshtastic.core.resources.bonding_failed_permissions
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.connections
import org.meshtastic.core.resources.disconnect
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
import org.meshtastic.core.resources.firmware_version
import org.meshtastic.core.resources.local_network_permission
import org.meshtastic.core.resources.local_network_permission_blocked_rationale
import org.meshtastic.core.resources.local_network_permission_rationale
import org.meshtastic.core.resources.no_device_selected
import org.meshtastic.core.resources.open_bluetooth_settings
import org.meshtastic.core.resources.open_location_settings
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.resources.open_wifi_settings
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.set_your_region
import org.meshtastic.core.resources.transmit_disabled
import org.meshtastic.core.resources.transmit_disabled_summary
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.resources.unknown_device
import org.meshtastic.core.resources.wifi_unavailable
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.PermissionRationaleDialog
import org.meshtastic.core.ui.component.PermissionRecoveryCard
import org.meshtastic.core.ui.component.RecoveryCard
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.CellTower
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.icon.SystemUpdate
import org.meshtastic.core.ui.util.LocalEventBranding
import org.meshtastic.core.ui.util.PermissionGateAction
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.bleScanRequiresLocationServices
import org.meshtastic.core.ui.util.hasEnded
import org.meshtastic.core.ui.util.isBluetoothDisabled
import org.meshtastic.core.ui.util.isGpsDisabled
import org.meshtastic.core.ui.util.isWifiUnavailable
import org.meshtastic.core.ui.util.permissionGateAction
import org.meshtastic.core.ui.util.rememberBluetoothPermissionState
import org.meshtastic.core.ui.util.rememberLocalNetworkPermissionState
import org.meshtastic.core.ui.util.rememberOpenBluetoothSettings
import org.meshtastic.core.ui.util.rememberOpenLocationSettings
import org.meshtastic.core.ui.util.rememberOpenWifiSettings
import org.meshtastic.core.ui.util.shouldShowWifiUnavailableBanner
import org.meshtastic.core.ui.viewmodel.ConnectionStatus
import org.meshtastic.core.ui.viewmodel.ConnectionsViewModel
import org.meshtastic.feature.connections.NO_DEVICE_SELECTED
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.ui.components.ConnectingDeviceInfo
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedInfo
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedText
import org.meshtastic.feature.connections.ui.components.DeviceList
import org.meshtastic.feature.connections.ui.components.EventFirmwareCard
import org.meshtastic.feature.connections.ui.components.TransportSelector

/**
 * Fixed minimum height for the "connected device" card at the top of the Connections screen. Shared across the three UI
 * states (NO_DEVICE, CONNECTING, CONNECTED_WITH_NODE) so the card never collapses or jumps size between state
 * transitions. Sized to comfortably fit the CONNECTED state (battery/RSSI row + node row + disconnect button).
 */
private val CardMinHeight = 100.dp

/** Whether the connected card's config warning cards (region, transmit) may render for this connection. */
internal fun canShowConfigWarnings(
    connectedWithNode: Boolean,
    activeNodeInfoReady: Boolean,
    lockdownState: LockdownState,
    isManaged: Boolean,
    isPhysicalDevice: Boolean,
): Boolean =
    connectedWithNode && activeNodeInfoReady && lockdownState.allowsConfigWrites && !isManaged && isPhysicalDevice

/** Applies connection policy and renders the actionable configuration-health cards it admits. */
@Composable
internal fun ConfigurationWarningCards(
    connectedWithNode: Boolean,
    activeNodeInfoReady: Boolean,
    lockdownState: LockdownState,
    isManaged: Boolean,
    isPhysicalDevice: Boolean,
    regionUnset: Boolean,
    txDisabled: Boolean,
    onConfigNavigate: (Route) -> Unit,
) {
    val showWarnings =
        canShowConfigWarnings(
            connectedWithNode = connectedWithNode,
            activeNodeInfoReady = activeNodeInfoReady,
            lockdownState = lockdownState,
            isManaged = isManaged,
            isPhysicalDevice = isPhysicalDevice,
        )

    Column {
        if (showWarnings && regionUnset) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingIcon = MeshtasticIcons.Language,
                    text = stringResource(Res.string.set_your_region),
                    // Navigate straight to the LoRa screen: it re-reads the route on entry and renders from the
                    // connect-time snapshot meanwhile, so pre-fetching behind a progress dialog here bought nothing and
                    // could strand the user on an empty dialog when the read completed before the dialog observed it.
                    onClick = { onConfigNavigate(SettingsRoute.LoRa) },
                )
            }
        }

        // An unset region already disables transmit and has its own card, so do not blame one root cause twice.
        if (showWarnings && txDisabled && !regionUnset) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingIcon = MeshtasticIcons.CellTower,
                    text = stringResource(Res.string.transmit_disabled),
                    supportingText = stringResource(Res.string.transmit_disabled_summary),
                    onClick = { onConfigNavigate(SettingsRoute.LoRa) },
                )
            }
        }
    }
}

/** Composable screen for managing device connections (BLE, TCP, USB). It displays connection status. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ModifierMissing", "ComposableParamOrder")
@Composable
fun ConnectionsScreen(
    connectionsViewModel: ConnectionsViewModel = koinViewModel(),
    scanModel: ScannerViewModel = koinViewModel(),
    onClickNodeChip: (Int) -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    onConfigNavigate: (Route) -> Unit,
) {
    val connectionProgress by scanModel.connectionProgressText.collectAsStateWithLifecycle()
    val connectionStatus by connectionsViewModel.connectionStatus.collectAsStateWithLifecycle()
    val connectionState by connectionsViewModel.connectionState.collectAsStateWithLifecycle()
    val ourNode by connectionsViewModel.ourNodeForDisplay.collectAsStateWithLifecycle()
    val firmwareUpdateNotice by connectionsViewModel.firmwareUpdateNotice.collectAsStateWithLifecycle()
    val regionUnset by connectionsViewModel.regionUnset.collectAsStateWithLifecycle()
    val txDisabled by connectionsViewModel.txDisabled.collectAsStateWithLifecycle()
    val activeNodeInfoReady by connectionsViewModel.activeNodeInfoReady.collectAsStateWithLifecycle()
    val lockdownState by connectionsViewModel.lockdownState.collectAsStateWithLifecycle()
    val localConfig by connectionsViewModel.localConfig.collectAsStateWithLifecycle()

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
    val blePermissionRefusal by scanModel.blePermissionRefusal.collectAsStateWithLifecycle()
    val bleAutoScan by scanModel.bleAutoScan.collectAsStateWithLifecycle()
    val networkAutoScan by scanModel.networkAutoScan.collectAsStateWithLifecycle()

    // Android 17 (API 37) gates NSD/mDNS behind ACCESS_LOCAL_NETWORK. Without this prompt the platform falls back to
    // the system "Choose a device to connect" picker on every discoverServices() call. The reactive state lets the
    // network-scan toggle request in-context and route a permanent denial to settings.
    val localNetworkPermission = rememberLocalNetworkPermissionState()
    val bluetoothPermission = rememberBluetoothPermissionState()

    // ACCESS_LOCAL_NETWORK gates the socket, not just discovery — a blocked local TCP connect times out rather than
    // failing fast — but a TCP address says nothing about locality: public-IP/port-forward/VPN radios need no
    // permission at all. Policy (see LocalNetworkGateAction): prompt when possible, warn when not, never block.
    // A connect issued while the prompt is up is stashed with the status it saw; the LaunchedEffect below resolves it
    // on the status transition the request produces — grant runs it, a denial warns and runs it anyway.
    var pendingTcpConnect by remember { mutableStateOf<Pair<PermissionStatus, () -> Unit>?>(null) }
    // A connect held back only while an educational dialog is up. Distinct from pendingTcpConnect, which waits on the
    // system prompt: this one has not asked the OS anything yet, and must still run if the user dismisses the dialog.
    var rationaleTcpConnect by remember { mutableStateOf<(() -> Unit)?>(null) }
    val gateTcpConnect: (connect: () -> Unit) -> Unit = { connect ->
        when (localNetworkGateAction(localNetworkPermission.status)) {
            LocalNetworkGateAction.PROCEED -> connect()

            LocalNetworkGateAction.REQUEST_PERMISSION -> {
                pendingTcpConnect = localNetworkPermission.status to connect
                localNetworkPermission.request()
            }

            LocalNetworkGateAction.SHOW_RATIONALE -> rationaleTcpConnect = connect

            // The pane already carries a permission card naming the fix, so no modal here. The connect runs regardless
            // — a public-IP or VPN radio was never subject to this permission, and the OS enforces it at the socket.
            LocalNetworkGateAction.PROCEED_WITH_WARNING -> connect()
        }
    }

    rationaleTcpConnect?.let { connect ->
        PermissionRationaleDialog(
            titleRes = Res.string.local_network_permission,
            rationaleRes = Res.string.local_network_permission_rationale,
            icon = MeshtasticIcons.Language,
            onConfirm = {
                rationaleTcpConnect = null
                pendingTcpConnect = localNetworkPermission.status to connect
                localNetworkPermission.request()
            },
            // Declining the explanation is not declining the connection. Never-block means never-block.
            onDismiss = {
                rationaleTcpConnect = null
                connect()
            },
        )
    }
    LaunchedEffect(localNetworkPermission.status) {
        val pending = pendingTcpConnect ?: return@LaunchedEffect
        when (resolvePendingTcpConnect(stashedStatus = pending.first, currentStatus = localNetworkPermission.status)) {
            PendingTcpConnectResolution.CONNECT -> {
                pendingTcpConnect = null
                pending.second()
            }

            // Warning-free for the same reason as above: the denial that just landed also made the pane's permission
            // card appear, and it says the same thing with a button attached.
            PendingTcpConnectResolution.CONNECT_WITH_WARNING -> {
                pendingTcpConnect = null
                pending.second()
            }

            PendingTcpConnectResolution.KEEP_WAITING -> Unit
        }
    }

    // A completed connection is proof the refusal is behind us, whatever the system reported at the time.
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) scanModel.clearBlePermissionRefusal()
    }

    // Adapter-state, distinct from permission state: a permission can be granted while Bluetooth is off or the device
    // is off Wi-Fi. Detected separately so the UI can route to the adapter's settings rather than re-prompting.
    val bluetoothDisabled = isBluetoothDisabled()
    val wifiUnavailable = isWifiUnavailable()
    val openBluetoothSettings = rememberOpenBluetoothSettings()
    val openWifiSettings = rememberOpenWifiSettings()
    val openLocationSettings = rememberOpenLocationSettings()
    val uriHandler = LocalUriHandler.current

    // Android 11 and lower gate the BLE scan on system Location Services as well as the permission, and a scan with
    // them off returns zero results with no error — the same picture as "no radios nearby". Detected separately from
    // the permission so the hint points at the toggle that is actually blocking the scan.
    val gpsDisabled = isGpsDisabled()
    val bleBlockedByLocationServices = bleScanRequiresLocationServices && gpsDisabled

    // Pre-Android-12 the BLE permission *is* ACCESS_FINE_LOCATION, so the copy has to name that instead of the
    // "Nearby devices" permission the user will never be offered on those releases.
    val bluetoothRationale =
        stringResource(
            if (bleScanRequiresLocationServices) {
                Res.string.bluetooth_permission_rationale_pre31
            } else {
                Res.string.bluetooth_permission_rationale
            },
        )
    val bluetoothBlockedRationale =
        stringResource(
            if (bleScanRequiresLocationServices) {
                Res.string.bluetooth_permission_blocked_rationale_pre31
            } else {
                Res.string.bluetooth_permission_blocked_rationale
            },
        )
    // Auto-start BLE discovery when the screen is visible (lifecycle ≥ STARTED) and the user has previously opted in.
    // ScannerViewModel skips screen-entry discovery when a selected device can reconnect through the transport's
    // fresh-advertisement scan. LifecycleStartEffect stops scanning on ON_STOP (app backgrounded) and restarts on
    // ON_START — preventing continuous background BLE radio usage that drains the battery.
    // Keyed on the active pane so a persisted TCP/USB pane loaded after first composition disposes this effect and
    // stops an initially eligible BLE scan. The toggle handler starts/stops scans directly; this effect owns lifecycle
    // cleanup, while the LaunchedEffect below handles async preference loading without disposing the lifecycle owner.
    // Keyed on the grant as well as the pane, mirroring the network pair below: a tap on Scan that resolved to a
    // permission request persists the intent, and this is what turns the resulting grant into the scan the user
    // already asked for rather than a second trip to the button.
    LifecycleStartEffect(activeTransport, bluetoothPermission.isGranted) {
        if (activeTransport == DeviceType.BLE && bleAutoScan && bluetoothPermission.isGranted && !isBleScanning) {
            scanModel.startBleAutoScan()
        }
        onStopOrDispose { scanModel.stopBleScan() }
    }

    LaunchedEffect(activeTransport, bluetoothPermission.isGranted, bleAutoScan) {
        if (activeTransport == DeviceType.BLE && bleAutoScan && bluetoothPermission.isGranted && !isBleScanning) {
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

    // Work around CMP-6615 in Compose Multiplatform 1.11.1: Android stringResource enters a blocking resource state.
    // Keep these stable slots outside AnimatedContent so connection-state transitions do not re-enter resource loading
    // while the main thread is applying an animation frame.
    val firmwareVersion =
        ourNode
            ?.metadata
            ?.firmware_version
            ?.takeIf { it.isNotBlank() }
            ?.let { stringResource(Res.string.firmware_version, it) }
    val currentlyConnectedText =
        CurrentlyConnectedText(
            unknownLabel = stringResource(Res.string.unknown),
            rssiLabel = stringResource(Res.string.rssi),
            disconnectLabel = stringResource(Res.string.disconnect),
            firmwareVersion = firmwareVersion,
        )

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
                                                text = currentlyConnectedText,
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

                        // Event firmware is reported here rather than by swapping the app-bar logo: the Meshtastic
                        // identity stays put, and the edition reads as one more fact about the connected device.
                        // LocalEventBranding is only populated while connected to event firmware, so the card comes
                        // and goes with the device. Hidden once the event is over — the ended-event card below takes
                        // over from here, and celebrating an event that has passed would undercut its nudge.
                        LocalEventBranding.current
                            ?.takeIf { !it.hasEnded() }
                            ?.let { edition ->
                                Spacer(modifier = Modifier.height(8.dp))
                                EventFirmwareCard(edition = edition)
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

                        // Config warnings sit outside the animated card so they do not affect the
                        // CONNECTED ↔ CONNECTING ↔ NO_DEVICE size transition.
                        val isPhysicalDevice =
                            selectedDevice != InterfaceId.MOCK.id.toString() &&
                                selectedDevice != InterfaceId.REPLAY.id.toString()
                        val isManaged = localConfig.security?.is_managed == true
                        // Gate on LockdownState rather than sessionAuthorized. Pre-2.8 firmware and newer builds that
                        // do not include runtime lockdown support never enter that authentication flow, while an
                        // explicit DISABLED state also leaves sessionAuthorized false. None, Disabled, and Unlocked do
                        // not withhold config writes on lockdown grounds; AwaitingResponse stays non-actionable until
                        // firmware reports the result. Managed mode is a separate client policy: match the existing
                        // settings behavior and suppress warnings only when SecurityConfig explicitly marks the device
                        // managed, so an incomplete first-run config stream does not hide the region warning.
                        // Node readiness binds the warnings to the active transport session. Stage 1 clears cached
                        // config before accepting the fresh stream, while a cached node can survive a database switch;
                        // do not attribute post-handshake config state to a node the new session has not identified.
                        ConfigurationWarningCards(
                            connectedWithNode = uiState == ConnectionUiState.CONNECTED_WITH_NODE,
                            activeNodeInfoReady = activeNodeInfoReady,
                            lockdownState = lockdownState,
                            isManaged = isManaged,
                            isPhysicalDevice = isPhysicalDevice,
                            regionUnset = regionUnset,
                            txDisabled = txDisabled,
                            onConfigNavigate = onConfigNavigate,
                        )

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
                        // Missing permission takes precedence over every other BLE hint: nothing else the user could
                        // fix will produce a scan result while it is absent, and an empty list with a "check you're in
                        // range" hint blames the radio for the app's own missing grant. The card's action follows the
                        // status — request while the system will still prompt, app settings once it won't.
                        if (activeTransport == DeviceType.BLE && !bluetoothPermission.isGranted) {
                            PermissionRecoveryCard(
                                state = bluetoothPermission,
                                rationale =
                                if (bluetoothPermission.status == PermissionStatus.PERMANENTLY_DENIED) {
                                    bluetoothBlockedRationale
                                } else {
                                    bluetoothRationale
                                },
                            )
                        }
                        // The platform refused a bond or scan while the app holds the grant — a partial grant, or an
                        // OEM quirk. Only shown when the two disagree: the card above already covers a plain missing
                        // permission, and with a better action than "go look in settings".
                        if (
                            activeTransport == DeviceType.BLE && blePermissionRefusal && bluetoothPermission.isGranted
                        ) {
                            RecoveryCard(
                                message = stringResource(Res.string.bonding_failed_permissions),
                                actionLabel = stringResource(Res.string.open_settings),
                                onAction = {
                                    scanModel.clearBlePermissionRefusal()
                                    bluetoothPermission.openAppSettings()
                                },
                                actionIcon = MeshtasticIcons.AppSettingsAlt,
                                onDismiss = { scanModel.clearBlePermissionRefusal() },
                                dismissContentDescription = stringResource(Res.string.close),
                            )
                        }
                        if (activeTransport == DeviceType.BLE && bluetoothPermission.isGranted && bluetoothDisabled) {
                            RecoveryCard(
                                message = stringResource(Res.string.bluetooth_disabled),
                                actionLabel = stringResource(Res.string.open_bluetooth_settings),
                                onAction = openBluetoothSettings,
                                actionIcon = MeshtasticIcons.Bluetooth,
                            )
                        }
                        // Android 11 and lower only. Shown after the adapter check so a device with both problems is
                        // told about the radio first — turning location on would not help while Bluetooth is off.
                        if (
                            activeTransport == DeviceType.BLE &&
                            bluetoothPermission.isGranted &&
                            !bluetoothDisabled &&
                            bleBlockedByLocationServices
                        ) {
                            RecoveryCard(
                                message = stringResource(Res.string.ble_scan_needs_location_services),
                                actionLabel = stringResource(Res.string.open_location_settings),
                                onAction = openLocationSettings,
                            )
                        }
                        // The BLE pane's missing-permission card, for the transport Android 17 gates. Without it
                        // the network pane repeats the BLE bug it was just fixed for: an empty list and a hint about
                        // the network, when the app was never allowed to look. The gate is on the socket, not just
                        // discovery, so manual entry is no workaround for a radio on this Wi-Fi — only one reached
                        // over a public address or a VPN is unaffected, which is what the copy now says.
                        if (activeTransport == DeviceType.TCP && !localNetworkPermission.isGranted) {
                            PermissionRecoveryCard(
                                state = localNetworkPermission,
                                rationale =
                                stringResource(
                                    if (localNetworkPermission.status == PermissionStatus.PERMANENTLY_DENIED) {
                                        Res.string.local_network_permission_blocked_rationale
                                    } else {
                                        Res.string.local_network_permission_rationale
                                    },
                                ),
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
                                onSelectDevice = { entry ->
                                    // Recent TCP addresses are persisted, so this list renders without a scan — and
                                    // therefore without the scan toggle's permission request ever having run. BLE and
                                    // USB are unaffected by ACCESS_LOCAL_NETWORK, so only gate Tcp.
                                    if (entry is DeviceListEntry.Tcp) {
                                        gateTcpConnect { scanModel.onSelected(entry) }
                                    } else {
                                        scanModel.onSelected(entry)
                                    }
                                },
                                onToggleBleScan = {
                                    // Always allow stopping an in-progress scan, whatever the permission says.
                                    if (isBleScanning) {
                                        scanModel.toggleBleScan()
                                    } else {
                                        when (permissionGateAction(bluetoothPermission.status)) {
                                            // Granted. Route to whichever system toggle is actually blocking the scan
                                            // before starting one that would silently return nothing.
                                            PermissionGateAction.PROCEED ->
                                                when {
                                                    bluetoothDisabled -> openBluetoothSettings()
                                                    bleBlockedByLocationServices -> openLocationSettings()
                                                    else -> scanModel.toggleBleScan()
                                                }

                                            // Never asked: go straight to the system dialog. A rationale before the
                                            // first prompt adds friction without adding information. Persist the
                                            // intent so the grant starts the scan the user already asked for, instead
                                            // of making them tap Scan a second time.
                                            PermissionGateAction.REQUEST -> {
                                                scanModel.persistBleAutoScanIntent(true)
                                                bluetoothPermission.request()
                                            }

                                            // Denied once. The educational UI the guidance asks for is already on
                                            // screen — the permission card above this list carries the same rationale
                                            // and its own Grant button, and can be ignored, which a modal cannot.
                                            // Repeating it in a dialog is friction, not information.
                                            PermissionGateAction.SHOW_RATIONALE -> {
                                                scanModel.persistBleAutoScanIntent(true)
                                                bluetoothPermission.request()
                                            }

                                            // The system won't prompt again, so requesting would do nothing visible.
                                            PermissionGateAction.OPEN_SETTINGS -> bluetoothPermission.openAppSettings()
                                        }
                                    }
                                },
                                onToggleNetworkScan = {
                                    if (isNetworkScanning) {
                                        scanModel.toggleNetworkScan()
                                    } else {
                                        when (permissionGateAction(localNetworkPermission.status)) {
                                            PermissionGateAction.PROCEED -> scanModel.toggleNetworkScan()

                                            // Prefer requesting the runtime grant over letting the platform fall back
                                            // to the system NSD picker. Persist the user's intent so that if they
                                            // grant after the prompt, the scan starts via the LifecycleStartEffect and
                                            // stays on for next session.
                                            PermissionGateAction.REQUEST -> {
                                                scanModel.persistNetworkAutoScanIntent(true)
                                                localNetworkPermission.request()
                                            }

                                            // As on the Bluetooth pane: the card above this list is the rationale,
                                            // and it is already visible whenever this branch is reachable.
                                            PermissionGateAction.SHOW_RATIONALE -> {
                                                scanModel.persistNetworkAutoScanIntent(true)
                                                localNetworkPermission.request()
                                            }

                                            PermissionGateAction.OPEN_SETTINGS ->
                                                localNetworkPermission.openAppSettings()
                                        }
                                    }
                                },
                                onAddManualAddress = { _, fullAddress ->
                                    // Typing an address is always allowed — the target may be a public host or VPN
                                    // peer. The gate runs at connect, where the permission can actually matter.
                                    gateTcpConnect { scanModel.connectToManualAddress(fullAddress) }
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
    text: CurrentlyConnectedText,
    onNavigateToNodeDetails: (Int) -> Unit,
    onClickDisconnect: () -> Unit,
) {
    ourNode?.let { node ->
        CurrentlyConnectedInfo(
            node = node,
            text = text,
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
