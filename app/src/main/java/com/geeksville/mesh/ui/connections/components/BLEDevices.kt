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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.geeksville.mesh.R
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.model.BTScanModel

@Suppress("LongMethod")
@Composable
fun BLEDevices(
    btDevices: List<BTScanModel.DeviceListEntry>,
    selectedDevice: String,
    showBluetoothRationaleDialog: () -> Unit,
    requestBluetoothPermission: (Array<String>) -> Unit,
    scanModel: BTScanModel
) {
    val context = LocalContext.current
    Row {
        Text(
            text = stringResource(R.string.bluetooth),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
    }
    if (btDevices.isNotEmpty()) {
        btDevices.forEach { device ->
            DeviceListItem(
                device,
                device.fullAddress == selectedDevice
            ) {
                scanModel.onSelected(device)
            }
        }
    } else {
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
