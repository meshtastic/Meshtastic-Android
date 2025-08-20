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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.service.ConnectionState

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun DeviceListItem(
    connectionState: ConnectionState,
    device: DeviceListEntry,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (device) {
            is DeviceListEntry.Ble -> Icons.Rounded.Bluetooth
            is DeviceListEntry.Usb -> Icons.Rounded.Usb
            is DeviceListEntry.Tcp -> Icons.Rounded.Wifi
            is DeviceListEntry.Disconnect -> Icons.Rounded.Cancel
            is DeviceListEntry.Mock -> Icons.Rounded.Add
        }

    val contentDescription =
        when (device) {
            is DeviceListEntry.Ble -> stringResource(R.string.bluetooth)
            is DeviceListEntry.Usb -> stringResource(R.string.serial)
            is DeviceListEntry.Tcp -> stringResource(R.string.network)
            is DeviceListEntry.Disconnect -> stringResource(R.string.disconnect)
            is DeviceListEntry.Mock -> stringResource(R.string.add)
        }

    val useSelectable = modifier == Modifier
    ListItem(
        modifier =
        if (useSelectable) {
            modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
        } else {
            modifier.fillMaxWidth()
        },
        headlineContent = { Text(device.name) },
        leadingContent = { Icon(icon, contentDescription) },
        supportingContent = {
            if (device is DeviceListEntry.Tcp) {
                Text(device.address)
            }
        },
        trailingContent = {
            if (device is DeviceListEntry.Disconnect) {
                RadioButton(selected = connectionState == ConnectionState.DISCONNECTED, onClick = null)
            } else {
                RadioButton(selected = connectionState == ConnectionState.CONNECTED, onClick = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
