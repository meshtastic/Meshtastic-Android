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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connected_sleeping
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.must_set_region
import org.meshtastic.core.resources.node_restarting
import org.meshtastic.core.resources.not_connected
import org.meshtastic.core.resources.reconnecting
import org.meshtastic.core.resources.stop_connecting
import org.meshtastic.core.ui.viewmodel.ConnectionStatus

/**
 * Displays the currently connecting (or connected) device with its name, address, connection status, and a disconnect
 * button.
 */
@Composable
fun ConnectingDeviceInfo(
    deviceName: String,
    deviceAddress: String,
    connectionStatus: ConnectionStatus,
    connectionProgress: String?,
    onClickDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel =
        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> stringResource(Res.string.connected)
            ConnectionStatus.MUST_SET_REGION -> stringResource(Res.string.must_set_region)
            ConnectionStatus.CONNECTING -> connectionProgress ?: stringResource(Res.string.connecting)
            ConnectionStatus.RECONNECTING -> stringResource(Res.string.reconnecting)
            ConnectionStatus.RESTARTING -> stringResource(Res.string.node_restarting)
            ConnectionStatus.CONNECTED_SLEEPING -> stringResource(Res.string.connected_sleeping)
            ConnectionStatus.NOT_CONNECTED -> stringResource(Res.string.not_connected)
        }

    // This card also renders when the transport is already CONNECTED but node info hasn't arrived yet
    // (or the region needs setting), so only the not-yet-established states get "Stop Connecting".
    val disconnectLabel =
        when (connectionStatus) {
            ConnectionStatus.CONNECTED,
            ConnectionStatus.CONNECTED_SLEEPING,
            ConnectionStatus.MUST_SET_REGION,
            -> stringResource(Res.string.disconnect)

            ConnectionStatus.CONNECTING,
            ConnectionStatus.RECONNECTING,
            ConnectionStatus.RESTARTING,
            ConnectionStatus.NOT_CONNECTED,
            -> stringResource(Res.string.stop_connecting)
        }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularWavyProgressIndicator(modifier = Modifier.size(40.dp))

            Column {
                Text(text = deviceName, style = MaterialTheme.typography.headlineSmall)
                Text(text = deviceAddress, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DisconnectButton(onClick = onClickDisconnect, label = disconnectLabel)
    }
}
