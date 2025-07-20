/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.service.MeshService

@Suppress("LongMethod")
@Composable
fun BLEDevices(
    connectionState: MeshService.ConnectionState,
    btDevices: List<BTScanModel.DeviceListEntry>,
    selectedDevice: String,
    showBluetoothRationaleDialog: () -> Unit,
    requestBluetoothPermission: (Array<String>) -> Unit,
    scanModel: BTScanModel
) {
    val context = LocalContext.current
    val isScanning by scanModel.spinner.collectAsStateWithLifecycle(false)
    Text(
        text = stringResource(R.string.bluetooth),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    btDevices.forEach { device ->
        DeviceListItem(
            connectionState = connectionState,
            device = device,
            selected = device.fullAddress == selectedDevice,
            onSelect = { scanModel.onSelected(device) },
            modifier = Modifier
        )
    }
    if (isScanning) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = stringResource(R.string.scanning),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    } else if (btDevices.filterNot { it.isDisconnect }.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothDisabled,
                contentDescription = stringResource(R.string.no_ble_devices),
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = stringResource(R.string.no_ble_devices),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
    Button(
        enabled = !isScanning,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val bluetoothPermissions = context.getBluetoothPermissions()
            if (bluetoothPermissions.isEmpty()) {
                // If no permissions needed, trigger the scan directly (or via ViewModel)
                scanModel.startScan()
            } else {
                if (bluetoothPermissions.any { permission ->
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            context as Activity,
                            permission
                        )
                    }
                ) {
                    showBluetoothRationaleDialog()
                } else {
                    requestBluetoothPermission(bluetoothPermissions)
                }
            }
        }
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = stringResource(R.string.scan)
        )
        Text(stringResource(R.string.scan))
    }
}
