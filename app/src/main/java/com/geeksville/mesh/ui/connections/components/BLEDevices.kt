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
package com.geeksville.mesh.ui.connections.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.DeviceListEntry
import no.nordicsemi.android.common.scanner.rememberFilterState
import no.nordicsemi.android.common.scanner.view.ScannerView
import org.meshtastic.core.ble.MeshtasticBleConstants.BLE_NAME_PATTERN
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.service.ConnectionState

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
fun BLEDevices(connectionState: ConnectionState, selectedDevice: String, scanModel: BTScanModel) {
    val filterState =
        rememberFilterState(
            filter = {
                Any {
                    ServiceUuid(SERVICE_UUID)
                    Name(Regex(BLE_NAME_PATTERN))
                }
            },
        )
    ScannerView(
        state = filterState,
        onScanResultSelected = { result -> scanModel.onSelected(DeviceListEntry.Ble(result.peripheral)) },
        deviceItem = { result ->
            val device = remember(result.peripheral.address) { DeviceListEntry.Ble(result.peripheral) }
            DeviceListItem(
                connectionState =
                connectionState.takeIf { device.fullAddress == selectedDevice } ?: ConnectionState.Disconnected,
                device = device,
                onSelect = { scanModel.onSelected(device) },
            )
        },
    )
}
