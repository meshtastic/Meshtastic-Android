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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.grant_permission
import org.meshtastic.core.resources.node_bearer_active
import org.meshtastic.core.resources.node_bearer_audio
import org.meshtastic.core.resources.node_bearer_ble_adv
import org.meshtastic.core.resources.node_bearer_counters
import org.meshtastic.core.resources.node_bearer_gatt
import org.meshtastic.core.resources.node_bearer_lora
import org.meshtastic.core.resources.node_bearer_mqtt
import org.meshtastic.core.resources.node_bearer_needs_hardware
import org.meshtastic.core.resources.node_bearer_needs_permission
import org.meshtastic.core.resources.node_bearer_needs_usb
import org.meshtastic.core.resources.node_bearer_off
import org.meshtastic.core.resources.node_bearer_ready
import org.meshtastic.core.resources.node_bearer_udp
import org.meshtastic.core.resources.node_bearer_unsupported
import org.meshtastic.core.resources.node_bearer_video
import org.meshtastic.core.resources.node_bearer_wifi_aware
import org.meshtastic.core.resources.node_status_peers
import org.meshtastic.core.resources.node_status_phase_failed
import org.meshtastic.core.resources.node_status_phase_rebooting
import org.meshtastic.core.resources.node_status_phase_running
import org.meshtastic.core.resources.node_status_phase_starting
import org.meshtastic.core.resources.node_status_phase_stopped
import org.meshtastic.core.resources.node_status_settings_reset
import org.meshtastic.core.resources.node_status_title
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.icon.Antenna
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.BluetoothSearching
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MqttConnected
import org.meshtastic.core.ui.icon.QrCodeScanner
import org.meshtastic.core.ui.icon.Speaker
import org.meshtastic.core.ui.icon.Udp
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberPermissionState
import org.meshtastic.feature.connections.model.BearerRowState
import org.meshtastic.feature.connections.model.BearerRowUi
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.node.host.Bearer
import org.meshtastic.node.host.NodePhase

/**
 * This device as the node: its phase, identity and one row per bearer. Every bearer but LoRa carries a switch; one
 * driven by the Network settings writes them, so both places agree. A bearer waiting on a permission offers the grant.
 */
@Composable
fun NodeStatusCard(
    status: NodeStatusUi,
    onSetBearer: (Bearer, Boolean) -> Unit,
    onRefreshRequirements: () -> Unit,
    onRequestUsbPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            NodeStatusHeader(status = status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            status.bearers.forEach { row ->
                BearerRow(
                    row = row,
                    onSetBearer = onSetBearer,
                    onRefreshRequirements = onRefreshRequirements,
                    onRequestUsbPermission = onRequestUsbPermission,
                )
            }
        }
    }
}

@Composable
private fun NodeStatusHeader(status: NodeStatusUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(Res.string.node_status_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(status.phase.label()),
                style = MaterialTheme.typography.labelLarge,
                color =
                when (status.phase) {
                    NodePhase.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            status.nodeId?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            Text(
                text = pluralStringResource(Res.plurals.node_status_peers, status.peerCount, status.peerCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        status.failure?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (status.settingsReset) {
            Text(
                text = stringResource(Res.string.node_status_settings_reset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BearerRow(
    row: BearerRowUi,
    onSetBearer: (Bearer, Boolean) -> Unit,
    onRefreshRequirements: () -> Unit,
    onRequestUsbPermission: () -> Unit,
) {
    val state = row.state
    BasicListItem(
        text = stringResource(row.bearer.label()),
        supportingText = row.supportingText(),
        leadingIcon = row.bearer.icon(),
        trailingContent =
        when {
            state is BearerRowState.NeedsPermission -> {
                { GrantPermissionButton(permission = state.permission, onGrant = onRefreshRequirements) }
            }

            state is BearerRowState.NeedsUsbPermission -> {
                {
                    TextButton(onClick = onRequestUsbPermission) {
                        Text(stringResource(Res.string.grant_permission))
                    }
                }
            }

            row.switchable -> {
                { Switch(checked = row.enabled, onCheckedChange = null) }
            }

            else -> null
        },
        onClick = if (row.switchable) ({ onSetBearer(row.bearer, !row.enabled) }) else null,
    )
}

/** Requests the permission the library named; once granted, the node re-reads its requirements. */
@Composable
private fun GrantPermissionButton(permission: String, onGrant: () -> Unit) {
    val permissionState = rememberPermissionState(permission)
    val currentOnGrant by rememberUpdatedState(onGrant)
    // Once per status change, not per recomposition: the node re-reads its requirements after the grant.
    DisposableEffect(permissionState.status) {
        if (permissionState.status == PermissionStatus.GRANTED) currentOnGrant()
        onDispose {}
    }
    TextButton(
        onClick = {
            if (permissionState.status == PermissionStatus.PERMANENTLY_DENIED) {
                permissionState.openAppSettings()
            } else {
                permissionState.request()
            }
        },
    ) {
        Text(stringResource(Res.string.grant_permission))
    }
}

@Composable
private fun BearerRowUi.supportingText(): String {
    val stateText =
        when (val s = state) {
            BearerRowState.Off -> stringResource(Res.string.node_bearer_off)
            BearerRowState.Ready -> stringResource(Res.string.node_bearer_ready)
            BearerRowState.Active -> stringResource(Res.string.node_bearer_active)
            is BearerRowState.Unavailable -> s.reason
            is BearerRowState.NeedsPermission -> stringResource(Res.string.node_bearer_needs_permission)
            is BearerRowState.NeedsUsbPermission -> stringResource(Res.string.node_bearer_needs_usb, s.device)
            is BearerRowState.NeedsHardware -> stringResource(Res.string.node_bearer_needs_hardware, s.what)
            is BearerRowState.Unsupported -> stringResource(Res.string.node_bearer_unsupported, s.reason)
        }
    val counters = stringResource(Res.string.node_bearer_counters, received, sent, relayed)
    return if (enabled) "$stateText · $counters" else stateText
}

private fun NodePhase.label(): StringResource = when (this) {
    NodePhase.Starting -> Res.string.node_status_phase_starting
    NodePhase.Running -> Res.string.node_status_phase_running
    NodePhase.Rebooting -> Res.string.node_status_phase_rebooting
    NodePhase.Failed -> Res.string.node_status_phase_failed
    NodePhase.Stopped -> Res.string.node_status_phase_stopped
}

private fun Bearer.label(): StringResource = when (this) {
    Bearer.UDP -> Res.string.node_bearer_udp
    Bearer.BLE_ADV -> Res.string.node_bearer_ble_adv
    Bearer.GATT -> Res.string.node_bearer_gatt
    Bearer.LORA -> Res.string.node_bearer_lora
    Bearer.MQTT -> Res.string.node_bearer_mqtt
    Bearer.WIFI_AWARE -> Res.string.node_bearer_wifi_aware
    Bearer.AUDIO -> Res.string.node_bearer_audio
    Bearer.VIDEO -> Res.string.node_bearer_video
}

@Composable
private fun Bearer.icon(): ImageVector = when (this) {
    Bearer.UDP -> MeshtasticIcons.Udp
    Bearer.BLE_ADV -> MeshtasticIcons.BluetoothSearching
    Bearer.GATT -> MeshtasticIcons.Bluetooth
    Bearer.LORA -> MeshtasticIcons.Antenna
    Bearer.MQTT -> MeshtasticIcons.MqttConnected
    Bearer.WIFI_AWARE -> MeshtasticIcons.Wifi
    Bearer.AUDIO -> MeshtasticIcons.Speaker
    Bearer.VIDEO -> MeshtasticIcons.QrCodeScanner
}
