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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.geeksville.mesh.ui.connections.DeviceType
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.ui.icon.Device
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

@Composable
fun ConnectionsNavIcon(modifier: Modifier = Modifier, connectionState: ConnectionState, deviceType: DeviceType?) {
    val tint =
        when (connectionState) {
            ConnectionState.DISCONNECTED -> colorScheme.StatusRed
            ConnectionState.DEVICE_SLEEP -> colorScheme.StatusYellow
            else -> colorScheme.StatusGreen
        }

    val (backgroundIcon, connectionTypeIcon) =
        when (connectionState) {
            ConnectionState.DISCONNECTED -> MeshtasticIcons.NoDevice to null
            ConnectionState.DEVICE_SLEEP -> MeshtasticIcons.Device to Icons.Rounded.Snooze
            else ->
                MeshtasticIcons.Device to
                    when (deviceType) {
                        DeviceType.BLE -> Icons.Rounded.Bluetooth
                        DeviceType.TCP -> Icons.Rounded.Wifi
                        DeviceType.USB -> Icons.Rounded.Usb
                        else -> null
                    }
        }

    val foregroundPainter = connectionTypeIcon?.let { rememberVectorPainter(it) }

    Icon(
        imageVector = backgroundIcon,
        contentDescription = null,
        tint = tint,
        modifier =
        modifier.drawWithContent {
            drawContent()
            foregroundPainter?.let {
                @Suppress("MagicNumber")
                val badgeSize = size.width * .45f
                with(it) { draw(Size(badgeSize, badgeSize), colorFilter = ColorFilter.tint(tint)) }
            }
        },
    )
}

class ConnectionStateProvider : PreviewParameterProvider<ConnectionState> {
    override val values: Sequence<ConnectionState> =
        sequenceOf(ConnectionState.CONNECTED, ConnectionState.DEVICE_SLEEP, ConnectionState.DISCONNECTED)
}

class DeviceTypeProvider : PreviewParameterProvider<DeviceType> {
    override val values: Sequence<DeviceType> = sequenceOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)
}

@PreviewLightDark
@Composable
private fun ConnectionsNavIconPreviewConnectionStates(
    @PreviewParameter(ConnectionStateProvider::class) connectionState: ConnectionState,
) {
    AppTheme { ConnectionsNavIcon(connectionState = connectionState, deviceType = DeviceType.BLE) }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionsNavIconPreviewDeviceTypes(@PreviewParameter(DeviceTypeProvider::class) deviceType: DeviceType) {
    ConnectionsNavIcon(connectionState = ConnectionState.CONNECTED, deviceType = deviceType)
}
