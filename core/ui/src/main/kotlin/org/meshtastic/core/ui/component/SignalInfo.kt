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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.air_utilization
import org.meshtastic.core.strings.channel_utilization
import org.meshtastic.core.strings.signal
import org.meshtastic.core.strings.signal_quality
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.icon.AirUtilization
import org.meshtastic.core.ui.icon.ChannelUtilization
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme

const val MAX_VALID_SNR = 100F
const val MAX_VALID_RSSI = 0

@Suppress("LongMethod")
@Composable
fun SignalInfo(
    modifier: Modifier = Modifier,
    node: Node,
    isThisNode: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isThisNode) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconInfo(
                    icon = MeshtasticIcons.ChannelUtilization,
                    contentDescription = stringResource(Res.string.channel_utilization),
                    text = "%.1f%%".format(node.deviceMetrics.channelUtilization),
                    contentColor = contentColor,
                )
                IconInfo(
                    icon = MeshtasticIcons.AirUtilization,
                    contentDescription = stringResource(Res.string.air_utilization),
                    text = "%.1f%%".format(node.deviceMetrics.airUtilTx),
                    contentColor = contentColor,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (node.channel > 0) {
                    ChannelInfo(channel = node.channel, contentColor = contentColor)
                }
                if (node.hopsAway > 0) {
                    HopsInfo(hops = node.hopsAway, contentColor = contentColor)
                }
            }

            /* We only know the Signal Quality from direct nodes aka 0 hop. */
            if (node.hopsAway <= 0) {
                if (node.snr < MAX_VALID_SNR && node.rssi < MAX_VALID_RSSI) {
                    val quality = determineSignalQuality(node.snr, node.rssi)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Snr(node.snr)
                        Rssi(node.rssi)
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = quality.imageVector,
                            contentDescription = stringResource(Res.string.signal_quality),
                            tint = quality.color.invoke(),
                        )
                        Text(
                            text = "${stringResource(Res.string.signal)} ${stringResource(quality.nameRes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun SignalInfoSimplePreview() {
    AppTheme {
        SignalInfo(
            node = Node(num = 1, lastHeard = 0, channel = 0, snr = 12.5F, rssi = -42, hopsAway = 0),
            isThisNode = false,
        )
    }
}

@PreviewLightDark
@Composable
fun SignalInfoPreview(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
    AppTheme { SignalInfo(node = node, isThisNode = false) }
}

@Composable
@PreviewLightDark
fun SignalInfoSelfPreview(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
    AppTheme { SignalInfo(node = node, isThisNode = true) }
}
