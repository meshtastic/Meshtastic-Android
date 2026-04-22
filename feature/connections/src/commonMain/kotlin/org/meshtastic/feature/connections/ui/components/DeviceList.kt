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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.isValidAddress
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.network.repository.NetworkConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_network_device
import org.meshtastic.core.resources.add_network_device_manually
import org.meshtastic.core.resources.address
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.ip_port
import org.meshtastic.core.resources.network
import org.meshtastic.core.resources.no_bluetooth_devices_hint
import org.meshtastic.core.resources.no_bluetooth_devices_seen
import org.meshtastic.core.resources.no_network_devices_hint
import org.meshtastic.core.resources.no_network_devices_seen
import org.meshtastic.core.resources.no_usb_devices_hint
import org.meshtastic.core.resources.no_usb_devices_seen
import org.meshtastic.core.resources.recent_network_devices
import org.meshtastic.core.resources.scan_bluetooth_devices
import org.meshtastic.core.resources.scan_network_devices
import org.meshtastic.core.resources.scanning_bluetooth
import org.meshtastic.core.resources.scanning_network
import org.meshtastic.core.resources.usb
import org.meshtastic.core.ui.icon.Add
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.icon.Usb
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.feature.connections.model.DeviceListEntry

/**
 * Unified device list: BLE / USB / Network sections rendered as one scrollable [LazyColumn].
 *
 * Replaces the previous tab-based UI. Every section uses the same M3 header template ([DeviceSectionHeader]); empty
 * sections are hidden. Stable per-transport keys (e.g. `"ble:<fullAddress>"`) keep LazyColumn's recomposition scope
 * tight to the actual item that changed when a user taps a device card.
 *
 * BLE / network scanning is user-triggered — the header's trailing toggle calls back to the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun DeviceList(
    connectionState: ConnectionState,
    selectedDevice: String,
    bleDevices: List<DeviceListEntry>,
    usbDevices: List<DeviceListEntry>,
    discoveredTcpDevices: List<DeviceListEntry>,
    recentTcpDevices: List<DeviceListEntry>,
    isBleScanning: Boolean,
    isNetworkScanning: Boolean,
    onSelectDevice: (DeviceListEntry) -> Unit,
    onToggleBleScan: () -> Unit,
    onToggleNetworkScan: () -> Unit,
    onAddManualAddress: (address: String, fullAddress: String) -> Unit,
    onRemoveRecentAddress: (DeviceListEntry) -> Unit,
    modifier: Modifier = Modifier,
    showBleSection: Boolean = true,
    showNetworkSection: Boolean = true,
    showUsbSection: Boolean = true,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val hideAndDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) showAddDialog = false }
    }

    if (showAddDialog) {
        AddDeviceDialog(
            sheetState = sheetState,
            onHideDialog = hideAndDismiss,
            onClickAdd = { address, fullAddress ->
                onAddManualAddress(address, fullAddress)
                hideAndDismiss()
            },
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showBleSection) {
            bluetoothSection(
                bleDevices = bleDevices,
                connectionState = connectionState,
                selectedDevice = selectedDevice,
                isBleScanning = isBleScanning,
                onSelectDevice = onSelectDevice,
                onToggleBleScan = onToggleBleScan,
            )
        }

        if (showNetworkSection) {
            networkSection(
                discoveredTcpDevices = discoveredTcpDevices,
                recentTcpDevices = recentTcpDevices,
                connectionState = connectionState,
                selectedDevice = selectedDevice,
                isNetworkScanning = isNetworkScanning,
                onSelectDevice = onSelectDevice,
                onToggleNetworkScan = onToggleNetworkScan,
                onAddManually = { showAddDialog = true },
                onRemoveRecentAddress = onRemoveRecentAddress,
            )
        }

        if (showUsbSection) {
            usbSection(
                usbDevices = usbDevices,
                connectionState = connectionState,
                selectedDevice = selectedDevice,
                onSelectDevice = onSelectDevice,
            )
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.bluetoothSection(
    bleDevices: List<DeviceListEntry>,
    connectionState: ConnectionState,
    selectedDevice: String,
    isBleScanning: Boolean,
    onSelectDevice: (DeviceListEntry) -> Unit,
    onToggleBleScan: () -> Unit,
) {
    item(key = "header:ble", contentType = "header") {
        DeviceSectionHeader(
            title = stringResource(Res.string.bluetooth),
            showProgress = isBleScanning,
            trailing = {
                ScanToggleAction(
                    isScanning = isBleScanning,
                    scanLabel = stringResource(Res.string.scan_bluetooth_devices),
                    scanningLabel = stringResource(Res.string.scanning_bluetooth),
                    onToggle = onToggleBleScan,
                )
            },
        )
    }
    items(bleDevices, key = { device -> "ble:${device.fullAddress}" }, contentType = { "device" }) { device ->
        DeviceCard(
            device = device,
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            onSelect = onSelectDevice,
            modifier = Modifier.animateItem(),
        )
    }
    if (bleDevices.isEmpty()) {
        item(key = "empty:ble", contentType = "empty") {
            SectionEmptyState(
                text = stringResource(Res.string.no_bluetooth_devices_seen),
                supportingText = stringResource(Res.string.no_bluetooth_devices_hint),
                imageVector = MeshtasticIcons.Bluetooth,
            )
        }
    }
}

private fun LazyListScope.usbSection(
    usbDevices: List<DeviceListEntry>,
    connectionState: ConnectionState,
    selectedDevice: String,
    onSelectDevice: (DeviceListEntry) -> Unit,
) {
    item(key = "header:usb", contentType = "header") { DeviceSectionHeader(title = stringResource(Res.string.usb)) }
    items(usbDevices, key = { device -> "usb:${device.fullAddress}" }, contentType = { "device" }) { device ->
        DeviceCard(
            device = device,
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            onSelect = onSelectDevice,
        )
    }
    if (usbDevices.isEmpty()) {
        item(key = "empty:usb", contentType = "empty") {
            SectionEmptyState(
                text = stringResource(Res.string.no_usb_devices_seen),
                supportingText = stringResource(Res.string.no_usb_devices_hint),
                imageVector = MeshtasticIcons.Usb,
            )
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.networkSection(
    discoveredTcpDevices: List<DeviceListEntry>,
    recentTcpDevices: List<DeviceListEntry>,
    connectionState: ConnectionState,
    selectedDevice: String,
    isNetworkScanning: Boolean,
    onSelectDevice: (DeviceListEntry) -> Unit,
    onToggleNetworkScan: () -> Unit,
    onAddManually: () -> Unit,
    onRemoveRecentAddress: (DeviceListEntry) -> Unit,
) {
    item(key = "header:tcp-discovered", contentType = "header") {
        DeviceSectionHeader(
            title = stringResource(Res.string.network),
            showProgress = isNetworkScanning,
            trailing = {
                ScanToggleAction(
                    isScanning = isNetworkScanning,
                    scanLabel = stringResource(Res.string.scan_network_devices),
                    scanningLabel = stringResource(Res.string.scanning_network),
                    onToggle = onToggleNetworkScan,
                )
            },
        )
    }
    items(
        discoveredTcpDevices,
        key = { device -> "tcp-discovered:${device.fullAddress}" },
        contentType = { "device" },
    ) { device ->
        DeviceCard(
            device = device,
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            onSelect = onSelectDevice,
        )
    }

    if (discoveredTcpDevices.isEmpty() && recentTcpDevices.isEmpty()) {
        item(key = "empty:tcp", contentType = "empty") {
            SectionEmptyState(
                text = stringResource(Res.string.no_network_devices_seen),
                supportingText = stringResource(Res.string.no_network_devices_hint),
                imageVector = MeshtasticIcons.Wifi,
            )
        }
    }

    recentNetworkSection(
        recentTcpDevices = recentTcpDevices,
        connectionState = connectionState,
        selectedDevice = selectedDevice,
        onSelectDevice = onSelectDevice,
        onRemoveRecentAddress = onRemoveRecentAddress,
    )

    item(key = "action:add-network", contentType = "action") {
        ConnectionActionButton(
            onClick = onAddManually,
            icon = MeshtasticIcons.Add,
            text = stringResource(Res.string.add_network_device_manually),
            modifier = Modifier.fillMaxWidth(),
            style = ConnectionActionButtonStyle.Tonal,
        )
    }

    item(key = "spacer:bottom", contentType = "spacer") { Spacer(Modifier.height(16.dp)) }
}

private fun LazyListScope.recentNetworkSection(
    recentTcpDevices: List<DeviceListEntry>,
    connectionState: ConnectionState,
    selectedDevice: String,
    onSelectDevice: (DeviceListEntry) -> Unit,
    onRemoveRecentAddress: (DeviceListEntry) -> Unit,
) {
    if (recentTcpDevices.isEmpty()) return
    item(key = "header:tcp-recent", contentType = "header") {
        DeviceSectionHeader(title = stringResource(Res.string.recent_network_devices))
    }
    items(
        recentTcpDevices,
        key = { device -> "tcp-recent:${device.fullAddress}" },
        contentType = { "device" },
    ) { device ->
        DeviceCard(
            device = device,
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            onSelect = onSelectDevice,
            onDelete = onRemoveRecentAddress,
        )
    }
}

/** Single device row: card + [DeviceListItem]. Factored out so every section renders items identically. */
@Composable
private fun DeviceCard(
    device: DeviceListEntry,
    connectionState: ConnectionState,
    selectedDevice: String,
    onSelect: (DeviceListEntry) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((DeviceListEntry) -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        DeviceListItem(
            connectionState =
            connectionState.takeIf { device.fullAddress == selectedDevice } ?: ConnectionState.Disconnected,
            device = device,
            onSelect = { onSelect(device) },
            onDelete = onDelete?.let { delete -> { delete(device) } },
            rssi = (device as? DeviceListEntry.Ble)?.device?.rssi,
        )
    }
}

/** Compact text-button variant of the scan toggle, used inside a section header's trailing slot. */
@Composable
private fun ScanToggleAction(isScanning: Boolean, scanLabel: String, scanningLabel: String, onToggle: () -> Unit) {
    ConnectionActionButton(
        onClick = onToggle,
        icon = if (isScanning) MeshtasticIcons.Close else MeshtasticIcons.Search,
        text = if (isScanning) scanningLabel else scanLabel,
        style = ConnectionActionButtonStyle.Text,
    )
}

/**
 * Inline empty state for an individual transport section. Follows Material 3 inline empty-state guidance: a small,
 * muted icon, a short title, and an optional supporting hint. Rendered within the section's flow (no full-page
 * takeover); encourages the user to act via the section header's scan toggle rather than duplicating action buttons.
 */
@Composable
private fun SectionEmptyState(
    text: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Dialog for manually adding a TCP device by IP address and port. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceDialog(
    sheetState: SheetState,
    onHideDialog: () -> Unit,
    onClickAdd: (address: String, fullAddress: String) -> Unit,
) {
    val addressState = rememberTextFieldState("")
    val portState = rememberTextFieldState(NetworkConstants.SERVICE_PORT.toString())

    @Suppress("MagicNumber")
    ModalBottomSheet(onDismissRequest = onHideDialog, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = addressState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(Res.string.address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(.7f),
                )

                OutlinedTextField(
                    state = portState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    placeholder = { Text(NetworkConstants.SERVICE_PORT.toString()) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(Res.string.ip_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(.3f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = { onHideDialog() }) {
                    Text(stringResource(Res.string.cancel))
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val address = addressState.text.toString()
                        if (address.isValidAddress()) {
                            val portString = portState.text.toString()
                            val port = portString.toIntOrNull()

                            val combinedString =
                                if (port != null && port != NetworkConstants.SERVICE_PORT) {
                                    "$address:$portString"
                                } else {
                                    address
                                }

                            onClickAdd(combinedString, "t$combinedString")
                        }
                    },
                ) {
                    Text(stringResource(Res.string.add_network_device))
                }
            }
        }
    }
}
