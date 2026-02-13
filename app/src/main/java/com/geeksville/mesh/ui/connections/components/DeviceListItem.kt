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

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.model.DeviceListEntry
import no.nordicsemi.android.common.ui.view.RssiIcon
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add
import org.meshtastic.core.strings.bluetooth
import org.meshtastic.core.strings.network
import org.meshtastic.core.strings.serial

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun DeviceListItem(
    connectionState: ConnectionState,
    device: DeviceListEntry,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    rssi: Int? = null,
) {
    val icon =
        when (device) {
            is DeviceListEntry.Ble ->
                if (connectionState.isConnected()) {
                    Icons.Rounded.BluetoothConnected
                } else if (connectionState.isConnecting()) {
                    Icons.AutoMirrored.Rounded.BluetoothSearching
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
    val interactionSource = remember { MutableInteractionSource() }
    val indication: Indication = LocalIndication.current

    ListItem(
        modifier =
        if (useSelectable && onDelete != null) {
            modifier.fillMaxWidth().indication(interactionSource, indication).pointerInput(onDelete) {
                detectTapGestures(onTap = { onSelect() }, onLongPress = { onDelete() })
            }
        } else if (useSelectable) {
            modifier.fillMaxWidth().indication(interactionSource, indication).pointerInput(Unit) {
                detectTapGestures(onTap = { onSelect() })
            }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rssi != null) {
                    RssiIcon(rssi = rssi)
                }

                if (connectionState.isConnecting()) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    RadioButton(selected = connectionState.isConnected(), onClick = null)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
