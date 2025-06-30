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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BTScanModel

@Composable
fun DeviceListItem(
    device: BTScanModel.DeviceListEntry,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val icon = if (device.isBLE) {
        Icons.Default.Bluetooth
    } else if (device.isUSB) {
        Icons.Default.Usb
    } else if (device.isTCP) {
        Icons.Default.Wifi
    } else if (device.isDisconnect) {
        Icons.Default.Cancel
    } else {
        Icons.Default.Add
    }

    val contentDescription = if (device.isBLE) {
        stringResource(R.string.bluetooth)
    } else if (device.isUSB) {
        stringResource(R.string.serial)
    } else if (device.isTCP) {
        stringResource(R.string.network)
    } else if (device.isDisconnect) {
        stringResource(R.string.disconnect)
    } else {
        stringResource(R.string.add)
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
            ),
        headlineContent = { Text(device.name) },
        leadingContent = {
            Icon(
                icon,
                contentDescription
            )
        },
        supportingContent = {
            if (device.isTCP) {
                Text(device.address)
            }
        },
        trailingContent = {
            if (selected) {
                Icon(
                    Icons.Outlined.CloudDone,
                    stringResource(R.string.connected),
                    tint = Color(color = 0xFF30C047)
                )
            } else {
                Icon(
                    Icons.Default.CloudQueue,
                    stringResource(R.string.not_connected)
                )
            }
        }
    )
}
