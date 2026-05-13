/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.Device
import org.meshtastic.core.ui.icon.DeviceSleep
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.icon.Reconnecting
import org.meshtastic.core.ui.icon.Usb
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

@Composable
fun ConnectionsNavIcon(
    modifier: Modifier = Modifier,
    connectionState: ConnectionState,
    deviceType: DeviceType?,
    contentDescription: String? = null,
) {
    val tint = getTint(connectionState)

    val (backgroundIcon, connectionTypeIcon) = getIconPair(deviceType = deviceType, connectionState = connectionState)

    val foregroundPainter = connectionTypeIcon?.let { rememberVectorPainter(it) }

    Crossfade(targetState = backgroundIcon, label = "ConnectionIcon") {
        Icon(
            imageVector = it,
            contentDescription = contentDescription,
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
}

@Composable
private fun getTint(connectionState: ConnectionState): Color = when (connectionState) {
    ConnectionState.Connecting -> colorScheme.StatusOrange
    ConnectionState.Disconnected -> colorScheme.StatusRed
    ConnectionState.DeviceSleep -> colorScheme.StatusYellow
    else -> colorScheme.StatusGreen
}

@Composable
fun getIconPair(connectionState: ConnectionState, deviceType: DeviceType? = null): Pair<ImageVector, ImageVector?> =
    when (connectionState) {
        ConnectionState.Disconnected -> MeshtasticIcons.NoDevice to null

        ConnectionState.DeviceSleep -> MeshtasticIcons.Device to MeshtasticIcons.DeviceSleep

        ConnectionState.Connecting -> MeshtasticIcons.Device to MeshtasticIcons.Reconnecting

        else ->
            MeshtasticIcons.Device to
                when (deviceType) {
                    DeviceType.BLE -> MeshtasticIcons.Bluetooth
                    DeviceType.TCP -> MeshtasticIcons.Wifi
                    DeviceType.USB -> MeshtasticIcons.Usb
                    else -> null
                }
    }
