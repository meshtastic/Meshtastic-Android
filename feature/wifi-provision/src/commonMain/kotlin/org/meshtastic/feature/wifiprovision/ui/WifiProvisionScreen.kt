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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@file:Suppress("TooManyFunctions", "LongMethod")

package org.meshtastic.feature.wifiprovision.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ble.normalizeBleAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.action_select_device
import org.meshtastic.core.resources.action_select_network
import org.meshtastic.core.resources.address
import org.meshtastic.core.resources.apply
import org.meshtastic.core.resources.back
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.connect
import org.meshtastic.core.resources.hide_password
import org.meshtastic.core.resources.img_mpwrd_logo
import org.meshtastic.core.resources.mpwrd_os
import org.meshtastic.core.resources.password
import org.meshtastic.core.resources.retry
import org.meshtastic.core.resources.show_password
import org.meshtastic.core.resources.unknown_device
import org.meshtastic.core.resources.web_bluetooth_unsupported
import org.meshtastic.core.resources.wifi_provision_available_networks
import org.meshtastic.core.resources.wifi_provision_choose_device
import org.meshtastic.core.resources.wifi_provision_choose_device_detail
import org.meshtastic.core.resources.wifi_provision_confirm_compare
import org.meshtastic.core.resources.wifi_provision_confirm_consequence
import org.meshtastic.core.resources.wifi_provision_confirm_title
import org.meshtastic.core.resources.wifi_provision_confirm_title_named
import org.meshtastic.core.resources.wifi_provision_connect_failed
import org.meshtastic.core.resources.wifi_provision_description
import org.meshtastic.core.resources.wifi_provision_device_found
import org.meshtastic.core.resources.wifi_provision_device_found_detail
import org.meshtastic.core.resources.wifi_provision_device_not_found
import org.meshtastic.core.resources.wifi_provision_hidden_network
import org.meshtastic.core.resources.wifi_provision_link_unsupported
import org.meshtastic.core.resources.wifi_provision_mpwrd_disclaimer
import org.meshtastic.core.resources.wifi_provision_no_devices
import org.meshtastic.core.resources.wifi_provision_no_devices_detail
import org.meshtastic.core.resources.wifi_provision_no_networks
import org.meshtastic.core.resources.wifi_provision_no_response
import org.meshtastic.core.resources.wifi_provision_pick_device
import org.meshtastic.core.resources.wifi_provision_rescan
import org.meshtastic.core.resources.wifi_provision_scan_failed
import org.meshtastic.core.resources.wifi_provision_scan_networks
import org.meshtastic.core.resources.wifi_provision_scanning_ble
import org.meshtastic.core.resources.wifi_provision_scanning_wifi
import org.meshtastic.core.resources.wifi_provision_sending_credentials
import org.meshtastic.core.resources.wifi_provision_signal_dbm
import org.meshtastic.core.resources.wifi_provision_signal_strength
import org.meshtastic.core.resources.wifi_provision_ssid_label
import org.meshtastic.core.resources.wifi_provision_ssid_placeholder
import org.meshtastic.core.resources.wifi_provision_success_description
import org.meshtastic.core.resources.wifi_provision_success_device_connected
import org.meshtastic.core.resources.wifi_provision_success_done
import org.meshtastic.core.resources.wifi_provision_success_ip_address
import org.meshtastic.core.resources.wifi_provision_success_missing_ip
import org.meshtastic.core.resources.wifi_provision_success_open_ssh
import org.meshtastic.core.resources.wifi_provision_success_open_ssh_fallback
import org.meshtastic.core.resources.wifi_provision_success_password_value
import org.meshtastic.core.resources.wifi_provision_success_setup_description
import org.meshtastic.core.resources.wifi_provision_success_setup_title
import org.meshtastic.core.resources.wifi_provision_success_ssh_command
import org.meshtastic.core.resources.wifi_provision_success_ssh_label
import org.meshtastic.core.resources.wifi_provision_success_ssh_unavailable
import org.meshtastic.core.resources.wifi_provision_success_username
import org.meshtastic.core.resources.wifi_provision_success_username_value
import org.meshtastic.core.resources.wifi_provisioning
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.CopyIconButton
import org.meshtastic.core.ui.component.EmptyState
import org.meshtastic.core.ui.component.MaterialBluetoothSignalInfo
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.BluetoothSearching
import org.meshtastic.core.ui.icon.CheckCircle
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Serial
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.isBluetoothSupported
import org.meshtastic.core.ui.util.rememberOpenUrl
import org.meshtastic.feature.wifiprovision.WifiProvisionError
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.Phase
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.ProvisionStatus
import org.meshtastic.feature.wifiprovision.WifiProvisionViewModel
import org.meshtastic.feature.wifiprovision.model.DiscoveredDevice
import org.meshtastic.feature.wifiprovision.model.WifiNetwork

private const val NETWORK_LIST_MAX_HEIGHT_DP = 240

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun WifiProvisionScreen(
    onNavigateUp: () -> Unit,
    address: String? = null,
    viewModel: WifiProvisionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = uiState.error?.message()

    LaunchedEffect(uiState.error) { errorMessage?.let { snackbarHostState.showSnackbar(it) } }
    // Nothing connects on arrival: an address waits for the user's Connect, the app lists what it finds and waits for a
    // tap, and the browser waits for a click on its chooser button.
    LaunchedEffect(viewModel, address) { viewModel.open(address) }

    WifiProvisionScreenContent(
        uiState = uiState,
        picksDevice = viewModel.picksDevice,
        snackbarHostState = snackbarHostState,
        actions =
        WifiProvisionActions(
            onNavigateUp = onNavigateUp,
            onCancel = {
                viewModel.disconnect()
                onNavigateUp()
            },
            onScanNetworks = viewModel::scanNetworks,
            onProvision = viewModel::provisionWifi,
            choice =
            DeviceChoiceActions(
                onPick = { viewModel.connectToDevice() },
                onSelect = viewModel::connectToDiscovered,
                onRescan = viewModel::rescan,
                onConfirm = viewModel::confirmConnect,
                onRetry = { if (viewModel.picksDevice) viewModel.connectToDevice() else viewModel.rescan() },
            ),
        ),
    )
}

/** The text the user reads for [this]: what happened and what to do, from string resources rather than exceptions. */
@Composable
private fun WifiProvisionError.message(): String = when (this) {
    WifiProvisionError.DeviceNotFound -> stringResource(Res.string.wifi_provision_device_not_found)
    WifiProvisionError.DeviceNotResponding -> stringResource(Res.string.wifi_provision_no_response)
    is WifiProvisionError.ConnectFailed -> stringResource(Res.string.wifi_provision_connect_failed, detail)
    is WifiProvisionError.ScanFailed -> stringResource(Res.string.wifi_provision_scan_failed, detail)
    is WifiProvisionError.ProvisionFailed -> detail
}

/** What choosing a device can ask of its ViewModel: the browser's chooser, a listed row, a rescan, a confirmation. */
@Immutable
internal class DeviceChoiceActions(
    val onPick: () -> Unit,
    val onSelect: (address: String) -> Unit,
    val onRescan: () -> Unit,
    val onConfirm: () -> Unit,
    val onRetry: () -> Unit,
) {
    companion object {
        val None = DeviceChoiceActions(onPick = {}, onSelect = {}, onRescan = {}, onConfirm = {}, onRetry = {})
    }
}

/** What the screen can ask of its ViewModel and host; [None] is for previews and tests that only look. */
@Immutable
internal class WifiProvisionActions(
    val onNavigateUp: () -> Unit,
    val onCancel: () -> Unit,
    val onScanNetworks: () -> Unit,
    val onProvision: (ssid: String, password: String, hidden: Boolean) -> Unit,
    val choice: DeviceChoiceActions,
) {
    companion object {
        val None =
            WifiProvisionActions(
                onNavigateUp = {},
                onCancel = {},
                onScanNetworks = {},
                onProvision = { _, _, _ -> },
                choice = DeviceChoiceActions.None,
            )
    }
}

/** One width for every state: wider windows get a column, not lines of more than 70 characters. */
private val ContentMaxWidth = 560.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WifiProvisionScreenContent(
    uiState: WifiProvisionUiState,
    picksDevice: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: WifiProvisionActions,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.wifi_provisioning)) },
                navigationIcon = {
                    IconButton(onClick = actions.onNavigateUp) {
                        Icon(MeshtasticIcons.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().animateContentSize()) {
                ProgressSlot(visible = uiState.phase.isLoading)

                MpwrdDisclaimerBanner()

                if (uiState.linkAddressIgnored && uiState.phase.isChoosingDevice) {
                    LinkAddressNotice()
                }

                Crossfade(targetState = screenKey(uiState, picksDevice), label = "wifi_provision") { key ->
                    when (key) {
                        ScreenKey.PickDevice -> PickDeviceContent(onPick = actions.choice.onPick)

                        ScreenKey.DeviceList ->
                            DeviceListContent(
                                devices = uiState.devices,
                                isScanning = uiState.phase == Phase.Discovering,
                                showAddress = uiState.addressIsHardware,
                                onSelect = actions.choice.onSelect,
                                onRescan = actions.choice.onRescan,
                            )

                        ScreenKey.ConfirmDevice ->
                            ConfirmDeviceContent(
                                address = uiState.pendingAddress.orEmpty(),
                                name = uiState.pendingName,
                                onConnect = actions.choice.onConfirm,
                                onCancel = actions.onCancel,
                            )

                        ScreenKey.ConnectingBle ->
                            ScanningBleContent(
                                errorMessage = uiState.error?.message(),
                                onRetry = actions.choice.onRetry,
                            )

                        ScreenKey.DeviceFound ->
                            DeviceFoundContent(
                                deviceName = uiState.deviceName,
                                deviceAddress = uiState.deviceAddress.takeIf { uiState.addressIsHardware },
                                onProceed = actions.onScanNetworks,
                                onCancel = actions.onCancel,
                            )

                        ScreenKey.LoadingNetworks -> ScanningNetworksContent()

                        ScreenKey.Connected ->
                            ConnectedContent(
                                networks = uiState.networks,
                                provisionStatus = uiState.provisionStatus,
                                ipAddress = uiState.ipAddress,
                                isProvisioning = uiState.phase == Phase.Provisioning,
                                isScanning = uiState.phase == Phase.LoadingNetworks,
                                onScanNetworks = actions.onScanNetworks,
                                onProvision = actions.onProvision,
                                onDisconnect = actions.onCancel,
                            )
                    }
                }
            }
        }
    }
}

/**
 * Progress for active operations in a slot that is always as tall as the indicator, so the banner below does not move
 * when it appears. The hidden bar only measures that height.
 */
@Composable
private fun ProgressSlot(visible: Boolean) {
    Box(modifier = Modifier.fillMaxWidth()) {
        LinearWavyProgressIndicator(
            progress = { 0f },
            modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics {},
        )
        if (visible) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Tells the user why a link did not pick a device, above the list or chooser that replaces it. */
@Composable
private fun LinkAddressNotice() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(
            stringResource(Res.string.wifi_provision_link_unsupported),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Screen-key helper for Crossfade
// ---------------------------------------------------------------------------

private enum class ScreenKey {
    PickDevice,
    DeviceList,
    ConfirmDevice,
    ConnectingBle,
    DeviceFound,
    LoadingNetworks,
    Connected,
}

private fun screenKey(state: WifiProvisionUiState, picksDevice: Boolean): ScreenKey = when (state.phase) {
    // A failed connect returns to Idle with its error, which ScanningBleContent shows with a retry.
    Phase.Idle -> if (picksDevice && state.error == null) ScreenKey.PickDevice else ScreenKey.ConnectingBle

    // Until a first device appears the search is a status page like every other wait, not an empty list.
    Phase.Discovering -> if (state.devices.isEmpty()) ScreenKey.ConnectingBle else ScreenKey.DeviceList

    Phase.DeviceList -> ScreenKey.DeviceList

    Phase.ConfirmDevice -> ScreenKey.ConfirmDevice

    Phase.ConnectingBle -> ScreenKey.ConnectingBle

    Phase.DeviceFound -> ScreenKey.DeviceFound

    Phase.LoadingNetworks -> if (state.networks.isEmpty()) ScreenKey.LoadingNetworks else ScreenKey.Connected

    Phase.Connected,
    Phase.Provisioning,
    -> ScreenKey.Connected
}

private val Phase.isChoosingDevice: Boolean
    get() = this == Phase.Idle || this == Phase.Discovering || this == Phase.DeviceList

private val Phase.isLoading: Boolean
    get() =
        this == Phase.Discovering ||
            this == Phase.ConnectingBle ||
            this == Phase.LoadingNetworks ||
            this == Phase.Provisioning

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

/** BLE scanning spinner — shown while searching for a device. */
@Composable
internal fun ScanningBleContent(errorMessage: String? = null, onRetry: () -> Unit = {}) {
    CenteredStatusContent {
        if (errorMessage != null) {
            Icon(
                MeshtasticIcons.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        } else {
            LoadingIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(Res.string.wifi_provision_scanning_ble), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * The browser's device chooser needs a click to open, so this asks for one before anything connects. A browser with no
 * Web Bluetooth gets the reason instead of a button that could only fail.
 */
@Composable
internal fun PickDeviceContent(onPick: () -> Unit, bluetoothSupported: Boolean = isBluetoothSupported()) {
    CenteredStatusContent {
        Icon(
            MeshtasticIcons.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(
                if (bluetoothSupported) {
                    Res.string.wifi_provision_choose_device_detail
                } else {
                    Res.string.web_bluetooth_unsupported
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (bluetoothSupported) {
            Spacer(Modifier.height(32.dp))
            Button(onClick = onPick) { Text(stringResource(Res.string.wifi_provision_choose_device)) }
        }
    }
}

/**
 * The provisioning devices in range, strongest first. Nothing connects until a row is tapped, even for a single device,
 * because the device chosen here receives the Wi-Fi password.
 */
@Composable
internal fun DeviceListContent(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    showAddress: Boolean,
    onSelect: (address: String) -> Unit,
    onRescan: () -> Unit,
) {
    if (devices.isEmpty() && !isScanning) {
        CenteredStatusContent {
            EmptyState(
                icon = MeshtasticIcons.BluetoothSearching,
                title = stringResource(Res.string.wifi_provision_no_devices),
                supportingText = stringResource(Res.string.wifi_provision_no_devices_detail),
                action = { RescanButton(onClick = onRescan) },
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.wifi_provision_pick_device),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
                Text(
                    stringResource(Res.string.wifi_provision_scanning_ble),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            LazyColumn {
                items(devices, key = { it.address }) { device ->
                    DeviceRow(device = device, showAddress = showAddress, onClick = { onSelect(device.address) })
                }
            }
        }
        if (!isScanning) {
            RescanButton(onClick = onRescan, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** The same action in the same emphasis wherever the scan can be repeated. */
@Composable
private fun RescanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(MeshtasticIcons.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.wifi_provision_rescan))
    }
}

/**
 * One listed device. The address is shown only where it is a hardware address the user can check; elsewhere it is an
 * identifier only this host understands.
 */
@Composable
internal fun DeviceRow(device: DiscoveredDevice, showAddress: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.name ?: stringResource(Res.string.unknown_device)) },
        // The address and the signal share one wrapping line, so a large font moves the signal below the address
        // instead of squeezing the address into a break inside its own characters.
        supportingContent =
        if (showAddress || device.rssi != null) {
            {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showAddress) Text(normalizeBleAddress(device.address))
                    device.rssi?.let { rssi ->
                        val description = stringResource(Res.string.wifi_provision_signal_dbm, rssi)
                        MaterialBluetoothSignalInfo(
                            rssi = rssi,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                        )
                    }
                }
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                MeshtasticIcons.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
        Modifier.clickable(
            onClickLabel = stringResource(Res.string.action_select_device),
            role = Role.Button,
            onClick = onClick,
        ),
    )
}

/**
 * Asks the user to confirm an address that came from outside the screen, such as a link, before anything connects. The
 * name is whatever the device advertises, so the address is what to check against the device's label.
 */
@Composable
internal fun ConfirmDeviceContent(address: String, name: String?, onConnect: () -> Unit, onCancel: () -> Unit) {
    CenteredStatusContent {
        Icon(
            MeshtasticIcons.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            if (name != null) {
                stringResource(Res.string.wifi_provision_confirm_title_named, name)
            } else {
                stringResource(Res.string.wifi_provision_confirm_title)
            },
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        DeviceAddress(address)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.wifi_provision_confirm_consequence),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.wifi_provision_confirm_compare),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            Button(onClick = onConnect) { Text(stringResource(Res.string.connect)) }
        }
    }
}

/** The identifier the user checks against the device, set larger than the text around it. */
@Composable
private fun DeviceAddress(address: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.address),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                normalizeBleAddress(address),
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Confirmation step shown after BLE device discovery — the Android analog of the web flasher's native BLE pairing
 * prompt. Gives the user a clear "device found" moment before proceeding.
 */
@Composable
internal fun DeviceFoundContent(
    deviceName: String?,
    onProceed: () -> Unit,
    onCancel: () -> Unit,
    deviceAddress: String? = null,
) {
    CenteredStatusContent {
        Icon(
            MeshtasticIcons.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.wifi_provision_device_found),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        if (deviceName != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                deviceName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (deviceAddress != null) {
            Spacer(Modifier.height(16.dp))
            DeviceAddress(deviceAddress)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.wifi_provision_confirm_compare),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.wifi_provision_device_found_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            Button(onClick = onProceed) { Text(stringResource(Res.string.wifi_provision_scan_networks)) }
        }
    }
}

/** Network scanning spinner — shown during the initial scan when no networks are loaded yet. */
@Composable
internal fun ScanningNetworksContent() {
    CenteredStatusContent {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.wifi_provision_scanning_wifi), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Main configuration screen shown after BLE connection — mirrors the web flasher's connected state. All controls (scan
 * button, network list, SSID/password fields, Apply, status) are on one screen.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun ConnectedContent(
    networks: List<WifiNetwork>,
    provisionStatus: ProvisionStatus,
    ipAddress: String?,
    isProvisioning: Boolean,
    isScanning: Boolean,
    onScanNetworks: () -> Unit,
    onProvision: (ssid: String, password: String, hidden: Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    if (provisionStatus == ProvisionStatus.Success) {
        ProvisionSuccessContent(ipAddress = ipAddress, onDone = onDisconnect)
        return
    }

    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var hiddenNetwork by rememberSaveable { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(provisionStatus) {
        if (provisionStatus == ProvisionStatus.Success) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.wifi_provision_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Scan button — FilledTonalButton for prominent secondary action
        FilledTonalButton(
            onClick = onScanNetworks,
            enabled = !isScanning && !isProvisioning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isScanning) {
                LoadingIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(MeshtasticIcons.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (isScanning) {
                    stringResource(Res.string.wifi_provision_scanning_wifi)
                } else {
                    stringResource(Res.string.wifi_provision_scan_networks)
                },
            )
        }

        // Network list (scrollable, capped height) — animated entrance
        AnimatedVisibility(
            visible = networks.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Text(
                    stringResource(Res.string.wifi_provision_available_networks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = NETWORK_LIST_MAX_HEIGHT_DP.dp)) {
                        items(networks, key = { it.ssid }) { network ->
                            NetworkRow(
                                network = network,
                                isSelected = network.ssid == ssid,
                                onClick = { ssid = network.ssid },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = networks.isEmpty() && !isScanning, enter = fadeIn(), exit = fadeOut()) {
            Text(
                stringResource(Res.string.wifi_provision_no_networks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // SSID input
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text(stringResource(Res.string.wifi_provision_ssid_label)) },
            placeholder = { Text(stringResource(Res.string.wifi_provision_ssid_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Password input
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconToggleButton(checked = passwordVisible, onCheckedChange = { passwordVisible = it }) {
                    Icon(
                        imageVector =
                        if (passwordVisible) MeshtasticIcons.VisibilityOff else MeshtasticIcons.Visibility,
                        contentDescription =
                        if (passwordVisible) {
                            stringResource(Res.string.hide_password)
                        } else {
                            stringResource(Res.string.show_password)
                        },
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onProvision(ssid, password, hiddenNetwork) }),
            modifier = Modifier.fillMaxWidth(),
        )

        // Hidden network toggle
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { hiddenNetwork = !hiddenNetwork },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(Res.string.wifi_provision_hidden_network), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = hiddenNetwork, onCheckedChange = { hiddenNetwork = it })
        }

        // Inline provision status (matches web flasher's status chip) — animated entrance
        AnimatedVisibility(
            visible = provisionStatus != ProvisionStatus.Idle || isProvisioning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ProvisionStatusCard(provisionStatus = provisionStatus, isProvisioning = isProvisioning)
        }

        // Action buttons — cancel left, primary action right (app convention)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDisconnect) { Text(stringResource(Res.string.cancel)) }
            Button(
                onClick = { onProvision(ssid, password, hiddenNetwork) },
                enabled = ssid.isNotBlank() && !isProvisioning,
                modifier = Modifier.weight(1f),
            ) {
                if (isProvisioning) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.wifi_provision_sending_credentials))
                } else {
                    Icon(MeshtasticIcons.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.apply))
                }
            }
        }
    }
}

@Composable
private fun ProvisionSuccessContent(ipAddress: String?, onDone: () -> Unit) {
    val openUrl = rememberOpenUrl()
    val defaultUsername = stringResource(Res.string.wifi_provision_success_username_value)
    val defaultPassword = stringResource(Res.string.wifi_provision_success_password_value)
    val resolvedIp = ipAddress ?: stringResource(Res.string.wifi_provision_success_missing_ip)
    val sshCommand =
        ipAddress?.let { stringResource(Res.string.wifi_provision_success_ssh_command, defaultUsername, it) }
            ?: stringResource(Res.string.wifi_provision_success_ssh_unavailable)
    val sshUri = ipAddress?.let { "ssh://$defaultUsername@$it" }

    Column(
        modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = MeshtasticIcons.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            text = stringResource(Res.string.wifi_provision_success_device_connected),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = stringResource(Res.string.wifi_provision_success_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ProvisionInfoItem(
                label = stringResource(Res.string.wifi_provision_success_ip_address),
                value = resolvedIp,
                copyEnabled = ipAddress != null,
            )
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text(
                    text = stringResource(Res.string.wifi_provision_success_setup_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
                Text(
                    text = stringResource(Res.string.wifi_provision_success_setup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
                ProvisionInfoItem(
                    label = stringResource(Res.string.wifi_provision_success_username),
                    value = defaultUsername,
                )
                ProvisionInfoItem(label = stringResource(Res.string.password), value = defaultPassword)
                ProvisionInfoItem(
                    label = stringResource(Res.string.wifi_provision_success_ssh_label),
                    value = sshCommand,
                    copyEnabled = ipAddress != null,
                )

                FilledTonalButton(
                    onClick = { sshUri?.let(openUrl) },
                    enabled = sshUri != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = MeshtasticIcons.Serial,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.wifi_provision_success_open_ssh))
                }
                Text(
                    text = stringResource(Res.string.wifi_provision_success_open_ssh_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.wifi_provision_success_done))
        }
    }
}

@Composable
private fun ProvisionInfoItem(label: String, value: String, copyEnabled: Boolean = true) {
    ListItem(
        overlineContent = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        headlineContent = { Text(text = value, style = MaterialTheme.typography.bodyLargeEmphasized) },
        trailingContent = {
            if (copyEnabled) {
                CopyIconButton(valueToCopy = value)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun NetworkRow(network: WifiNetwork, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ListItem(
        headlineContent = { Text(network.ssid) },
        supportingContent = { Text(stringResource(Res.string.wifi_provision_signal_strength, network.signalStrength)) },
        leadingContent = {
            Icon(MeshtasticIcons.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (network.isProtected) {
                Icon(
                    MeshtasticIcons.Lock,
                    contentDescription = stringResource(Res.string.password),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier =
        Modifier.clickable(
            onClickLabel = stringResource(Res.string.action_select_network),
            role = Role.Button,
            onClick = onClick,
        ),
    )
}

// ---------------------------------------------------------------------------
// mPWRD-OS disclaimer banner
// ---------------------------------------------------------------------------

private const val MPWRD_LOGO_SIZE_DP = 40

/** Branded disclaimer banner shown at the top of the provisioning screen. */
@Composable
internal fun MpwrdDisclaimerBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painter = painterResource(Res.drawable.img_mpwrd_logo),
                contentDescription = stringResource(Res.string.mpwrd_os),
                modifier = Modifier.size(MPWRD_LOGO_SIZE_DP.dp).clip(RoundedCornerShape(8.dp)),
            )
            AutoLinkText(
                text = stringResource(Res.string.wifi_provision_mpwrd_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared layout wrapper for centered status screens
// ---------------------------------------------------------------------------

@Composable
private fun CenteredStatusContent(content: @Composable () -> Unit) {
    Column(
        modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@PreviewLightDark
@Composable
private fun ProvisionSuccessContentPreview() {
    AppTheme { Surface { ProvisionSuccessContent(ipAddress = "192.168.1.100", onDone = {}) } }
}
