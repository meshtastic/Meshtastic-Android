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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.LocalModemPreset

/**
 * Renders the node's signal quality, or nothing when it has no SNR reading to rate.
 *
 * Presence comes from [Node.snrOrNull]/[Node.rssiOrNull], not from threshold comparisons: the previous `rssi < 0` gate
 * hid the whole row for a genuine 0 dBm reading, and `snr < 100f` would have hidden any reading at or above 100 dB.
 */
@Composable
fun SignalInfo(
    modifier: Modifier = Modifier,
    node: Node,
    @Suppress("UNUSED_PARAMETER") contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val snr = node.snrOrNull
    if (snr != null) {
        val quality = determineSignalQuality(snr, LocalModemPreset.current)
        val signalColor = quality.color.invoke()
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = vectorResource(quality.icon),
                contentDescription = stringResource(Res.string.signal_quality),
                modifier = Modifier.size(16.dp),
                tint = signalColor,
            )
            Text(
                text =
                "${MetricFormatter.snr(snr)} · ${MetricFormatter.rssi(node.rssiOrNull)} · " +
                    stringResource(quality.nameRes),
                style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                ),
                color = signalColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun SignalInfoSimplePreview() {
    AppTheme { SignalInfo(node = Node(num = 1, lastHeard = 0, channel = 0, snr = 12.5F, rssi = -42, hopsAway = 0)) }
}

@PreviewLightDark
@Composable
fun SignalInfoPreview(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
    AppTheme { SignalInfo(node = node) }
}
