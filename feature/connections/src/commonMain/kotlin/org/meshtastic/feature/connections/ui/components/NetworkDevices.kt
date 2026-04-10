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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.isValidAddress
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.network.repository.NetworkConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_network_device
import org.meshtastic.core.resources.address
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.discovered_network_devices
import org.meshtastic.core.resources.ip_port
import org.meshtastic.core.resources.no_network_devices_found
import org.meshtastic.core.resources.recent_network_devices
import org.meshtastic.core.ui.icon.Add
import org.meshtastic.core.ui.icon.HardwareModel
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.model.DeviceListEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDevices(
    connectionState: ConnectionState,
    discoveredNetworkDevices: List<DeviceListEntry>,
    recentNetworkDevices: List<DeviceListEntry>,
    selectedDevice: String,
    scanModel: ScannerViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (showAddDialog) {
        AddDeviceDialog(
            sheetState = sheetState,
            onHideDialog = {
                scope
                    .launch { sheetState.hide() }
                    .invokeOnCompletion { if (!sheetState.isVisible) showAddDialog = false }
            },
            onClickAdd = { address, fullAddress ->
                scanModel.addRecentAddress(fullAddress, address)
                scanModel.changeDeviceAddress(fullAddress)
                scope
                    .launch { sheetState.hide() }
                    .invokeOnCompletion { if (!sheetState.isVisible) showAddDialog = false }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (discoveredNetworkDevices.isEmpty() && recentNetworkDevices.isEmpty()) {
            EmptyStateContent(
                text = stringResource(Res.string.no_network_devices_found),
                imageVector = MeshtasticIcons.HardwareModel,
                modifier = Modifier.padding(vertical = 32.dp),
            ) {
                Button(onClick = { showAddDialog = true }) {
                    Icon(MeshtasticIcons.Add, contentDescription = null)
                    Text(stringResource(Res.string.add_network_device))
                }
            }
        } else {
            if (discoveredNetworkDevices.isNotEmpty()) {
                discoveredNetworkDevices.DeviceListSection(
                    title = stringResource(Res.string.discovered_network_devices),
                    connectionState = connectionState,
                    selectedDevice = selectedDevice,
                    onSelect = { scanModel.onSelected(it) },
                )
            }

            if (recentNetworkDevices.isNotEmpty()) {
                recentNetworkDevices.DeviceListSection(
                    title = stringResource(Res.string.recent_network_devices),
                    connectionState = connectionState,
                    selectedDevice = selectedDevice,
                    onSelect = { scanModel.onSelected(it) },
                    onDelete = { scanModel.removeRecentAddress(it.fullAddress) },
                )
            }

            Row(modifier = Modifier.padding(top = 8.dp)) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(MeshtasticIcons.Add, contentDescription = stringResource(Res.string.add_network_device))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceDialog(
    sheetState: SheetState,
    onHideDialog: () -> Unit,
    onClickAdd: (address: String, fullAddress: String) -> Unit,
) {
    val addressState = rememberTextFieldState("")
    val portState = rememberTextFieldState(NetworkConstants.SERVICE_PORT.toString())

    @Suppress("MagicNumber")
    ModalBottomSheet(onDismissRequest = onHideDialog, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = addressState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(Res.string.address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(.7f),
                )

                OutlinedTextField(
                    state = portState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    placeholder = { Text(NetworkConstants.SERVICE_PORT.toString()) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(Res.string.ip_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(.3f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = { onHideDialog() }) {
                    Text(stringResource(Res.string.cancel))
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val address = addressState.text.toString()
                        if (address.isValidAddress()) {
                            val portString = portState.text.toString()
                            val port = portString.toIntOrNull()

                            val combinedString =
                                if (port != null && port != NetworkConstants.SERVICE_PORT) {
                                    "$address:$portString"
                                } else {
                                    address
                                }

                            onClickAdd(combinedString, "t$combinedString")
                        }
                    },
                ) {
                    Text(stringResource(Res.string.add_network_device))
                }
            }
        }
    }
}
