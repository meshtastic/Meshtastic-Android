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

package com.geeksville.mesh.ui.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.model.getChannel

private const val PRECISE_POSITION_BITS = 32

/**
 * Returns the appropriate security icon and color based on the channel's security settings.
 *
 * @param isLowEntropyKey Whether the channel uses a low entropy key (0 or 1 byte PSK)
 * @param isPreciseLocation Whether the channel has precise location enabled (32 bits)
 * @return A pair of (ImageVector, Color) representing the security icon and its color
 */
@Composable
fun getSecurityIcon(
    isLowEntropyKey: Boolean,
    isPreciseLocation: Boolean = false
): Pair<ImageVector, Color> {
    return when {
        !isLowEntropyKey -> {
            // Secure channel - green lock
            Icons.Default.Lock to Color.Green
        }
        isPreciseLocation -> {
            // Insecure channel with precise location - red unlocked icon
            ImageVector.vectorResource(R.drawable.ic_lock_open_right_24) to Color.Red
        }
        else -> {
            // Insecure channel without precise location - yellow unlocked icon
            ImageVector.vectorResource(R.drawable.ic_lock_open_right_24) to Color.Yellow
        }
    }
}

/**
 * Calculates whether a channel uses a low entropy key (0 or 1 byte PSK).
 *
 * @param channel The channel to check
 * @return true if the channel uses a low entropy key, false otherwise
 */
fun Channel.isLowEntropyKey(): Boolean = settings.psk.size() <= 1

/**
 * Calculates whether a channel has precise location enabled (32 bits).
 *
 * @param channel The channel to check
 * @return true if the channel has precise location enabled, false otherwise
 */
fun Channel.isPreciseLocation(): Boolean = settings.getModuleSettings().positionPrecision == PRECISE_POSITION_BITS

/**
 * Calculates whether a channel uses a low entropy key (0 or 1 byte PSK).
 *
 * @param channelSettings The channel settings to check
 * @return true if the channel uses a low entropy key, false otherwise
 */
fun com.geeksville.mesh.ChannelProtos.ChannelSettings.isLowEntropyKey(): Boolean = psk.size() <= 1

/**
 * Calculates whether a channel has precise location enabled (32 bits).
 *
 * @param channelSettings The channel settings to check
 * @return true if the channel has precise location enabled, false otherwise
 */
fun com.geeksville.mesh.ChannelProtos.ChannelSettings.isPreciseLocation(): Boolean =
    getModuleSettings().positionPrecision == PRECISE_POSITION_BITS

/**
 * Gets the security icon for a channel.
 *
 * @param channel The channel to get the security icon for
 * @return A pair of (ImageVector, Color) representing the security icon and its color
 */
@Composable
fun getSecurityIcon(channel: Channel): Pair<ImageVector, Color> =
    getSecurityIcon(channel.isLowEntropyKey(), channel.isPreciseLocation())

/**
 * Gets the security icon for channel settings.
 *
 * @param channelSettings The channel settings to get the security icon for
 * @return A pair of (ImageVector, Color) representing the security icon and its color
 */
@Composable
fun getSecurityIcon(channelSettings: com.geeksville.mesh.ChannelProtos.ChannelSettings): Pair<ImageVector, Color> =
    getSecurityIcon(channelSettings.isLowEntropyKey(), channelSettings.isPreciseLocation())

/**
 * Gets the security icon for a channel by index from a channel set.
 *
 * @param channelSet The channel set containing the channel
 * @param channelIndex The index of the channel in the channel set
 * @return A pair of (ImageVector, Color) representing the security icon and its color, or null if channel not found
 */
@Composable
fun getSecurityIcon(channelSet: AppOnlyProtos.ChannelSet, channelIndex: Int): Pair<ImageVector, Color>? {
    val channel = channelSet.getChannel(channelIndex) ?: return null
    return getSecurityIcon(channel)
}

/**
 * Gets the security icon for a channel by name from a channel set.
 *
 * @param channelSet The channel set containing the channel
 * @param channelName The name of the channel to find
 * @return A pair of (ImageVector, Color) representing the security icon and its color, or null if channel not found
 */
@Composable
fun getSecurityIcon(channelSet: AppOnlyProtos.ChannelSet, channelName: String): Pair<ImageVector, Color>? {
    val channel = channelSet.settingsList.find { Channel(it, channelSet.loraConfig).name == channelName }
        ?.let { Channel(it, channelSet.loraConfig) } ?: return null
    return getSecurityIcon(channel)
}
