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

package com.geeksville.mesh.ui.connections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.PaxcountProtos
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.core.strings.R as Res

/** Converts Bluetooth RSSI to a 0-4 bar signal strength level. */
@Composable
fun CurrentlyConnectedInfo(
    node: Node,
    onNavigateToNodeDetails: (Int) -> Unit,
    onClickDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialBatteryInfo(level = node.batteryLevel, voltage = node.voltage)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NodeChip(node = node, onClick = { onNavigateToNodeDetails(it.num) })
            }

            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text(text = node.user.longName, style = MaterialTheme.typography.titleMedium)

                node.metadata?.firmwareVersion?.let { firmwareVersion ->
                    Text(
                        text = stringResource(Res.string.firmware_version, firmwareVersion),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Button(
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.StatusRed,
                contentColor = Color.White,
            ),
            onClick = onClickDisconnect,
        ) {
            Text(stringResource(Res.string.disconnect))
        }
    }
}

@Suppress("MagicNumber")
@PreviewLightDark
@Composable
private fun CurrentlyConnectedInfoPreview() {
    AppTheme {
        CurrentlyConnectedInfo(
            node =
            Node(
                num = 13444,
                user = MeshProtos.User.newBuilder().setShortName("\uD83E\uDEE0").setLongName("John Doe").build(),
                isIgnored = false,
                paxcounter = PaxcountProtos.Paxcount.newBuilder().setBle(10).setWifi(5).build(),
                environmentMetrics =
                TelemetryProtos.EnvironmentMetrics.newBuilder()
                    .setTemperature(25f)
                    .setRelativeHumidity(60f)
                    .build(),
            ),
            onNavigateToNodeDetails = {},
            onClickDisconnect = {},
        )
    }
}
