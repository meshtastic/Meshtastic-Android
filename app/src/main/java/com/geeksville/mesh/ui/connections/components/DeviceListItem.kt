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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.model.DeviceListEntry
import kotlinx.coroutines.delay
import no.nordicsemi.android.common.ui.view.RssiIcon
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.network
import org.meshtastic.core.resources.serial
import org.meshtastic.core.ui.component.NodeChip

private const val RSSI_UPDATE_RATE_MS = 2000L

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
    // Throttle the RSSI updates to match the connected device polling rate
    var displayedRssi by remember { mutableIntStateOf(rssi ?: 0) }
    LaunchedEffect(rssi) {
        if (displayedRssi == 0) {
            displayedRssi = rssi ?: 0
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(RSSI_UPDATE_RATE_MS)
            displayedRssi = rssi ?: 0
        }
    }

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

    val clickableModifier =
        if (useSelectable) {
            Modifier.indication(interactionSource, indication).pointerInput(device.fullAddress, onDelete) {
                detectTapGestures(onTap = { onSelect() }, onLongPress = onDelete?.let { { it() } })
            }
        } else {
            Modifier
        }

    ListItem(
        modifier = modifier.fillMaxWidth().then(clickableModifier).padding(vertical = 4.dp),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                device.node?.let { node -> NodeChip(node = node) }
                    ?: Text(text = device.name, style = MaterialTheme.typography.titleLarge)
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(32.dp),
                tint =
                if (connectionState.isConnected()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = { Text(text = device.address, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rssi != null) {
                    RssiIcon(rssi = displayedRssi)
                }

                if (connectionState.isConnecting()) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    RadioButton(selected = connectionState.isConnected(), onClick = null)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
