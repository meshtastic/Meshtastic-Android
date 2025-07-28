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

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.service.MeshService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Composable that displays a list of Bluetooth Low Energy (BLE) devices and allows scanning. It handles Bluetooth
 * permissions using `accompanist-permissions`.
 *
 * @param connectionState The current connection state of the MeshService.
 * @param btDevices List of discovered BLE devices.
 * @param selectedDevice The full address of the currently selected device.
 * @param scanModel The ViewModel responsible for Bluetooth scanning logic.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
fun BLEDevices(
    connectionState: MeshService.ConnectionState,
    btDevices: List<BTScanModel.DeviceListEntry>,
    selectedDevice: String,
    scanModel: BTScanModel,
) {
    LocalContext.current // Used implicitly by stringResource
    val isScanning by scanModel.spinner.collectAsStateWithLifecycle(false)

    // Define permissions needed for Bluetooth scanning based on Android version.
    val bluetoothPermissionsList = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // ACCESS_FINE_LOCATION is required for Bluetooth scanning on pre-S devices.
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionsState =
        rememberMultiplePermissionsState(
            permissions = bluetoothPermissionsList,
            onPermissionsResult = {
                if (it.values.all { granted -> granted }) {
                    scanModel.startScan()
                    scanModel.refreshPermissions()
                } else {
                    // If permissions are not granted, we can show a message or handle it accordingly.
                }
            },
        )

    Text(
        text = stringResource(R.string.bluetooth),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    if (permissionsState.allPermissionsGranted) {
        btDevices.forEach { device ->
            DeviceListItem(
                connectionState = connectionState,
                device = device,
                selected = device.fullAddress == selectedDevice,
                onSelect = { scanModel.onSelected(device) },
                modifier = Modifier,
            )
        }
        if (isScanning) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(96.dp))
                Text(
                    text = stringResource(R.string.scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else if (btDevices.filterNot { it.isDisconnect }.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothDisabled,
                    contentDescription = stringResource(R.string.no_ble_devices),
                    modifier = Modifier.size(96.dp),
                )
                Text(
                    text = stringResource(R.string.no_ble_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    } else {
        // Show a message and a button to grant permissions if not all granted
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val textToShow =
                if (permissionsState.shouldShowRationale) {
                    stringResource(R.string.permission_missing)
                } else {
                    stringResource(R.string.permission_missing_31)
                }
            Text(text = textToShow, style = MaterialTheme.typography.bodyMedium)
        }
    }

    Button(
        enabled = !isScanning, // Keep disabled during scan
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (permissionsState.allPermissionsGranted) {
                scanModel.startScan()
            } else {
                permissionsState.launchMultiplePermissionRequest()
            }
        },
    ) {
        Icon(imageVector = Icons.Default.Bluetooth, contentDescription = stringResource(R.string.scan))
        Text(
            if (permissionsState.allPermissionsGranted) {
                stringResource(R.string.scan)
            } else {
                stringResource(R.string.grant_permissions_and_scan)
            },
        )
    }
}
