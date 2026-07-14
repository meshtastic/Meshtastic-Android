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
package org.meshtastic.feature.connections.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.ConnectionStatus
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.UsbDeviceData
import org.meshtastic.feature.connections.ui.components.ConnectingDeviceInfo
import org.meshtastic.feature.connections.ui.components.DeviceList
import org.meshtastic.feature.connections.ui.components.DeviceListItem
import org.meshtastic.feature.connections.ui.components.DeviceSectionHeader
import org.meshtastic.feature.connections.ui.components.DisconnectButton
import org.meshtastic.feature.connections.ui.components.EmptyStateContent
import org.meshtastic.feature.connections.ui.components.TransportSelector
import org.meshtastic.proto.User

private const val PREVIEW_BLE_RSSI = -60

@PreviewLightDark
@Composable
fun DeviceListItemPreview() {
    val device = DeviceListEntry.Tcp(name = "Meshtastic_abcd", fullAddress = "s192.168.1.100")
    AppTheme { DeviceListItem(connectionState = ConnectionState.Disconnected, device = device, onSelect = {}) }
}

// Previously-connected device: shows the short-name NodeChip alongside the unique long name (#5808), which wraps to a
// second line rather than truncating.
@PreviewLightDark
@Composable
private fun DeviceListItemWithLongNamePreview() {
    val node = Node(num = 13444, user = User(short_name = "AB12", long_name = "James' Rooftop Solar Repeater"))
    val device = DeviceListEntry.Tcp(name = "Meshtastic_ab12", fullAddress = "s192.168.1.101", node = node)
    AppTheme { DeviceListItem(connectionState = ConnectionState.Disconnected, device = device, onSelect = {}) }
}

@PreviewLightDark
@Composable
fun DisconnectButtonPreview() {
    AppTheme { DisconnectButton(onClick = {}) }
}

@PreviewLightDark
@Composable
fun ConnectingDeviceInfoPreview() {
    AppTheme {
        ConnectingDeviceInfo(
            deviceName = "Meshtastic_abcd",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            connectionStatus = ConnectionStatus.CONNECTING,
            connectionProgress = "Discovering services...",
            onClickDisconnect = {},
        )
    }
}

@PreviewLightDark
@Composable
fun EmptyStateContentPreview() {
    // Bounded height so the docs reference is a tight crop of the empty-state block, not a full-screen frame
    // (EmptyStateContent fills its parent to center its content).
    AppTheme {
        Surface(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            EmptyStateContent(text = "No devices found", imageVector = MeshtasticIcons.Search)
        }
    }
}

// Real Connections-screen Bluetooth scan: the device list with the scan-in-progress header and a discovered radio.
// Replaces the old wifi-provision "Searching for device…" splash that was mislabeled as the BLE scan in the docs.
@PreviewLightDark
@Composable
fun BluetoothScanPreview() {
    AppTheme {
        Surface(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            DeviceList(
                connectionState = ConnectionState.Disconnected,
                selectedDevice = "",
                bleDevices =
                listOf(
                    DeviceListEntry.Ble(PreviewBleDevice(address = "AA:BB:CC:DD:EE:FF", name = "Meshtastic_abcd")),
                ),
                usbDevices = emptyList(),
                discoveredTcpDevices = emptyList(),
                recentTcpDevices = emptyList(),
                isBleScanning = true,
                isNetworkScanning = false,
                activeTransport = DeviceType.BLE,
                onSelectDevice = {},
                onToggleBleScan = {},
                onToggleNetworkScan = {},
                onAddManualAddress = { _, _ -> },
                onRemoveRecentAddress = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
fun DeviceSectionHeaderPreview() {
    AppTheme { DeviceSectionHeader(title = "Bluetooth Devices", showProgress = true) }
}

@PreviewLightDark
@Composable
fun TransportSelectorPreview() {
    AppTheme { TransportSelector(activeTransport = DeviceType.BLE, onSelectTransport = {}) }
}

@PreviewLightDark
@Composable
private fun BluetoothPanePreview() {
    AppTheme {
        DeviceList(
            connectionState = ConnectionState.Disconnected,
            selectedDevice = "",
            bleDevices =
            listOf(DeviceListEntry.Ble(PreviewBleDevice(address = "AA:BB:CC:DD:EE:FF", name = "Meshtastic_abcd"))),
            usbDevices = emptyList(),
            discoveredTcpDevices = emptyList(),
            recentTcpDevices = emptyList(),
            isBleScanning = false,
            isNetworkScanning = false,
            activeTransport = DeviceType.BLE,
            onSelectDevice = {},
            onToggleBleScan = {},
            onToggleNetworkScan = {},
            onAddManualAddress = { _, _ -> },
            onRemoveRecentAddress = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun NetworkPanePreview() {
    AppTheme {
        DeviceList(
            connectionState = ConnectionState.Disconnected,
            selectedDevice = "",
            bleDevices = emptyList(),
            usbDevices = emptyList(),
            discoveredTcpDevices = listOf(DeviceListEntry.Tcp(name = "Meshtastic_tcp", fullAddress = "t192.168.1.25")),
            recentTcpDevices = listOf(DeviceListEntry.Tcp(name = "192.168.1.99", fullAddress = "t192.168.1.99")),
            isBleScanning = false,
            isNetworkScanning = true,
            activeTransport = DeviceType.TCP,
            onSelectDevice = {},
            onToggleBleScan = {},
            onToggleNetworkScan = {},
            onAddManualAddress = { _, _ -> },
            onRemoveRecentAddress = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun UsbPanePreview() {
    AppTheme {
        DeviceList(
            connectionState = ConnectionState.Disconnected,
            selectedDevice = "",
            bleDevices = emptyList(),
            usbDevices =
            listOf(
                DeviceListEntry.Usb(
                    usbData = object : UsbDeviceData {},
                    name = "T-Deck",
                    fullAddress = "s/dev/bus/usb/001/002",
                    bonded = true,
                ),
            ),
            discoveredTcpDevices = emptyList(),
            recentTcpDevices = emptyList(),
            isBleScanning = false,
            isNetworkScanning = false,
            activeTransport = DeviceType.USB,
            onSelectDevice = {},
            onToggleBleScan = {},
            onToggleNetworkScan = {},
            onAddManualAddress = { _, _ -> },
            onRemoveRecentAddress = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun UsbPaneEmptyPreview() {
    AppTheme {
        DeviceList(
            connectionState = ConnectionState.Disconnected,
            selectedDevice = "",
            bleDevices = emptyList(),
            usbDevices = emptyList(),
            discoveredTcpDevices = emptyList(),
            recentTcpDevices = emptyList(),
            isBleScanning = false,
            isNetworkScanning = false,
            activeTransport = DeviceType.USB,
            onSelectDevice = {},
            onToggleBleScan = {},
            onToggleNetworkScan = {},
            onAddManualAddress = { _, _ -> },
            onRemoveRecentAddress = {},
        )
    }
}

private class PreviewBleDevice(
    override val address: String,
    override val name: String?,
    override val rssi: Int? = PREVIEW_BLE_RSSI,
) : BleDevice {
    private val stateFlow = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())

    override val state: StateFlow<BleConnectionState> = stateFlow.asStateFlow()
    override val isBonded: Boolean = true
    override val isConnected: Boolean = false

    override suspend fun readRssi(): Int = rssi ?: PREVIEW_BLE_RSSI

    override suspend fun bond() = Unit
}
