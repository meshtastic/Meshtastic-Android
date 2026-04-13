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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.firmware_version
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.User
import kotlin.time.Duration.Companion.seconds

private const val RSSI_DELAY = 2
private const val RSSI_TIMEOUT = 1

@Suppress("LoopWithTooManyJumpStatements", "TooGenericExceptionCaught")
@Composable
fun CurrentlyConnectedInfo(
    node: Node,
    onNavigateToNodeDetails: (Int) -> Unit,
    onClickDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    bleDevice: DeviceListEntry.Ble? = null,
) {
    var rssi by remember { mutableIntStateOf(0) }
    LaunchedEffect(bleDevice) {
        if (bleDevice == null) return@LaunchedEffect
        while (bleDevice.device.isConnected) {
            try {
                rssi = withTimeout(RSSI_TIMEOUT.seconds) { bleDevice.device.readRssi() }
            } catch (_: TimeoutCancellationException) {
                Logger.d { "RSSI read timed out" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.d(e) { "Failed to read RSSI ${e.message}" }
            }
            delay(RSSI_DELAY.seconds)
        }
    }
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialBatteryInfo(level = node.batteryLevel, voltage = node.voltage)
            if (bleDevice != null) {
                Rssi(rssi = rssi)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NodeChip(node = node, onClick = { onNavigateToNodeDetails(it.num) })

            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text(text = node.user.long_name, style = MaterialTheme.typography.titleMedium)

                node.metadata
                    ?.firmware_version
                    ?.takeIf { it.isNotBlank() }
                    ?.let { firmwareVersion ->
                        Text(
                            text = stringResource(Res.string.firmware_version, firmwareVersion),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        }

        DisconnectButton(onClick = onClickDisconnect)
    }
}

@Suppress("MagicNumber", "UnusedPrivateMember")
@Composable
private fun CurrentlyConnectedInfoPreview() {
    AppTheme {
        CurrentlyConnectedInfo(
            node =
            Node(
                num = 13444,
                user = User(short_name = "\uD83E\uDEE0", long_name = "John Doe"),
                isIgnored = false,
                paxcounter = Paxcount(ble = 10, wifi = 5),
                environmentMetrics = EnvironmentMetrics(temperature = 25f, relative_humidity = 60f),
            ),
            onNavigateToNodeDetails = {},
            onClickDisconnect = {},
        )
    }
}
