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

package com.geeksville.mesh.ui.settings.radio.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DeviceVersion

/**
 * At this firmware version periodic position sharing on a secondary channel was implemented. To enable this feature the
 * user must disable position on the primary channel and enable on a secondary channel. The lowest indexed secondary
 * channel with the position enabled will conduct the automatic position broadcasts.
 */
internal const val SECONDARY_CHANNEL_EPOCH = "2.6.10"

internal enum class ChannelIcons(
    val icon: ImageVector,
    @StringRes val descriptionResId: Int,
    @StringRes val additionalInfoResId: Int,
) {
    LOCATION(
        icon = Icons.Filled.LocationOn,
        descriptionResId = R.string.location_sharing,
        additionalInfoResId = R.string.periodic_position_broadcast,
    ),
    UPLINK(
        icon = Icons.Filled.CloudUpload,
        descriptionResId = R.string.uplink_enabled,
        additionalInfoResId = R.string.uplink_feature_description,
    ),
    DOWNLINK(
        icon = Icons.Filled.CloudDownload,
        descriptionResId = R.string.downlink_enabled,
        additionalInfoResId = R.string.downlink_feature_description,
    ),
}

@Composable
internal fun ChannelLegend(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick.invoke() },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row {
            Icon(imageVector = Icons.Filled.Info, contentDescription = stringResource(R.string.info))
            Text(
                text = stringResource(R.string.primary),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Text(
            text = stringResource(R.string.secondary),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
internal fun ChannelLegendDialog(firmwareVersion: DeviceVersion, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.fillMaxSize(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.channel_features)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.primary),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "- ${stringResource(R.string.primary_channel_feature)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.secondary),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "- ${stringResource(R.string.secondary_no_telemetry)}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                    if (firmwareVersion >= DeviceVersion(asString = SECONDARY_CHANNEL_EPOCH)) {
                        /* 2.6.10+ */
                        "- ${stringResource(R.string.secondary_channel_position_feature)}"
                    } else {
                        "- ${stringResource(R.string.manual_position_request)}"
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconDefinitions()
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.security_icon_help_dismiss)) }
            }
        },
    )
}

@Composable
private fun IconDefinitions() {
    Text(text = stringResource(R.string.icon_meanings), style = MaterialTheme.typography.titleLarge)
    ChannelIcons.entries.forEach { icon ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon.icon, contentDescription = stringResource(icon.descriptionResId))
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

@Preview
@Composable
private fun PreviewChannelLegendDialog() {
    ChannelLegendDialog(firmwareVersion = DeviceVersion(asString = SECONDARY_CHANNEL_EPOCH)) {}
}
