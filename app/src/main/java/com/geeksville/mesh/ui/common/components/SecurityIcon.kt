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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.getChannel

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
    contentDescription: String = stringResource(id = R.string.security_icon_description)
) {
    val (icon, color, computedDescription) = when {
        !isLowEntropyKey -> {
            Triple(Icons.Default.Lock, Color.Green, stringResource(id = R.string.security_icon_secure))
        }
        isPreciseLocation && isMqttEnabled -> {
            Triple(Icons.Default.Warning, Color.Red, stringResource(id = R.string.security_icon_warning))
        }
        isPreciseLocation -> {
            Triple(ImageVector.vectorResource(R.drawable.ic_lock_open_right_24),
                Color.Red,
                stringResource(id = R.string.security_icon_insecure_precise))
        }
        else -> {
            Triple(ImageVector.vectorResource(R.drawable.ic_lock_open_right_24),
                Color.Yellow,
                stringResource(id = R.string.security_icon_insecure))
        }
    }

    Icon(
        imageVector = icon,
        contentDescription = contentDescription + computedDescription,
        tint = color
    )
}

fun Channel.isLowEntropyKey(): Boolean = settings.psk.size() <= 1
fun Channel.isPreciseLocation(): Boolean = settings.getModuleSettings().positionPrecision == PRECISE_POSITION_BITS
fun Channel.isMqttEnabled(): Boolean = settings.uplinkEnabled

@Composable
fun SecurityIcon(
    channel: Channel,
    contentDescription: String = stringResource(id = R.string.security_icon_description)
) = SecurityIcon(
    channel.isLowEntropyKey(),
    channel.isPreciseLocation(),
    channel.isMqttEnabled(),
    contentDescription
)

@Composable
fun SecurityIcon(
    channelSet: AppOnlyProtos.ChannelSet,
    channelIndex: Int,
    contentDescription: String = stringResource(id = R.string.security_icon_description)
) {
    val channel = channelSet.getChannel(channelIndex) ?: return
    SecurityIcon(channel, contentDescription)
}

@Composable
fun SecurityIcon(
    channelSet: AppOnlyProtos.ChannelSet,
    channelName: String,
    contentDescription: String = stringResource(id = R.string.security_icon_description)
) {
    val channel = channelSet.settingsList.find {
        Channel(it, channelSet.loraConfig).name == channelName
    }?.let { Channel(it, channelSet.loraConfig) } ?: return
    SecurityIcon(channel, contentDescription)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Security Icons Preview",
            style = MaterialTheme.typography.headlineSmall
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = false,
                isPreciseLocation = false,
                isMqttEnabled = false
            )
            Text("Secure")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = true,
                isPreciseLocation = true,
                isMqttEnabled = false
            )
            Text("Insecure + Precise Location")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = true,
                isPreciseLocation = false,
                isMqttEnabled = false
            )
            Text("Insecure")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecurityIcon(
                isLowEntropyKey = false,
                isPreciseLocation = false,
                isMqttEnabled = true
            )
            Text("MQTT Enabled")
        }
    }
}
