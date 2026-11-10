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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.proto.Config
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.User
import kotlin.time.Duration.Companion.seconds

private const val RSSI_DELAY = 3
private const val RSSI_TIMEOUT = 1

/**
 * Plain text resolved outside the animated connected-device subtree.
 *
 * TODO(CMP-6615): Remove this bundle and the caller-provided component labels after upgrading from Compose
 *   Multiplatform 1.11.1 to a release containing the Android resource-loading fix.
 */
@Immutable
data class CurrentlyConnectedText(
    val unknownLabel: String,
    val rssiLabel: String,
    val disconnectLabel: String,
    val firmwareVersion: String?,
    val loraSummary: String? = null,
)

/** Preset and region labels for the connected card; [region] is null while the region is unset. */
data class LoraSummary(val preset: String, val region: String?)

/** Null until the LoRa config is known. */
fun loraSummary(
    lora: Config.LoRaConfig?,
    presetLabel: String?,
    regionLabel: String?,
    customLabel: String,
): LoraSummary? {
    if (lora == null) return null
    val preset = if (lora.use_preset) presetLabel ?: lora.modem_preset.name else customLabel
    val region = if (lora.region == Config.LoRaConfig.RegionCode.UNSET) null else regionLabel ?: lora.region.name
    return LoraSummary(preset, region)
}

@Suppress("LoopWithTooManyJumpStatements", "TooGenericExceptionCaught")
@Composable
fun CurrentlyConnectedInfo(
    node: Node,
    text: CurrentlyConnectedText,
    onNavigateToNodeDetails: (Int) -> Unit,
    onClickDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    bleDevice: DeviceListEntry.Ble? = null,
    onClickLora: () -> Unit = {},
) {
    // Null until the first successful read: 0 dBm is the strongest value on this scale, not "unknown".
    var rssi by remember(bleDevice?.device?.address) { mutableStateOf<Int?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(bleDevice, lifecycle) {
        if (bleDevice == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            bleDevice.device.state.collectLatest { state ->
                if (state != BleConnectionState.Connected) {
                    if (rssi != null) rssi = null
                    return@collectLatest
                }
                while (true) {
                    try {
                        val latestRssi = withTimeout(RSSI_TIMEOUT.seconds) { bleDevice.device.readRssi() }
                        if (latestRssi != rssi) rssi = latestRssi
                    } catch (_: TimeoutCancellationException) {
                        Logger.d { "RSSI read timed out" }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.d(e) { "Failed to read RSSI" }
                    }
                    delay(RSSI_DELAY.seconds)
                }
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialBatteryInfo(level = node.batteryLevel, voltage = node.voltage, unknownLabel = text.unknownLabel)
            if (bleDevice != null) {
                Rssi(rssi = rssi, label = text.rssiLabel)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NodeChip(node = node, onClick = { onNavigateToNodeDetails(it.num) })

            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text(text = node.effectiveLongName, style = MaterialTheme.typography.titleMediumEmphasized)

                text.firmwareVersion?.let { firmwareVersion ->
                    Text(
                        text = firmwareVersion,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        text.loraSummary?.let { LoraRow(summary = it, onClick = onClickLora) }

        DisconnectButton(onClick = onClickDisconnect, label = text.disconnectLabel)
    }
}

@Composable
private fun LoraRow(summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(imageVector = MeshtasticIcons.ChevronRight, contentDescription = null)
    }
}

@Suppress("MagicNumber", "UnusedPrivateFunction")
@Composable
private fun CurrentlyConnectedInfoPreview() {
    AppTheme {
        CurrentlyConnectedInfo(
            node =
            Node(
                num = 13444,
                user =
                User.Builder()
                    .also { wb ->
                        wb.short_name = "\uD83E\uDEE0"
                        wb.long_name = "John Doe"
                    }
                    .build(),
                isIgnored = false,
                paxcounter =
                Paxcount.Builder()
                    .also { wb ->
                        wb.ble = 10
                        wb.wifi = 5
                    }
                    .build(),
                environmentMetrics =
                EnvironmentMetrics.Builder()
                    .also { wb ->
                        wb.temperature = 25f
                        wb.relative_humidity = 60f
                    }
                    .build(),
            ),
            text =
            CurrentlyConnectedText(
                unknownLabel = stringResource(Res.string.unknown),
                rssiLabel = stringResource(Res.string.rssi),
                disconnectLabel = stringResource(Res.string.disconnect),
                firmwareVersion = null,
            ),
            onNavigateToNodeDetails = {},
            onClickDisconnect = {},
        )
    }
}
