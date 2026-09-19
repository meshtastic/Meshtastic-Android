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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.Node
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connections
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.firmware_version
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedInfo
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedText
import org.meshtastic.feature.connections.ui.components.DeviceList
import org.meshtastic.feature.connections.ui.components.TransportSelector

/**
 * The connections tab as the app lays it out: the connected radio's card and the transport selector first, the
 * discovery list second, side by side in the same [AdaptiveTwoPane] on an expanded window. Base Camp is connected over
 * Bluetooth, with two more of the group's radios in range; the list shows one transport at a time, the app's rule.
 */
@Composable
internal fun ConnectionsScreen(mesh: SampleMesh) {
    val radios = mesh.bleRadios()
    MarketingTheme {
        AppShell(TopLevelDestination.Connect) {
            Scaffold(
                topBar = {
                    MainAppBar(
                        title = stringResource(Res.string.connections),
                        ourNode = mesh.baseCamp,
                        showNodeChip = true,
                        canNavigateUp = false,
                        onNavigateUp = {},
                        onClickChip = {},
                        actions = {},
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AdaptiveTwoPane(
                        first = {
                            ConnectedCard(mesh.baseCamp)
                            TransportSelector(activeTransport = DeviceType.BLE, onSelectTransport = {})
                        },
                        second = {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                DeviceList(
                                    connectionState = ConnectionState.Connected,
                                    selectedDevice = radios.first().fullAddress,
                                    bleDevices = radios,
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
                        },
                    )
                }
            }
        }
    }
}

/** The connected-device card in the app's connected state: the same card shell and the feature's own contents. */
@Composable
private fun ConnectedCard(node: Node) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), contentAlignment = Alignment.Center) {
            CurrentlyConnectedInfo(
                node = node,
                text =
                CurrentlyConnectedText(
                    unknownLabel = stringResource(Res.string.unknown),
                    rssiLabel = stringResource(Res.string.rssi),
                    disconnectLabel = stringResource(Res.string.disconnect),
                    firmwareVersion = stringResource(Res.string.firmware_version, SampleMesh.FIRMWARE_VERSION),
                ),
                onNavigateToNodeDetails = {},
                onClickDisconnect = {},
            )
        }
    }
}

/**
 * The Bluetooth pane's radios: Base Camp, bonded and connected, so its row carries the node chip the app shows for a
 * radio it has met before; then two of the group's radios advertising nearby under their factory names, which are the
 * node number's last four hex digits.
 */
private fun SampleMesh.bleRadios(): List<DeviceListEntry> = listOf(
    DeviceListEntry.Ble(SampleBleDevice(baseCamp, rssi = -58, connected = true), node = baseCamp),
    DeviceListEntry.Ble(SampleBleDevice(trailhead, rssi = -71, connected = false)),
    DeviceListEntry.Ble(SampleBleDevice(sarahsTruck, rssi = -84, connected = false)),
)

/** A radio as the scanner would report it: advertised name and address derived from the node it belongs to. */
private class SampleBleDevice(node: Node, override val rssi: Int, connected: Boolean) : BleDevice {
    private val hex = node.num.toUInt().toString(16).padStart(8, '0')

    override val name: String = "Meshtastic_${hex.takeLast(4)}"

    override val address: String = hex.takeLast(6).uppercase().chunked(2).joinToString(":", prefix = "F0:9E:9E:")

    override val state: StateFlow<BleConnectionState> =
        MutableStateFlow(if (connected) BleConnectionState.Connected else BleConnectionState.Disconnected())

    override val isBonded: Boolean = true

    override val isConnected: Boolean = connected

    override suspend fun readRssi(): Int = rssi

    override suspend fun bond() = Unit
}
