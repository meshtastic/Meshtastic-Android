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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.ui.connections.ScannerViewModel
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.no_usb_devices
import org.meshtastic.core.service.ConnectionState

@Composable
fun UsbDevices(
    connectionState: ConnectionState,
    usbDevices: List<DeviceListEntry>,
    selectedDevice: String,
    scanModel: ScannerViewModel,
) {
    if (usbDevices.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            EmptyStateContent(
                imageVector = Icons.Rounded.UsbOff,
                text = stringResource(Res.string.no_usb_devices),
                modifier = Modifier.height(160.dp),
            )
        }
    } else {
        usbDevices.DeviceListSection(
            title = "USB",
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            onSelect = scanModel::onSelected,
        )
    }
}
