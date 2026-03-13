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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_available_devices
import org.meshtastic.feature.connections.ScannerViewModel

/**
 * Composable that displays a list of Bluetooth Low Energy (BLE) devices and allows scanning.
 *
 * @param connectionState The current connection state of the MeshService.
 * @param selectedDevice The full address of the currently selected device.
 * @param scanModel The ViewModel responsible for Bluetooth scanning logic.
 */
@Composable
fun BLEDevices(connectionState: ConnectionState, selectedDevice: String, scanModel: ScannerViewModel) {
    val bleDevices by scanModel.bleDevicesForUi.collectAsStateWithLifecycle()
    val isScanning by scanModel.isBleScanning.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        scanModel.startBleScan()
        onDispose { scanModel.stopBleScan() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.bluetooth_available_devices),
            modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp).fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            items(bleDevices, key = { it.fullAddress }) { device ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    DeviceListItem(
                        connectionState =
                        connectionState.takeIf { device.fullAddress == selectedDevice }
                            ?: ConnectionState.Disconnected,
                        device = device,
                        onSelect = { scanModel.onSelected(device) },
                        rssi = null,
                    )
                }
            }
        }
    }
}
