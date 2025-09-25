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
import android.content.Intent
import android.os.Build
import android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.service.ConnectionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.TitledCard

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
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun BLEDevices(
    connectionState: ConnectionState,
    btDevices: List<DeviceListEntry>,
    selectedDevice: String,
    scanModel: BTScanModel,
    bluetoothEnabled: Boolean,
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
                if (it.values.all { granted -> granted } && bluetoothEnabled) {
                    scanModel.startScan()
                    scanModel.refreshPermissions()
                } else {
                    // If permissions are not granted, we can show a message or handle it accordingly.
                }
            },
        )

    val settingsLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
            // Eventually auto scan once bluetooth is available
            // checkPermissionsAndScan(permissionsState, scanModel, bluetoothEnabled)
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (permissionsState.allPermissionsGranted) {
            when {
                !bluetoothEnabled -> {
                    val context = LocalContext.current
                    EmptyStateContent(
                        imageVector = Icons.Rounded.BluetoothDisabled,
                        text = stringResource(R.string.bluetooth_disabled),
                        actionButton = {
                            val intent = Intent(ACTION_BLUETOOTH_SETTINGS)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                Button(onClick = { settingsLauncher.launch(intent) }) {
                                    Text(text = stringResource(R.string.open_settings))
                                }
                            }
                        },
                    )
                }

                else -> {
                    val scanButton: @Composable () -> Unit = {
                        Button(
                            enabled = !isScanning,
                            onClick = { checkPermissionsAndScan(permissionsState, scanModel, true) },
                        ) {
                            Box {
                                // Still measure for the icon and text when scanning, so the button's size doesn't jump
                                // around.
                                Row(modifier = Modifier.alpha(if (isScanning) 0f else 1f)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.scan),
                                    )
                                    Text(stringResource(R.string.scan))
                                }

                                if (isScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.Center))
                                }
                            }
                        }
                    }

                    if (btDevices.isEmpty()) {
                        EmptyStateContent(
                            imageVector = Icons.Rounded.BluetoothDisabled,
                            text =
                            if (isScanning) {
                                stringResource(R.string.scanning_bluetooth)
                            } else {
                                stringResource(R.string.no_ble_devices)
                            },
                            actionButton = scanButton,
                        )
                    } else {
                        TitledCard(title = stringResource(R.string.bluetooth_paired_devices)) {
                            btDevices.forEach { device ->
                                val connected =
                                    connectionState == ConnectionState.CONNECTED && device.fullAddress == selectedDevice
                                DeviceListItem(
                                    connected = connected,
                                    device = device,
                                    onSelect = { scanModel.onSelected(device) },
                                    modifier = Modifier,
                                )
                            }
                        }

                        scanButton()
                    }
                }
            }
        } else {
            // Show a message and a button to grant permissions if not all granted
            EmptyStateContent(
                text =
                if (permissionsState.shouldShowRationale) {
                    stringResource(R.string.permission_missing)
                } else {
                    stringResource(R.string.permission_missing_31)
                },
                actionButton = {
                    Button(onClick = { checkPermissionsAndScan(permissionsState, scanModel, bluetoothEnabled) }) {
                        Text(text = stringResource(R.string.grant_permissions))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private fun checkPermissionsAndScan(
    permissionsState: MultiplePermissionsState,
    scanModel: BTScanModel,
    bluetoothEnabled: Boolean,
) {
    if (permissionsState.allPermissionsGranted && bluetoothEnabled) {
        scanModel.startScan()
    } else {
        permissionsState.launchMultiplePermissionRequest()
    }
}
