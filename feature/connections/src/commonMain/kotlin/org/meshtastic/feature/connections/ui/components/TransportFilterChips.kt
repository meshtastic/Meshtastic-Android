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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.transport_ble
import org.meshtastic.core.resources.transport_tcp
import org.meshtastic.core.resources.transport_usb
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Usb
import org.meshtastic.core.ui.icon.Wifi

/**
 * Inclusive transport-visibility filter chips rendered below the connection card. Each chip independently toggles the
 * visibility of its corresponding section ([showBle] → BLE, [showNetwork] → Network/TCP, [showUsb] → USB) in the device
 * list. Selections are persisted by the caller (defaults to all-on).
 */
@Composable
fun TransportFilterChips(
    showBle: Boolean,
    showNetwork: Boolean,
    showUsb: Boolean,
    onToggleBle: () -> Unit,
    onToggleNetwork: () -> Unit,
    onToggleUsb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        TransportChip(
            selected = showBle,
            label = Res.string.transport_ble,
            icon = MeshtasticIcons.Bluetooth,
            onClick = onToggleBle,
        )
        TransportChip(
            selected = showNetwork,
            label = Res.string.transport_tcp,
            icon = MeshtasticIcons.Wifi,
            onClick = onToggleNetwork,
        )
        TransportChip(
            selected = showUsb,
            label = Res.string.transport_usb,
            icon = MeshtasticIcons.Usb,
            onClick = onToggleUsb,
        )
    }
}

@Composable
private fun TransportChip(selected: Boolean, label: StringResource, icon: ImageVector, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
    )
}
