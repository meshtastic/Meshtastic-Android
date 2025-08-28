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

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.service.ConnectionState
import com.geeksville.mesh.ui.common.components.TitledCard
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.connections.isIPAddress
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("MagicNumber", "LongMethod")
@Composable
fun NetworkDevices(
    connectionState: ConnectionState,
    discoveredNetworkDevices: List<DeviceListEntry>,
    recentNetworkDevices: List<DeviceListEntry>,
    selectedDevice: String,
    scanModel: BTScanModel,
) {
    val searchDialogState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSearchDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var deviceToDelete by remember { mutableStateOf<DeviceListEntry?>(null) }

    if (showSearchDialog) {
        AddDeviceDialog(
            searchDialogState,
            onHideDialog = { showSearchDialog = false },
            onClickAdd = { ipAddress, fullAddress ->
                scanModel.onSelected(DeviceListEntry.Tcp(ipAddress, fullAddress))
                showSearchDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        deviceToDelete?.let {
            ConfirmDeleteDialog(
                it.fullAddress,
                onHideDialog = { showDeleteDialog = false },
                onConfirm = { deviceFullAddress -> scanModel.removeRecentAddress(deviceFullAddress) },
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val addButton: @Composable () -> Unit = {
            Button(onClick = { showSearchDialog = true }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.add_network_device))
                Text(stringResource(R.string.add_network_device))
            }
        }

        when {
            discoveredNetworkDevices.isEmpty() && recentNetworkDevices.isEmpty() -> {
                EmptyStateContent(
                    imageVector = Icons.Rounded.Wifi,
                    text = stringResource(R.string.no_network_devices),
                    actionButton = addButton,
                )
            }

            else -> {
                if (recentNetworkDevices.isNotEmpty()) {
                    TitledCard(title = stringResource(R.string.recent_network_devices)) {
                        recentNetworkDevices.forEach { device ->
                            DeviceListItem(
                                connected =
                                connectionState == ConnectionState.CONNECTED &&
                                    device.fullAddress == selectedDevice,
                                device = device,
                                onSelect = { scanModel.onSelected(device) },
                                modifier =
                                Modifier.combinedClickable(
                                    onClick = { scanModel.onSelected(device) },
                                    onLongClick = {
                                        deviceToDelete = device
                                        showDeleteDialog = true
                                    },
                                ),
                            )
                        }
                    }
                }

                if (discoveredNetworkDevices.isNotEmpty()) {
                    TitledCard(title = stringResource(R.string.discovered_network_devices)) {
                        discoveredNetworkDevices.forEach { device ->
                            DeviceListItem(
                                connected =
                                connectionState == ConnectionState.CONNECTED &&
                                    device.fullAddress == selectedDevice,
                                device = device,
                                onSelect = { scanModel.onSelected(device) },
                            )
                        }
                    }
                }

                addButton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceDialog(
    sheetState: SheetState,
    onHideDialog: () -> Unit,
    onClickAdd: (ipAddress: String, fullAddress: String) -> Unit,
) {
    val ipState = rememberTextFieldState("")
    val portState = rememberTextFieldState(NetworkRepository.SERVICE_PORT.toString())

    val scope = rememberCoroutineScope()

    @Suppress("MagicNumber")
    ModalBottomSheet(onDismissRequest = onHideDialog, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = ipState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(R.string.ip_address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(.7f),
                )

                OutlinedTextField(
                    state = portState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    placeholder = { Text(NetworkRepository.SERVICE_PORT.toString()) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(R.string.ip_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(.3f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = { onHideDialog() }) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ipAddress = ipState.text.toString()
                        if (ipAddress.isIPAddress()) {
                            val portString = portState.text.toString()

                            val combinedString =
                                if (portString.isNotEmpty() && portString.toInt() != NetworkRepository.SERVICE_PORT) {
                                    "$ipAddress:$portString"
                                } else {
                                    ipAddress
                                }

                            onClickAdd(ipState.text.toString(), "t$combinedString")

                            scope
                                .launch { sheetState.hide() }
                                .invokeOnCompletion {
                                    if (!sheetState.isVisible) {
                                        onHideDialog()
                                    }
                                }
                        }
                    },
                ) {
                    Text(stringResource(R.string.add_network_device))
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    fullAddressToDelete: String,
    onHideDialog: () -> Unit,
    onConfirm: (deviceFullAddress: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onHideDialog,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.confirm_delete_node)) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(fullAddressToDelete)
                    onHideDialog()
                },
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = { Button(onClick = { onHideDialog() }) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun SearchDialogPreview() {
    AppTheme {
        AddDeviceDialog(sheetState = rememberModalBottomSheetState(), onHideDialog = {}, onClickAdd = { _, _ -> })
    }
}

@PreviewLightDark
@Composable
private fun ConfirmDeleteDialogPreview() {
    AppTheme { ConfirmDeleteDialog(fullAddressToDelete = "", onHideDialog = {}, onConfirm = {}) }
}
