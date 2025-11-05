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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.strings.R as Res

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
    val text =
        if (isThisNode) {
            stringResource(Res.string.channel_air_util)
                .format(node.deviceMetrics.channelUtilization, node.deviceMetrics.airUtilTx)
        } else {
            buildList {
                val hopsString =
                    "%s: %s"
                        .format(
                            stringResource(Res.string.hops_away),
                            if (node.hopsAway == -1) {
                                "?"
                            } else {
                                node.hopsAway.toString()
                            },
                        )
                if (node.channel > 0) {
                    add("ch:${node.channel}")
                }
                if (node.hopsAway != 0) add(hopsString)
            }
                .joinToString(" ")
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (text.isNotEmpty()) {
            Text(text = text, color = contentColor, style = MaterialTheme.typography.labelSmall)
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
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
