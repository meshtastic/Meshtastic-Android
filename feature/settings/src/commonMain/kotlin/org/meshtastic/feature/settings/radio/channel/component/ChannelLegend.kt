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
package org.meshtastic.feature.settings.radio.channel.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_features
import org.meshtastic.core.resources.downlink_enabled
import org.meshtastic.core.resources.downlink_feature_description
import org.meshtastic.core.resources.ic_cloud_download
import org.meshtastic.core.resources.ic_cloud_upload
import org.meshtastic.core.resources.ic_location_on
import org.meshtastic.core.resources.icon_meanings
import org.meshtastic.core.resources.info
import org.meshtastic.core.resources.location_sharing
import org.meshtastic.core.resources.manual_position_request
import org.meshtastic.core.resources.periodic_position_broadcast
import org.meshtastic.core.resources.primary
import org.meshtastic.core.resources.primary_channel_feature
import org.meshtastic.core.resources.secondary
import org.meshtastic.core.resources.secondary_channel_position_feature
import org.meshtastic.core.resources.secondary_no_telemetry
import org.meshtastic.core.resources.security_icon_help_dismiss
import org.meshtastic.core.resources.uplink_enabled
import org.meshtastic.core.resources.uplink_feature_description
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons

@Composable
internal fun ChannelLegend(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick.invoke() },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row {
            Icon(imageVector = MeshtasticIcons.Info, contentDescription = stringResource(Res.string.info))
            Text(
                text = stringResource(Res.string.primary),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Text(
            text = stringResource(Res.string.secondary),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

internal enum class ChannelIcons(
    val icon: DrawableResource,
    val descriptionResId: StringResource,
    val additionalInfoResId: StringResource,
) {
    LOCATION(
        icon = Res.drawable.ic_location_on,
        descriptionResId = Res.string.location_sharing,
        additionalInfoResId = Res.string.periodic_position_broadcast,
    ),
    UPLINK(
        icon = Res.drawable.ic_cloud_upload,
        descriptionResId = Res.string.uplink_enabled,
        additionalInfoResId = Res.string.uplink_feature_description,
    ),
    DOWNLINK(
        icon = Res.drawable.ic_cloud_download,
        descriptionResId = Res.string.downlink_enabled,
        additionalInfoResId = Res.string.downlink_feature_description,
    ),
}

@Composable
internal fun ChannelLegendDialog(capabilities: Capabilities, onDismiss: () -> Unit) {
    MeshtasticDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.channel_features),
        dismissText = stringResource(Res.string.security_icon_help_dismiss),
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(Res.string.primary),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "- ${stringResource(Res.string.primary_channel_feature)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.secondary),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "- ${stringResource(Res.string.secondary_no_telemetry)}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                    if (capabilities.supportsSecondaryChannelLocation) {
                        /* 2.6.10+ */
                        "- ${stringResource(Res.string.secondary_channel_position_feature)}"
                    } else {
                        "- ${stringResource(Res.string.manual_position_request)}"
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconDefinitions()
            }
        },
    )
}

@Composable
private fun IconDefinitions() {
    Text(text = stringResource(Res.string.icon_meanings), style = MaterialTheme.typography.titleLarge)
    ChannelIcons.entries.forEach { icon ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = vectorResource(icon.icon), contentDescription = stringResource(icon.descriptionResId))
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = stringResource(icon.descriptionResId), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(icon.additionalInfoResId), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (icon != ChannelIcons.entries.lastOrNull()) {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }
    }
}
