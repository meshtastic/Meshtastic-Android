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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.connections.isIPAddress

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("MagicNumber", "LongMethod")
@Composable
fun NetworkDevices(
    connectionState: MeshService.ConnectionState,
    networkDevices: List<BTScanModel.DeviceListEntry>,
    selectedDevice: String,
    scanModel: BTScanModel,
) {
    val manualIpAddress = rememberTextFieldState("")
    val manualIpPort = rememberTextFieldState(NetworkRepository.Companion.SERVICE_PORT.toString())
    Text(
        text = stringResource(R.string.network),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    networkDevices.forEach { device ->
        DeviceListItem(connectionState, device, device.fullAddress == selectedDevice) {
            scanModel.onSelected(device)
        }
    }
    if (networkDevices.filterNot { it.isDisconnect }.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.WifiFind,
                contentDescription = stringResource(R.string.no_network_devices),
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = stringResource(R.string.no_network_devices),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Companion.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, CenterHorizontally)
    ) {
        OutlinedTextField(
            state = manualIpAddress,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(R.string.ip_address)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Companion.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.weight(.7f, fill = false) // Fill 70% of the space
        )
        OutlinedTextField(
            state = manualIpPort,
            placeholder = { Text(NetworkRepository.SERVICE_PORT.toString()) },
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(R.string.ip_port)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Companion.Decimal,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.weight(.3f, fill = false) // Fill remaining space
        )
        IconButton(
            onClick = {
                if (manualIpAddress.text.toString().isIPAddress()) {
                    val fullAddress =
                        "t" + if (
                            manualIpPort.text.isNotEmpty() &&
                            manualIpPort.text.toString().toInt() != NetworkRepository.SERVICE_PORT
                        ) {
                            "${manualIpAddress.text}:${manualIpPort.text}"
                        } else {
                            "${manualIpAddress.text}"
                        }
                    scanModel.onSelected(
                        BTScanModel.DeviceListEntry(
                            "${manualIpAddress.text}",
                            fullAddress,
                            true
                        )
                    )
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.WifiFind,
                contentDescription = stringResource(R.string.add),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
