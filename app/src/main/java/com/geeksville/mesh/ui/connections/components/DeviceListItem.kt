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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
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
import com.geeksville.mesh.model.DeviceListEntry
import org.meshtastic.core.strings.R as Res

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun DeviceListItem(connected: Boolean, device: DeviceListEntry, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val icon =
        when (device) {
            is DeviceListEntry.Ble ->
                if (connected) {
                    Icons.Rounded.BluetoothConnected
                } else {
                    Icons.Rounded.Bluetooth
                }
            is DeviceListEntry.Usb -> Icons.Rounded.Usb
            is DeviceListEntry.Tcp -> Icons.Rounded.Wifi
            is DeviceListEntry.Mock -> Icons.Rounded.Add
        }

    val contentDescription =
        when (device) {
            is DeviceListEntry.Ble -> stringResource(Res.string.bluetooth)
            is DeviceListEntry.Usb -> stringResource(Res.string.serial)
            is DeviceListEntry.Tcp -> stringResource(Res.string.network)
            is DeviceListEntry.Mock -> stringResource(Res.string.add)
        }

    val useSelectable = modifier == Modifier
    ListItem(
        modifier =
        if (useSelectable) {
            modifier.fillMaxWidth().clickable(onClick = onSelect)
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
        trailingContent = { RadioButton(selected = connected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
