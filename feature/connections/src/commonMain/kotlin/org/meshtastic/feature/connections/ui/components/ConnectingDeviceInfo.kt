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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connect
import org.meshtastic.core.resources.connection_retry_in
import org.meshtastic.core.resources.connection_retry_now
import org.meshtastic.core.resources.connection_stopped_retrying
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.stop_connecting
import org.meshtastic.core.ui.util.bleScanRequiresLocationServices
import org.meshtastic.sdk.ConnectionState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Displays the currently connecting (or connected) device with its name, address and connection status. The status line
 * names the step in progress or the countdown to the next attempt, the line under it says why the last attempt failed,
 * and a client that stopped trying offers Connect next to the disconnect button.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
fun ConnectingDeviceInfo(
    deviceName: String,
    deviceAddress: String,
    connectionState: ConnectionState,
    onClickDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    restartExpected: Boolean = false,
    isBluetooth: Boolean = false,
    onClickConnect: () -> Unit = {},
) {
    val waitingToRetry = (connectionState as? ConnectionState.Reconnecting)?.retryAt != null
    val now by
        produceState(Clock.System.now(), connectionState) {
            while (waitingToRetry) {
                value = Clock.System.now()
                delay(1.seconds)
            }
        }
    val detail =
        connectionState.toConnectionStatusDetail(now, restartExpected, isBluetooth, bleScanRequiresLocationServices)

    // Only a session that is up, or asleep by design, is disconnected; anything still trying is stopped.
    val disconnectLabel =
        when (connectionState) {
            ConnectionState.Connected,
            is ConnectionState.DeviceSleeping,
            -> stringResource(Res.string.disconnect)

            else -> stringResource(Res.string.stop_connecting)
        }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!detail.canConnect) CircularWavyProgressIndicator(modifier = Modifier.size(40.dp))

            Column {
                Text(text = deviceName, style = MaterialTheme.typography.headlineSmall)
                Text(text = deviceAddress, style = MaterialTheme.typography.bodyLarge)
                val retry = detail.retry
                val statusLabel = rememberSettledPhase(detail.status)?.let { stringResource(it) }.orEmpty()
                val primaryLabel =
                    when {
                        retry == null -> statusLabel

                        retry.seconds > 0 ->
                            pluralStringResource(
                                Res.plurals.connection_retry_in,
                                retry.seconds,
                                retry.seconds,
                                retry.attempt,
                            )

                        else -> stringResource(Res.string.connection_retry_now, retry.attempt)
                    }
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                detail.failure?.let { failure ->
                    Text(
                        text = stringResource(failure),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (detail.canConnect) {
                    Text(
                        text = stringResource(Res.string.connection_stopped_retrying),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (detail.canConnect) {
            Button(onClick = onClickConnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.connect))
            }
        }
        DisconnectButton(onClick = onClickDisconnect, label = disconnectLabel)
    }
}

/** How long a phase must last before the card names it, so a fast connect does not flicker through every step. */
private val PHASE_SETTLE_TIME = 400.milliseconds

/**
 * [phase] once it has held for [PHASE_SETTLE_TIME]; until then the phase shown before it. The first phase and the end
 * of the phases (null, a retry countdown or a connected link) show at once. Failures are a separate line and are never
 * held back.
 */
@Composable
private fun rememberSettledPhase(phase: StringResource?): StringResource? {
    var shown by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (phase != null && shown != null) delay(PHASE_SETTLE_TIME)
        shown = phase
    }
    return shown
}
