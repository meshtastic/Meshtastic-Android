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
package org.meshtastic.feature.settings.radio.channel.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.delete
import org.meshtastic.core.ui.component.ChannelItem
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

@Composable
internal fun ChannelCard(
    index: Int,
    title: String,
    enabled: Boolean,
    channelSettings: ChannelSettings,
    loraConfig: Config.LoRaConfig,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    sharesLocation: Boolean,
) = ChannelItem(index = index, title = title, enabled = enabled, onClick = onEditClick) {
    if (sharesLocation) {
        Icon(
            imageVector = ChannelIcons.LOCATION.icon,
            contentDescription = stringResource(ChannelIcons.LOCATION.descriptionResId),
            modifier = Modifier.wrapContentSize().padding(horizontal = 5.dp),
        )
    }
    if (channelSettings.uplink_enabled) {
        Icon(
            imageVector = ChannelIcons.UPLINK.icon,
            contentDescription = stringResource(ChannelIcons.UPLINK.descriptionResId),
            modifier = Modifier.wrapContentSize().padding(horizontal = 5.dp),
        )
    }
    if (channelSettings.downlink_enabled) {
        Icon(
            imageVector = ChannelIcons.DOWNLINK.icon,
            contentDescription = stringResource(ChannelIcons.DOWNLINK.descriptionResId),
            modifier = Modifier.wrapContentSize().padding(horizontal = 5.dp),
        )
    }
    SecurityIcon(channelSettings, loraConfig)
    Spacer(modifier = Modifier.width(10.dp))
    IconButton(onClick = { onDeleteClick() }) {
        Icon(
            imageVector = Icons.TwoTone.Close,
            contentDescription = stringResource(Res.string.delete),
            modifier = Modifier.wrapContentSize(),
        )
    }
}

@Preview
@Composable
private fun ChannelCardPreview() {
    AppTheme {
        ChannelCard(
            index = 0,
            title = "Medium Fast",
            enabled = true,
            channelSettings = ChannelSettings(uplink_enabled = true, downlink_enabled = true),
            loraConfig = Config.LoRaConfig(),
            onEditClick = {},
            onDeleteClick = {},
            sharesLocation = true,
        )
    }
}
