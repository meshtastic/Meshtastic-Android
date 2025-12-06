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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.DeviceListEntry
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.bluetooth_available_devices
import org.meshtastic.core.strings.bluetooth_disabled
import org.meshtastic.core.strings.bluetooth_paired_devices
import org.meshtastic.core.strings.grant_permissions
import org.meshtastic.core.strings.no_ble_devices
import org.meshtastic.core.strings.open_settings
import org.meshtastic.core.strings.permission_missing
import org.meshtastic.core.strings.permission_missing_31
import org.meshtastic.core.strings.scan
import org.meshtastic.core.strings.scanning_bluetooth
import org.meshtastic.core.ui.util.showToast

/**
 * Composable that displays a list of Bluetooth Low Energy (BLE) devices and allows scanning. It handles Bluetooth
 * permissions using `accompanist-permissions`.
 *
 * @param connectionState The current connection state of the MeshService.
 * @param bondedDevices List of discovered BLE devices.
 * @param availableDevices
 * @param selectedDevice The full address of the currently selected device.
 * @param scanModel The ViewModel responsible for Bluetooth scanning logic.
 * @param bluetoothEnabled
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun BLEDevices(
    connectionState: ConnectionState,
    bondedDevices: List<DeviceListEntry>,
    availableDevices: List<DeviceListEntry>,
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
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
    }

    val context = LocalContext.current
    val permsMissing =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stringResource(Res.string.permission_missing_31)
        } else {
            stringResource(Res.string.permission_missing)
        }
    val coroutineScope = rememberCoroutineScope()

    val singlePermissionState =
        rememberPermissionState(
            permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            onPermissionResult = { granted ->
                scanModel.refreshPermissions()
                scanModel.startScan()
            },
        )

    val permissionsState =
        rememberMultiplePermissionsState(
            permissions = bluetoothPermissionsList,
            onPermissionsResult = { permissions ->
                val granted = permissions.values.all { it }
                if (permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                    coroutineScope.launch { context.showToast(permsMissing) }
                    singlePermissionState.launchPermissionRequest()
                }
                if (granted) {
                    scanModel.refreshPermissions()
                    scanModel.startScan()
                } else {
                    coroutineScope.launch { context.showToast(permsMissing) }
                }
            },
        )

    val settingsLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
            scanModel.refreshPermissions()
            scanModel.startScan()
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
                        text = stringResource(Res.string.bluetooth_disabled),
                        actionButton = {
                            val intent = Intent(ACTION_BLUETOOTH_SETTINGS)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                Button(onClick = { settingsLauncher.launch(intent) }) {
                                    Text(text = stringResource(Res.string.open_settings))
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
                                        contentDescription = stringResource(Res.string.scan),
                                    )
                                    Text(stringResource(Res.string.scan))
                                }

                                if (isScanning) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(24.dp).align(Alignment.Center),
                                    )
                                }
                            }
                        }
                    }

                    if (bondedDevices.isEmpty() && availableDevices.isEmpty()) {
                        EmptyStateContent(
                            imageVector = Icons.Rounded.BluetoothDisabled,
                            text =
                            if (isScanning) {
                                stringResource(Res.string.scanning_bluetooth)
                            } else {
                                stringResource(Res.string.no_ble_devices)
                            },
                            actionButton = scanButton,
                        )
                    } else {
                        bondedDevices.DeviceListSection(
                            title = stringResource(Res.string.bluetooth_paired_devices),
                            connectionState = connectionState,
                            selectedDevice = selectedDevice,
                            onSelect = scanModel::onSelected,
                        )

                        availableDevices.DeviceListSection(
                            title = stringResource(Res.string.bluetooth_available_devices),
                            connectionState = connectionState,
                            selectedDevice = selectedDevice,
                            onSelect = scanModel::onSelected,
                        )

                        scanButton()
                    }
                }
            }
        } else {
            // Show a message and a button to grant permissions if not all granted
            EmptyStateContent(
                text =
                if (permissionsState.shouldShowRationale) {
                    stringResource(Res.string.permission_missing)
                } else {
                    stringResource(Res.string.permission_missing_31)
                },
                actionButton = {
                    Button(onClick = { checkPermissionsAndScan(permissionsState, scanModel, bluetoothEnabled) }) {
                        Text(text = stringResource(Res.string.grant_permissions))
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
