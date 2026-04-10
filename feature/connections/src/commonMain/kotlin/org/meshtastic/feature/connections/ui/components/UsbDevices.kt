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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.no_usb_devices_found
import org.meshtastic.core.resources.usb
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.UsbOff
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.model.DeviceListEntry

@Composable
fun UsbDevices(
    connectionState: ConnectionState,
    usbDevices: List<DeviceListEntry>,
    selectedDevice: String,
    scanModel: ScannerViewModel,
) {
    if (usbDevices.isEmpty()) {
        EmptyStateContent(
            text = stringResource(Res.string.no_usb_devices_found),
            imageVector = MeshtasticIcons.UsbOff,
            modifier = Modifier.padding(vertical = 32.dp),
        )
    } else {
        usbDevices.DeviceListSection(
            title = stringResource(Res.string.usb),
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            onSelect = scanModel::onSelected,
        )
    }
}
