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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.service.ConnectionState
import com.geeksville.mesh.ui.common.theme.StatusColors.StatusGreen
import com.geeksville.mesh.ui.common.theme.StatusColors.StatusRed

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
            is DeviceListEntry.Ble -> Icons.Default.Bluetooth
            is DeviceListEntry.Usb -> Icons.Default.Usb
            is DeviceListEntry.Tcp -> Icons.Default.Wifi
            is DeviceListEntry.Disconnect -> Icons.Default.Cancel
            is DeviceListEntry.Mock -> Icons.Default.Add
        }

    val contentDescription =
        when (device) {
            is DeviceListEntry.Ble -> stringResource(R.string.bluetooth)
            is DeviceListEntry.Usb -> stringResource(R.string.serial)
            is DeviceListEntry.Tcp -> stringResource(R.string.network)
            is DeviceListEntry.Disconnect -> stringResource(R.string.disconnect)
            is DeviceListEntry.Mock -> stringResource(R.string.add)
        }

    val colors =
        when {
            selected && device is DeviceListEntry.Disconnect -> {
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    headlineColor = MaterialTheme.colorScheme.onErrorContainer,
                    leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    supportingColor = MaterialTheme.colorScheme.onErrorContainer,
                    trailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            selected -> { // Standard selection for other device types
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingIconColor =
                    when (connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.StatusGreen
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.StatusRed
                        else ->
                            MaterialTheme.colorScheme
                                .onPrimaryContainer // Fallback for other states (e.g. connecting)
                    },
                )
            }

            else -> {
                ListItemDefaults.colors()
            }
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
        leadingContent = {
            Icon(
                icon, // icon is already CloudOff if device.isDisconnect
                contentDescription,
            )
        },
        supportingContent = {
            if (device is DeviceListEntry.Tcp) {
                Text(device.address)
            }
        },
        trailingContent = {
            if (device is DeviceListEntry.Disconnect) {
                Icon(imageVector = Icons.Default.CloudOff, contentDescription = stringResource(R.string.disconnect))
            } else if (connectionState == ConnectionState.CONNECTED) {
                Icon(imageVector = Icons.Default.CloudDone, contentDescription = stringResource(R.string.connected))
            } else {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = stringResource(R.string.not_connected),
                )
            }
        },
        colors = colors,
    )
}
