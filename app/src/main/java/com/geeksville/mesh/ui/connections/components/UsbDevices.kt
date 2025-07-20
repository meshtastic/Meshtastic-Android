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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.service.MeshService

@Composable
fun UsbDevices(
    connectionState: MeshService.ConnectionState,
    usbDevices: List<BTScanModel.DeviceListEntry>,
    selectedDevice: String,
    scanModel: BTScanModel
) {
    Text(
        text = stringResource(R.string.serial),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    usbDevices.forEach { device ->
        DeviceListItem(
            connectionState = connectionState,
            device = device,
            selected = device.fullAddress == selectedDevice,
            onSelect = { scanModel.onSelected(device) },
            modifier = Modifier
        )
    }
    if (usbDevices.filterNot { it.isDisconnect || it.isMock }.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.UsbOff,
                contentDescription = stringResource(R.string.no_usb_devices),
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = stringResource(R.string.no_usb_devices),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
