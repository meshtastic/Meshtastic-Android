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
package org.meshtastic.app.ui.connections.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.nordicsemi.android.common.scanner.rememberFilterState
import no.nordicsemi.android.common.scanner.view.ScannerView
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.app.model.DeviceListEntry
import org.meshtastic.app.ui.connections.ScannerViewModel
import org.meshtastic.core.ble.AndroidBleDevice
import org.meshtastic.core.ble.MeshtasticBleConstants.BLE_NAME_PATTERN
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_available_devices

/**
 * Composable that displays a list of Bluetooth Low Energy (BLE) devices and allows scanning. It handles Bluetooth
 * permissions and hardware state using Nordic Common Libraries' ScannerView.
 *
 * @param connectionState The current connection state of the MeshService.
 * @param selectedDevice The full address of the currently selected device.
 * @param scanModel The ViewModel responsible for Bluetooth scanning logic.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BLEDevices(connectionState: ConnectionState, selectedDevice: String, scanModel: ScannerViewModel) {
    val filterState =
        rememberFilterState(
            filter = {
                Any {
                    ServiceUuid(SERVICE_UUID)
                    Name(Regex(BLE_NAME_PATTERN))
                }
            },
        )
    val bleDevices by scanModel.bleDevicesForUi.collectAsStateWithLifecycle()

    Column {
        Text(
            text = stringResource(Res.string.bluetooth_available_devices),
            modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp).fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        ScannerView(
            state = filterState,
            onScanResultSelected = { result ->
                scanModel.onSelected(DeviceListEntry.Ble(AndroidBleDevice(result.peripheral)))
            },
            deviceItem = { result ->
                val device =
                    remember(result.peripheral.address, bleDevices) {
                        bleDevices.find { it.fullAddress == "x${result.peripheral.address}" }
                            ?: DeviceListEntry.Ble(AndroidBleDevice(result.peripheral))
                    }
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
                        rssi = result.rssi,
                    )
                }
            },
        )
    }
}
