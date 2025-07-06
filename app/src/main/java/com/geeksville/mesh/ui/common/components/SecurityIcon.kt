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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.model.getChannel
@Suppress("TooManyFunctions") // lots of overloads

private const val PRECISE_POSITION_BITS = 32
/**
 * Returns the appropriate security icon composable based on the channel's security settings.
 *
 * @param isLowEntropyKey Whether the channel uses a low entropy key (0 or 1 byte PSK)
 * @param isPreciseLocation Whether the channel has precise location enabled (32 bits)
 * @param isMqttEnabled Whether MQTT is enabled (adds warning icon)
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint
 */
@Composable
fun SecurityIcon(
    isLowEntropyKey: Boolean,
    isPreciseLocation: Boolean = false,
    isMqttEnabled: Boolean = false,
    contentDescription: String = "Security status"
) {
    val (icon, color) = when {
        !isLowEntropyKey -> {
            // Secure channel - green lock
            Icons.Default.Lock to Color.Green
        }
        isPreciseLocation && isMqttEnabled -> {
            // MQTT enabled - warning icon in red
            Icons.Default.Warning to Color.Red
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

    androidx.compose.material3.Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = color
    )
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
fun Channel.isPreciseLocation(): Boolean =
    settings.getModuleSettings().positionPrecision == PRECISE_POSITION_BITS

/**
 * Calculates whether a channel uses a low entropy key (0 or 1 byte PSK).
 *
 * @param channelSettings The channel settings to check
 * @return true if the channel uses a low entropy key, false otherwise
 */
fun com.geeksville.mesh.ChannelProtos.ChannelSettings.isLowEntropyKey(): Boolean =
    psk.size() <= 1

/**
 * Calculates whether a channel has precise location enabled (32 bits).
 *
 * @param channelSettings The channel settings to check
 * @return true if the channel has precise location enabled, false otherwise
 */
fun com.geeksville.mesh.ChannelProtos.ChannelSettings.isPreciseLocation(): Boolean =
    getModuleSettings().positionPrecision == PRECISE_POSITION_BITS

/**
 * Calculates whether MQTT is effectively enabled for a channel.
 * This checks if the channel has uplink enabled, which means
 * messages from this channel will be sent to MQTT by the wider network.
 *
 * @param channel The channel to check
 * @return true if the channel uses uplink, false otherwise
 */
fun Channel.isMqttEnabled(): Boolean = settings.uplinkEnabled

/**
 * Calculates whether MQTT is effectively enabled for channel settings.
 * This checks if the channel has uplink enabled, which means
 * messages from this channel will be sent to MQTT by the wider network.
 *
 * @param channelSettings The channel settings to check
 * @return true if the channel uses uplink, false otherwise
 */
fun com.geeksville.mesh.ChannelProtos.ChannelSettings.isMqttEnabled(): Boolean = uplinkEnabled

/**
 * Gets the security icon for a channel.
 *
 * @param channel The channel to get the security icon for
 * @param isMqttEnabled Whether MQTT is enabled
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint
 */
@Composable
fun SecurityIcon(
    channel: Channel,
    isMqttEnabled: Boolean = false,
    contentDescription: String = "Security status"
) = SecurityIcon(
    channel.isLowEntropyKey(),
    channel.isPreciseLocation(),
    isMqttEnabled,
    contentDescription
)

/**
 * Gets the security icon for channel settings.
 *
 * @param channelSettings The channel settings to get the security icon for
 * @param isMqttEnabled Whether MQTT is enabled
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint
 */
@Composable
fun SecurityIcon(
    channelSettings: com.geeksville.mesh.ChannelProtos.ChannelSettings,
    isMqttEnabled: Boolean = false,
    contentDescription: String = "Security status"
) = SecurityIcon(
    channelSettings.isLowEntropyKey(),
    channelSettings.isPreciseLocation(),
    isMqttEnabled,
    contentDescription
)

/**
 * Gets the security icon for a channel by index from a channel set.
 *
 * @param channelSet The channel set containing the channel
 * @param channelIndex The index of the channel in the channel set
 * @param isMqttEnabled Whether MQTT is enabled
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint, or null if channel not found
 */
@Composable
fun SecurityIcon(
    channelSet: AppOnlyProtos.ChannelSet,
    channelIndex: Int,
    isMqttEnabled: Boolean = false,
    contentDescription: String = "Security status"
) {
    val channel = channelSet.getChannel(channelIndex) ?: return
    SecurityIcon(channel, isMqttEnabled, contentDescription)
}

/**
 * Gets the security icon for a channel by name from a channel set.
 *
 * @param channelSet The channel set containing the channel
 * @param channelName The name of the channel to find
 * @param isMqttEnabled Whether MQTT is enabled
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint, or null if channel not found
 */
@Composable
fun SecurityIcon(
    channelSet: AppOnlyProtos.ChannelSet,
    channelName: String,
    isMqttEnabled: Boolean = false,
    contentDescription: String = "Security status"
) {
    val channel = channelSet.settingsList.find {
        Channel(it, channelSet.loraConfig).name == channelName
    }?.let { Channel(it, channelSet.loraConfig) } ?: return
    SecurityIcon(channel, isMqttEnabled, contentDescription)
}

/**
 * Gets the security icon for a channel by index from a channel set.
 * This automatically determines if MQTT is enabled for this specific channel.
 *
 * @param channelSet The channel set containing the channel
 * @param channelIndex The index of the channel in the channel set
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint, or null if channel not found
 */
@Composable
fun SecurityIcon(
    channelSet: AppOnlyProtos.ChannelSet,
    channelIndex: Int,
    contentDescription: String = "Security status"
) {
    val channel = channelSet.getChannel(channelIndex) ?: return
    SecurityIcon(channel, channel.isMqttEnabled(), contentDescription)
}

/**
 * Gets the security icon for a channel by name from a channel set.
 * This automatically determines if MQTT is enabled for this specific channel.
 *
 * @param channelSet The channel set containing the channel
 * @param channelName The name of the channel to find
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint, or null if channel not found
 */
@Composable
fun SecurityIcon(
    channelSet: AppOnlyProtos.ChannelSet,
    channelName: String,
    contentDescription: String = "Security status"
) {
    val channel = channelSet.settingsList.find {
        Channel(it, channelSet.loraConfig).name == channelName
    }?.let { Channel(it, channelSet.loraConfig) } ?: return
    SecurityIcon(channel, channel.isMqttEnabled(), contentDescription)
}

/**
 * Gets the security icon for a channel.
 * This automatically determines if MQTT is enabled for this specific channel.
 *
 * @param channel The channel to get the security icon for
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint
 */
@Composable
fun SecurityIcon(
    channel: Channel,
    contentDescription: String = "Security status"
) {
    SecurityIcon(channel, channel.isMqttEnabled(), contentDescription)
}

/**
 * Gets the security icon for channel settings.
 * This automatically determines if MQTT is enabled for this specific channel.
 *
 * @param channelSettings The channel settings to get the security icon for
 * @param contentDescription The content description for the icon
 * @return A composable Icon element with appropriate imageVector and tint
 */
@Composable
fun SecurityIcon(
    channelSettings: com.geeksville.mesh.ChannelProtos.ChannelSettings,
    contentDescription: String = "Security status"
) {
    SecurityIcon(channelSettings, channelSettings.isMqttEnabled(), contentDescription)
}

// Preview functions for development and testing
@Preview(name = "Secure Channel - Green Lock")
@Composable
private fun PreviewSecureChannel() {
    SecurityIcon(
        isLowEntropyKey = false,
        isPreciseLocation = false,
        isMqttEnabled = false
    )
}

@Preview(name = "Insecure Channel with Precise Location - Red Unlock")
@Composable
private fun PreviewInsecureChannelWithPreciseLocation() {
    SecurityIcon(
        isLowEntropyKey = true,
        isPreciseLocation = true,
        isMqttEnabled = false
    )
}

@Preview(name = "Insecure Channel without Precise Location - Yellow Unlock")
@Composable
private fun PreviewInsecureChannelWithoutPreciseLocation() {
    SecurityIcon(
        isLowEntropyKey = true,
        isPreciseLocation = false,
        isMqttEnabled = false
    )
}

@Preview(name = "MQTT Enabled - Red Warning")
@Composable
private fun PreviewMqttEnabled() {
    SecurityIcon(
        isLowEntropyKey = false,
        isPreciseLocation = false,
        isMqttEnabled = true
    )
}

@Preview(name = "All Security Icons")
@Composable
private fun PreviewAllSecurityIcons() {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.Text(
            "Security Icons Preview",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = false,
                isPreciseLocation = false,
                isMqttEnabled = false
            )
            androidx.compose.material3.Text("Secure")
        }

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = true,
                isPreciseLocation = true,
                isMqttEnabled = false
            )
            androidx.compose.material3.Text("Insecure + Precise Location")
        }

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = true,
                isPreciseLocation = false,
                isMqttEnabled = false
            )
            androidx.compose.material3.Text("Insecure")
        }

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = false,
                isPreciseLocation = false,
                isMqttEnabled = true
            )
            androidx.compose.material3.Text("MQTT Enabled")
        }
    }
}
